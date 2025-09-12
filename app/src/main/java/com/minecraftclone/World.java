package com.minecraftclone;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;

/**
 * Represents the game world as a set of chunks.
 */
public class World {
    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private final ChunkGenerator generator;

    public World(ChunkGenerator generator) {
        this.generator = generator;
    }

    /**
     * Retrieves a chunk at the given chunk coordinates, creating and generating it
     * if necessary.
     */
    public Chunk getChunk(int cx, int cy, int cz) {
        ChunkPos pos = new ChunkPos(cx, cy, cz);
        Chunk chunk = chunks.get(pos);
        if (chunk == null) {
            chunk = new Chunk();
            chunks.put(pos, chunk);
            if (generator != null) {
                generator.generate(this, cx, cy, cz);
            }
        }
        return chunk;
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
        Chunk chunk = getChunk(worldToChunk(x), worldToChunk(y), worldToChunk(z));
        return chunk.getBlock(mod(x), mod(y), mod(z));
    }

    /**
     * Sets a block at world coordinates.
     */
    public void setBlock(int x, int y, int z, BlockType type) {
        Chunk chunk = getChunk(worldToChunk(x), worldToChunk(y), worldToChunk(z));
        chunk.setBlock(mod(x), mod(y), mod(z), type);
    }

    private int worldToChunk(int c) {
        return Math.floorDiv(c, Chunk.SIZE);
    }

    private int mod(int c) {
        return Math.floorMod(c, Chunk.SIZE);
    }
}
