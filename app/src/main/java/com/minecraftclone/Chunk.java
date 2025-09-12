package com.minecraftclone;

/**
 * Represents a 16x16x16 block section of the world.
 */
public class Chunk {
    public static final int SIZE = 16;
    private final BlockType[][][] blocks = new BlockType[SIZE][SIZE][SIZE];

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
    }

    private void check(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) {
            throw new IndexOutOfBoundsException("Block coordinates out of range");
        }
    }
}
