package com.minecraftclone;

/**
 * Generates chunk terrain using 3D noise.
 */
public class ChunkGenerator {
    private final NoiseGenerator noise;
    private final double frequency;
    private final double amplitude;
    private final int baseHeight;

    public ChunkGenerator(long seed) {
        this(seed, 0.1, Chunk.SIZE / 4.0, Chunk.SIZE / 2);
    }

    public ChunkGenerator(long seed, double frequency, double amplitude, int baseHeight) {
        this.noise = new NoiseGenerator(seed);
        this.frequency = frequency;
        this.amplitude = amplitude;
        this.baseHeight = baseHeight;
    }

    /**
     * Generates or fills the chunk at the given coordinates.
     */
    public Chunk generate(World world, int cx, int cy, int cz) {
        Chunk chunk = world.getChunk(cx, cy, cz);
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.SIZE; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    int wx = cx * Chunk.SIZE + x;
                    int wy = cy * Chunk.SIZE + y;
                    int wz = cz * Chunk.SIZE + z;
                    double n = noise.noise(wx * frequency, wy * frequency, wz * frequency);
                    double density = n * amplitude;
                    if (density > wy - baseHeight) {
                        chunk.setBlock(x, y, z, BlockType.STONE);
                    }
                }
            }
        }

        // Convert topmost stone blocks into grass/dirt layers.
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = Chunk.SIZE - 1; y >= 0; y--) {
                    if (chunk.getBlock(x, y, z) == BlockType.STONE) {
                        chunk.setBlock(x, y, z, BlockType.GRASS);
                        for (int d = 1; d <= 3 && y - d >= 0; d++) {
                            if (chunk.getBlock(x, y - d, z) == BlockType.STONE) {
                                chunk.setBlock(x, y - d, z, BlockType.DIRT);
                            }
                        }
                        break;
                    }
                }
            }
        }

        return chunk;
    }

    /**
     * Finds the highest non-air block at the given world column within
     * the generated chunks. Returns -1 if none found.
     */
    public int findSurfaceY(World world, int wx, int wz) {
        int cx = Math.floorDiv(wx, Chunk.SIZE);
        int cz = Math.floorDiv(wz, Chunk.SIZE);
        int x = Math.floorMod(wx, Chunk.SIZE);
        int z = Math.floorMod(wz, Chunk.SIZE);
        Chunk chunk = world.getChunk(cx, 0, cz);
        for (int y = Chunk.SIZE - 1; y >= 0; y--) {
            if (chunk.getBlock(x, y, z) != BlockType.AIR) {
                return y;
            }
        }
        return -1;
    }
}

