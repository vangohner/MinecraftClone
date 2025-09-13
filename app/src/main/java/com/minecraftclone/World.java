package com.minecraftclone;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Represents the game world as a set of chunks.
 */
public class World {
    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
    private final Set<ChunkPos> pending = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService workers = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final ChunkGenerator generator;
    private final Path saveDir;
    private final boolean debug;

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
                if (generator != null) {
                    generator.generate(this, p.x(), p.y(), p.z(), chunk);
                }
            } else if (debug) {
                System.out.println("Loaded chunk " + p.x() + "," + p.y() + "," + p.z());
            }
            markNeighborsDirty(p.x(), p.y(), p.z());
            return chunk;
        });
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
     * loaded yet. Multiple requests for the same chunk are coalesced.
     */
    public void requestChunk(int cx, int cy, int cz) {
        ChunkPos pos = new ChunkPos(cx, cy, cz);
        if (chunks.containsKey(pos) || !pending.add(pos)) {
            return;
        }
        workers.submit(() -> {
            try {
                getChunk(cx, cy, cz);
            } finally {
                pending.remove(pos);
            }
        });
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
     * Sets a block at world coordinates.
     */
    public void setBlock(int x, int y, int z, BlockType type) {
        Chunk chunk = getChunk(worldToChunk(x), worldToChunk(y), worldToChunk(z));
        chunk.setBlock(mod(x), mod(y), mod(z), type);
    }

    /**
     * Stops the worker threads. Should be invoked on application shutdown.
     */
    public void shutdown() {
        saveAll();
        workers.shutdown();
    }

    /** Saves all currently loaded chunks to disk. */
    public void saveAll() {
        for (ChunkPos pos : chunks.keySet()) {
            saveChunk(pos.x(), pos.y(), pos.z());
        }
    }

    /** Saves a single chunk if it is loaded. */
    public void saveChunk(int cx, int cy, int cz) {
        Chunk chunk = getChunkIfLoaded(cx, cy, cz);
        if (chunk == null) {
            return;
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(chunkPath(cx, cy, cz)))) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int y = 0; y < Chunk.SIZE; y++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        out.writeByte(chunk.getBlock(x, y, z).ordinal());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save chunk " + cx + "," + cy + "," + cz + ": " + e.getMessage());
        }
    }

    private Chunk loadChunk(int cx, int cy, int cz) {
        Path path = chunkPath(cx, cy, cz);
        if (!Files.exists(path)) {
            return null;
        }
        Chunk chunk = new Chunk();
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int y = 0; y < Chunk.SIZE; y++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        int ord = in.readUnsignedByte();
                        chunk.setBlockUnchecked(x, y, z, BlockType.values()[ord]);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load chunk " + cx + "," + cy + "," + cz + ": " + e.getMessage());
            return null;
        }
        return chunk;
    }

    private Path chunkPath(int cx, int cy, int cz) {
        return saveDir.resolve(cx + "_" + cy + "_" + cz + ".chk");
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
