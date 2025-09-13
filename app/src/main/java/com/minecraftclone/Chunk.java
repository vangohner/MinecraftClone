package com.minecraftclone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a 16x16x16 block section of the world.
 */
public class Chunk {
    public static final int SIZE = 16;
    private final BlockType[][][] blocks = new BlockType[SIZE][SIZE][SIZE];
    private ChunkMesh mesh;
    private final Map<Integer, ChunkMesh> lodMeshes = new HashMap<>();
    private final Set<Integer> emptyLodSteps = new HashSet<>();
    private boolean dirty = true;
    // whether the chunk's block data differs from its last on-disk save
    private boolean needsSave = true;
    public enum Origin { GENERATED, LOADED }
    private Origin origin = Origin.GENERATED;

    public Chunk() {
        // initialize all blocks to AIR
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    blocks[x][y][z] = BlockType.AIR;
                }
            }
        }
    }

    public BlockType getBlock(int x, int y, int z) {
        check(x, y, z);
        return blocks[x][y][z];
    }

    public void setBlock(int x, int y, int z, BlockType type) {
        check(x, y, z);
        blocks[x][y][z] = type;
        dirty = true;
        needsSave = true;
        clearLods();
    }

    /**
     * Sets a block without marking the chunk dirty or clearing LOD meshes.
     * Intended for bulk loading from disk where the chunk will be marked dirty
     * once after all blocks are populated.
     */
    void setBlockUnchecked(int x, int y, int z, BlockType type) {
        blocks[x][y][z] = type;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Returns {@code true} if the chunk's blocks have changed since the last disk save. */
    public boolean needsSave() {
        return needsSave;
    }

    void markSaved() {
        needsSave = false;
    }

    public ChunkMesh getMesh() {
        return mesh;
    }

    public void setMesh(ChunkMesh mesh) {
        this.mesh = mesh;
        this.dirty = false;
    }

    public ChunkMesh getLodMesh(int step) {
        return lodMeshes.get(step);
    }

    public void setLodMesh(int step, ChunkMesh mesh) {
        lodMeshes.put(step, mesh);
    }

    public boolean isLodStepEmpty(int step) {
        return emptyLodSteps.contains(step);
    }

    public void markLodStepEmpty(int step) {
        emptyLodSteps.add(step);
    }

    public void clearEmptyLodSteps() {
        emptyLodSteps.clear();
    }

    public Origin getOrigin() {
        return origin;
    }

    void setOrigin(Origin origin) {
        this.origin = origin;
    }

    /** Marks the chunk as needing its mesh rebuilt. */
    public void markDirty() {
        this.dirty = true;
        clearLods();
    }

    private void clearLods() {
        for (ChunkMesh m : lodMeshes.values()) {
            m.dispose();
        }
        lodMeshes.clear();
        emptyLodSteps.clear();
    }

    private void check(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            throw new IndexOutOfBoundsException("Block coordinates out of range");
        }
    }
}
