package com.minecraftclone;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/**
 * Represents the game world as a set of chunks.
 */
public class World {
    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
    private final Set<ChunkPos> pending = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ThreadPoolExecutor workers;
    private final int maxQueueSize;
    private final ChunkGenerator generator;
    private final Path saveDir;
    private final boolean debug;

    private static final int REGION_SIZE = 32;
    private static final int CHUNK_BYTES = Chunk.SIZE * Chunk.SIZE * Chunk.SIZE;
    private static final int REGION_CHUNK_COUNT = REGION_SIZE * REGION_SIZE * REGION_SIZE;
    private static final int HEADER_BYTES = REGION_CHUNK_COUNT / 8;

    public World(ChunkGenerator generator) {
        this(generator, Path.of("world"), false);
    }

    public World(ChunkGenerator generator, boolean debug) {
        this(generator, Path.of("world"), debug);
    }

    public World(ChunkGenerator generator, Path saveDir) {
        this(generator, saveDir, false);
    }

    public World(ChunkGenerator generator, Path saveDir, boolean debug) {
        this.generator = generator;
        this.saveDir = saveDir;
        this.debug = debug;
        int threads = Runtime.getRuntime().availableProcessors();
        this.maxQueueSize = threads * 4;
        BlockingQueue<Runnable> queue = new PriorityBlockingQueue<>(maxQueueSize,
                Comparator.comparingInt(r -> ((ChunkRequest) r).distanceSq));
        this.workers = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                queue);
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create world directory", e);
        }
    }

    /**
     * Retrieves a chunk at the given chunk coordinates, creating and generating
     * it if necessary. This method executes generation on the calling thread and
     * is primarily intended for synchronous access such as spawn setup or block
     * modification.
     */
    public Chunk getChunk(int cx, int cy, int cz) {
        ChunkPos pos = new ChunkPos(cx, cy, cz);
        return chunks.computeIfAbsent(pos, p -> {
            Chunk chunk = loadChunk(p.x(), p.y(), p.z());
            if (chunk == null) {
                if (debug) {
                    System.out.println("Generating chunk " + p.x() + "," + p.y() + "," + p.z());
                }
                chunk = new Chunk();
                chunk.setOrigin(Chunk.Origin.GENERATED);
                if (generator != null) {
                    generator.generate(this, p.x(), p.y(), p.z(), chunk);
                }
                // persist newly generated chunk immediately
                writeChunk(chunk, p.x(), p.y(), p.z());
            } else {
                if (debug) {
                    System.out.println("Loaded chunk " + p.x() + "," + p.y() + "," + p.z());
                }
            }
            markNeighborsDirty(p.x(), p.y(), p.z());
            return chunk;
        });
    }

    public boolean isDebug() {
        return debug;
    }

    /**
     * Retrieves a chunk if it has already been generated, or {@code null}
     * otherwise.
     */
    public Chunk getChunkIfLoaded(int cx, int cy, int cz) {
        return chunks.get(new ChunkPos(cx, cy, cz));
    }

    /**
     * Queues asynchronous generation for the specified chunk if it has not been
     * loaded yet. Multiple requests for the same chunk are coalesced. The player
     * chunk coordinates are provided so requests can be prioritized by proximity.
     */
    public void requestChunk(int cx, int cy, int cz, int pcx, int pcy, int pcz) {
        ChunkPos pos = new ChunkPos(cx, cy, cz);
        if (chunks.containsKey(pos) || pending.contains(pos)) {
            return;
        }
        if (workers.getQueue().size() >= maxQueueSize) {
            return; // Too many pending tasks, drop this request.
        }
        int dx = cx - pcx;
        int dy = cy - pcy;
        int dz = cz - pcz;
        int distSq = dx * dx + dy * dy + dz * dz;
        pending.add(pos);
        try {
            workers.execute(new ChunkRequest(cx, cy, cz, pos, distSq));
        } catch (RejectedExecutionException e) {
            pending.remove(pos); // Executor shutting down or queue full; drop the task.
        }
    }

    /** Simple task wrapper carrying distance information for prioritization. */
    private class ChunkRequest implements Runnable {
        final int cx, cy, cz;
        final ChunkPos pos;
        final int distanceSq;

        ChunkRequest(int cx, int cy, int cz, ChunkPos pos, int distanceSq) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.pos = pos;
            this.distanceSq = distanceSq;
        }

        @Override
        public void run() {
            try {
                getChunk(cx, cy, cz);
            } finally {
                pending.remove(pos);
            }
        }
    }

    /**
     * Returns the set of positions for currently loaded chunks.
     */
    public Set<ChunkPos> getChunkPositions() {
        return Collections.unmodifiableSet(chunks.keySet());
    }

    /**
     * Gets the block type at world coordinates.
     */
    public BlockType getBlock(int x, int y, int z) {
        Chunk chunk = getChunkIfLoaded(worldToChunk(x), worldToChunk(y), worldToChunk(z));
        if (chunk == null) {
            return BlockType.AIR;
        }
        return chunk.getBlock(mod(x), mod(y), mod(z));
    }

    /**
     * Sets a block at world coordinates and writes the enclosing chunk back to disk.
     */
    public void setBlock(int x, int y, int z, BlockType type) {
        int cx = worldToChunk(x);
        int cy = worldToChunk(y);
        int cz = worldToChunk(z);
        Chunk chunk = getChunk(cx, cy, cz);
        chunk.setBlock(mod(x), mod(y), mod(z), type);
        // persist the chunk immediately so modifications survive crashes
        saveChunk(cx, cy, cz);
    }

    /**
     * Stops the worker threads. Should be invoked on application shutdown.
     */
    public void shutdown() {
        workers.shutdown();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        saveAll();
    }

    /** Saves all loaded chunks whose data changed since the last write, with progress output. */
    public void saveAll() {
        var positions = new ArrayList<ChunkPos>();
        for (var pos : chunks.keySet()) {
            Chunk chunk = chunks.get(pos);
            if (chunk != null && chunk.needsSave()) {
                positions.add(pos);
            }
        }
        int total = positions.size();
        if (total == 0) {
            return;
        }
        System.out.println("Saving " + total + " chunks...");
        long start = System.nanoTime();
        for (int i = 0; i < total; i++) {
            ChunkPos pos = positions.get(i);
            saveChunk(pos.x(), pos.y(), pos.z());
            long elapsed = System.nanoTime() - start;
            long avg = elapsed / (i + 1);
            long eta = avg * (total - i - 1);
            System.out.printf("Saved %d/%d chunks (ETA %.1fs)%n", i + 1, total, eta / 1_000_000_000.0);
        }
        System.out.println("Finished saving chunks.");
    }

    /** Saves a single chunk if it has unsaved changes. */
    public void saveChunk(int cx, int cy, int cz) {
        Chunk chunk = getChunkIfLoaded(cx, cy, cz);
        if (chunk == null || !chunk.needsSave()) {
            return;
        }
        writeChunk(chunk, cx, cy, cz);
    }

    /** Writes the provided chunk data to its region file. */
    private void writeChunk(Chunk chunk, int cx, int cy, int cz) {
        Path path = regionPath(cx, cy, cz);
        long index = chunkIndex(cx, cy, cz);
        long offset = chunkOffset(cx, cy, cz);
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            if (raf.length() < HEADER_BYTES) {
                raf.setLength(HEADER_BYTES);
            }
            raf.seek(offset);
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int y = 0; y < Chunk.SIZE; y++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        raf.writeByte(chunk.getBlock(x, y, z).ordinal());
                    }
                }
            }
            int byteIndex = (int) (index >>> 3);
            int bitMask = 1 << (index & 7);
            raf.seek(byteIndex);
            int flags = raf.read();
            if (flags < 0) {
                flags = 0;
            }
            raf.seek(byteIndex);
            raf.write(flags | bitMask);
            chunk.markSaved();
            chunk.clearEmptyLodSteps();
        } catch (IOException e) {
            System.err.println("Failed to save chunk " + cx + "," + cy + "," + cz + ": " + e.getMessage());
        }
    }

    private Chunk loadChunk(int cx, int cy, int cz) {
        Path path = regionPath(cx, cy, cz);
        if (!Files.exists(path)) {
            return null;
        }
        long index = chunkIndex(cx, cy, cz);
        long offset = chunkOffset(cx, cy, cz);
        Chunk chunk = new Chunk();
        chunk.setOrigin(Chunk.Origin.LOADED);
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            if (raf.length() < HEADER_BYTES) {
                return null;
            }
            int byteIndex = (int) (index >>> 3);
            int bitMask = 1 << (index & 7);
            raf.seek(byteIndex);
            int flags = raf.read();
            if (flags < 0 || (flags & bitMask) == 0) {
                return null;
            }
            if (offset + CHUNK_BYTES > raf.length()) {
                return null;
            }
            raf.seek(offset);
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int y = 0; y < Chunk.SIZE; y++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        int ord = raf.readUnsignedByte();
                        chunk.setBlockUnchecked(x, y, z, BlockType.values()[ord]);
                    }
                }
            }
            chunk.markSaved();
            chunk.clearEmptyLodSteps();
        } catch (IOException e) {
            System.err.println("Failed to load chunk " + cx + "," + cy + "," + cz + ": " + e.getMessage());
            return null;
        }
        return chunk;
    }

    private Path regionPath(int cx, int cy, int cz) {
        int rx = regionCoord(cx);
        int ry = regionCoord(cy);
        int rz = regionCoord(cz);
        return saveDir.resolve("r_" + rx + "_" + ry + "_" + rz + ".rg");
    }

    private Path regionPathFromCoords(int rx, int ry, int rz) {
        return saveDir.resolve("r_" + rx + "_" + ry + "_" + rz + ".rg");
    }

    private long chunkIndex(int cx, int cy, int cz) {
        int lx = regionMod(cx);
        int ly = regionMod(cy);
        int lz = regionMod(cz);
        return ((long) lx * REGION_SIZE + ly) * REGION_SIZE + lz;
    }

    private long chunkOffset(int cx, int cy, int cz) {
        return HEADER_BYTES + chunkIndex(cx, cy, cz) * CHUNK_BYTES;
    }

    /** Deletes the region file at the given region coordinates and unloads its chunks. */
    public void deleteRegion(int rx, int ry, int rz) {
        chunks.keySet().removeIf(pos ->
                regionCoord(pos.x()) == rx &&
                regionCoord(pos.y()) == ry &&
                regionCoord(pos.z()) == rz);
        try {
            Files.deleteIfExists(regionPathFromCoords(rx, ry, rz));
        } catch (IOException e) {
            System.err.println("Failed to delete region " + rx + "," + ry + "," + rz + ": " + e.getMessage());
        }
    }

    /** Deletes all region files and unloads every chunk. */
    public void clearWorld() {
        chunks.clear();
        pending.clear();
        try (var stream = Files.list(saveDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".rg")).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    System.err.println("Failed to delete region file " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("Failed to clear world: " + e.getMessage());
        }
    }

    private int regionCoord(int c) {
        return Math.floorDiv(c, REGION_SIZE);
    }

    private int regionMod(int c) {
        return Math.floorMod(c, REGION_SIZE);
    }

    private int worldToChunk(int c) {
        return Math.floorDiv(c, Chunk.SIZE);
    }

    private int mod(int c) {
        return Math.floorMod(c, Chunk.SIZE);
    }

    private void markNeighborsDirty(int cx, int cy, int cz) {
        int[][] dirs = { {1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1} };
        for (int[] d : dirs) {
            Chunk neighbor = getChunkIfLoaded(cx + d[0], cy + d[1], cz + d[2]);
            if (neighbor != null) {
                neighbor.markDirty();
            }
        }
    }
}
