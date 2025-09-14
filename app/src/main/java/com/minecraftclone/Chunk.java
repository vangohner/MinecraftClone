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
    private final Set<Integer> dirtyLodSteps = new HashSet<>();
    private boolean dirty = true;
    // whether the chunk's block data differs from its last on-disk save
    private boolean needsSave = true;
    public enum Origin { GENERATED, LOADED }
    private Origin origin = Origin.GENERATED;
    /** True if every block on each of the six faces is solid. Indexed as +X,-X,+Y,-Y,+Z,-Z. */
    private final boolean[] solidFaces = new boolean[6];
    /** Whether this chunk is completely hidden by neighbors. */
    private boolean occluded;

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
        ChunkMesh old = lodMeshes.put(step, mesh);
        if (old != null) {
            old.dispose();
        }
        dirtyLodSteps.remove(step);
    }

    public boolean isLodStepEmpty(int step) {
        return emptyLodSteps.contains(step);
    }

    public void markLodStepEmpty(int step) {
        emptyLodSteps.add(step);
        dirtyLodSteps.remove(step);
    }

    public void clearEmptyLodSteps() {
        emptyLodSteps.clear();
        dirtyLodSteps.clear();
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
        dirtyLodSteps.addAll(lodMeshes.keySet());
        emptyLodSteps.clear();
    }

    private void clearLods() {
        for (ChunkMesh m : lodMeshes.values()) {
            m.dispose();
        }
        lodMeshes.clear();
        emptyLodSteps.clear();
        dirtyLodSteps.clear();
    }

    public boolean isLodStepDirty(int step) {
        return dirtyLodSteps.contains(step);
    }

    /** Recomputes whether each face of the chunk is fully solid. */
    public void updateFaceSolidity() {
        // +X face
        solidFaces[0] = true;
        outer0: for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                if (blocks[SIZE - 1][y][z] == BlockType.AIR) {
                    solidFaces[0] = false;
                    break outer0;
                }
            }
        }
        // -X face
        solidFaces[1] = true;
        outer1: for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                if (blocks[0][y][z] == BlockType.AIR) {
                    solidFaces[1] = false;
                    break outer1;
                }
            }
        }
        // +Y face
        solidFaces[2] = true;
        outer2: for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (blocks[x][SIZE - 1][z] == BlockType.AIR) {
                    solidFaces[2] = false;
                    break outer2;
                }
            }
        }
        // -Y face
        solidFaces[3] = true;
        outer3: for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (blocks[x][0][z] == BlockType.AIR) {
                    solidFaces[3] = false;
                    break outer3;
                }
            }
        }
        // +Z face
        solidFaces[4] = true;
        outer4: for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (blocks[x][y][SIZE - 1] == BlockType.AIR) {
                    solidFaces[4] = false;
                    break outer4;
                }
            }
        }
        // -Z face
        solidFaces[5] = true;
        outer5: for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (blocks[x][y][0] == BlockType.AIR) {
                    solidFaces[5] = false;
                    break outer5;
                }
            }
        }
    }

    public boolean isFaceSolid(int face) {
        return solidFaces[face];
    }

    public boolean isOccluded() {
        return occluded;
    }

    public void setOccluded(boolean occluded) {
        this.occluded = occluded;
    }

    private void check(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            throw new IndexOutOfBoundsException("Block coordinates out of range");
        }
    }
}
