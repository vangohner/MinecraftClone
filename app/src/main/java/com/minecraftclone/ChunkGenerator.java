package com.minecraftclone;

/**
 * Generates chunk terrain using a noise-based height map.
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
    public Chunk generate(World world, int cx, int cz) {
        Chunk chunk = world.getChunk(cx, 0, cz);
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = cx * Chunk.SIZE + x;
                int wz = cz * Chunk.SIZE + z;
                int height = sampleHeight(wx, wz);
                for (int y = 0; y <= height; y++) {
                    if (y == height) {
                        chunk.setBlock(x, y, z, BlockType.GRASS);
                    } else if (y >= height - 3) {
                        chunk.setBlock(x, y, z, BlockType.DIRT);
                    } else {
                        chunk.setBlock(x, y, z, BlockType.STONE);
                    }
                }
            }
        }
        return chunk;
    }

    /**
     * Samples the terrain height at the given world coordinates.
     */
    public int sampleHeight(int wx, int wz) {
        double n = noise.noise(wx * frequency, wz * frequency);
        int height = (int) Math.round(baseHeight + n * amplitude);
        if (height < 0) {
            height = 0;
        } else if (height >= Chunk.SIZE) {
            height = Chunk.SIZE - 1;
        }
        return height;
    }
}
