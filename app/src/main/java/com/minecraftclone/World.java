package com.minecraftclone;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the game world as a set of chunks.
 */
public class World {
    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();

    /**
     * Retrieves a chunk at the given chunk coordinates, creating it if necessary.
     */
    public Chunk getChunk(int cx, int cy, int cz) {
        return chunks.computeIfAbsent(new ChunkPos(cx, cy, cz), pos -> new Chunk());
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
