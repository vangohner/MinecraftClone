package com.minecraftclone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates chunk terrain using layered 3D noise. The generator creates a
 * 2D height map for broad terrain features (mountains, valleys, etc.) and then
 * applies an additional 3D noise field to add cliffs, overhangs, floating
 * islands and other irregularities. A separate noise field carves out caves.
 */
public class ChunkGenerator {
    private final NoiseGenerator heightNoise;
    private final NoiseGenerator detailNoise;
    private final NoiseGenerator caveNoise;

    private final double baseFrequency;
    private final double baseAmplitude;
    private final int baseHeight;

    // Tuning parameters for secondary noise fields.
    private final double detailFrequency;
    private final double detailAmplitude;
    private final double caveFrequency = 0.08;
    private final double caveThreshold = 0.65;

    public ChunkGenerator(long seed) {
        // Default values tuned for varied terrain across large vertical ranges.
        this(seed, 0.003, Chunk.SIZE * 4.0, 0);
    }

    public ChunkGenerator(long seed, double frequency, double amplitude, int baseHeight) {
        this.heightNoise = new NoiseGenerator(seed);
        this.detailNoise = new NoiseGenerator(seed + 1);
        this.caveNoise = new NoiseGenerator(seed + 2);

        this.baseFrequency = frequency;
        this.baseAmplitude = amplitude;
        this.baseHeight = baseHeight;

        // Secondary noise scales relative to the primary height noise.
        this.detailFrequency = frequency * 4.0;
        this.detailAmplitude = amplitude / 2.0;
    }

    /**
     * Generates or fills the provided chunk at the given coordinates.
     * The chunk is expected to be newly created and not yet present in the
     * world's chunk map so generation can occur off the main thread without
     * exposing a partially built chunk.
     */
    public Chunk generate(World world, int cx, int cy, int cz, Chunk chunk) {
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.SIZE; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    int wx = cx * Chunk.SIZE + x;
                    int wy = cy * Chunk.SIZE + y;
                    int wz = cz * Chunk.SIZE + z;

                    // Base terrain height from 2D noise.
                    double surface = heightNoise.noise(wx * baseFrequency, wz * baseFrequency) * baseAmplitude + baseHeight;

                    // Additional 3D displacement for cliffs, overhangs and floating islands.
                    double displacement = detailNoise.noise(wx * detailFrequency, wy * detailFrequency, wz * detailFrequency) * detailAmplitude;

                    double density = displacement + (surface - wy);
                    if (density > 0) {
                        chunk.setBlock(x, y, z, BlockType.STONE);

                        // Carve out caves using a separate noise field.
                        double cave = caveNoise.noise(wx * caveFrequency, wy * caveFrequency, wz * caveFrequency);
                        if (cave > caveThreshold) {
                            chunk.setBlock(x, y, z, BlockType.AIR);
                        }
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

        List<Integer> ys = new ArrayList<>();
        for (ChunkPos pos : world.getChunkPositions()) {
            if (pos.x() == cx && pos.z() == cz) {
                ys.add(pos.y());
            }
        }
        if (ys.isEmpty()) {
            return -1;
        }
        Collections.sort(ys);
        for (int i = ys.size() - 1; i >= 0; i--) {
            int cy = ys.get(i);
            Chunk chunk = world.getChunk(cx, cy, cz);
            for (int y = Chunk.SIZE - 1; y >= 0; y--) {
                if (chunk.getBlock(x, y, z) != BlockType.AIR) {
                    return cy * Chunk.SIZE + y;
                }
            }
        }
        return -1;
    }
}

