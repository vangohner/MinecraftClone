package com.minecraftclone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates chunk terrain using multiple layers of 2D and 3D noise. Large scale
 * continent and mountain maps define broad features while finer 3D fields add
 * cliffs, floating islands and overhangs. Additional modifiers create
 * monolithic pillars and carve out massive cave systems. The generator is
 * intentionally "amplified" to take advantage of the engine's unbounded world
 * height.
 */
public class ChunkGenerator {
    private final NoiseGenerator heightNoise;
    private final NoiseGenerator detailNoise;
    private final NoiseGenerator caveNoise;
    private final NoiseGenerator continentNoise;
    private final NoiseGenerator mountainNoise;
    private final NoiseGenerator islandNoise;
    private final NoiseGenerator monolithNoise;
    private final NoiseGenerator regionNoise;

    private final double baseFrequency;
    private final double baseAmplitude;
    private final int baseHeight;

    // Tuning parameters for secondary noise fields.
    private final double detailFrequency;
    private final double detailAmplitude;

    private final double caveFrequency;
    private final double caveThreshold;

    private final double continentFrequency;
    private final double continentAmplitude;
    private final double mountainFrequency;
    private final double mountainAmplitude;
    private final double islandFrequency;
    private final double islandAmplitude;
    private final double islandBaseHeight = 40.0;
    private final double islandThreshold = 0.6;
    private final double monolithFrequency;
    private final double monolithAmplitude;
    private final double monolithThreshold = 0.8;
    private final double regionFrequency;

    private final int waterLevel = 0;
    private final int snowLine = 80;

    public ChunkGenerator(long seed) {
        // Default values tuned for amplified terrain across vast vertical ranges.
        this(seed, 0.003, Chunk.SIZE * 16.0, 0);
    }

    public ChunkGenerator(long seed, double frequency, double amplitude, int baseHeight) {
        this.heightNoise = new NoiseGenerator(seed);
        this.detailNoise = new NoiseGenerator(seed + 1);
        this.caveNoise = new NoiseGenerator(seed + 2);
        this.continentNoise = new NoiseGenerator(seed + 3);
        this.mountainNoise = new NoiseGenerator(seed + 4);
        this.islandNoise = new NoiseGenerator(seed + 5);
        this.monolithNoise = new NoiseGenerator(seed + 6);
        this.regionNoise = new NoiseGenerator(seed + 7);

        this.baseFrequency = frequency;
        this.baseAmplitude = amplitude;
        this.baseHeight = baseHeight;

        // Secondary noise scales relative to the primary height noise.
        this.detailFrequency = frequency * 8.0;
        this.detailAmplitude = amplitude / 4.0;

        this.caveFrequency = 0.02;
        this.caveThreshold = 0.55;

        this.continentFrequency = frequency / 8.0;
        this.continentAmplitude = amplitude * 1.5;
        this.mountainFrequency = frequency / 2.0;
        this.mountainAmplitude = amplitude;
        this.islandFrequency = frequency * 6.0;
        this.islandAmplitude = amplitude / 2.0;
        this.monolithFrequency = frequency * 4.0;
        this.monolithAmplitude = amplitude * 0.75;
        this.regionFrequency = frequency / 16.0;
    }

    /**
     * Generates or fills the provided chunk at the given coordinates.
     * The chunk is expected to be newly created and not yet present in the
     * world's chunk map so generation can occur off the main thread without
     * exposing a partially built chunk.
     */
    public Chunk generate(World world, int cx, int cy, int cz, Chunk chunk) {
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = cx * Chunk.SIZE + x;
                int wz = cz * Chunk.SIZE + z;

                double regionScale = (regionNoise.noise(wx * regionFrequency, wz * regionFrequency) + 1.0) / 2.0;

                double contAmp = continentAmplitude * (0.3 + 0.7 * regionScale);
                double mountAmp = mountainAmplitude * regionScale;
                double detailAmp = detailAmplitude * (0.5 + 0.5 * regionScale);

                double continent = continentNoise.noise(wx * continentFrequency, wz * continentFrequency) * contAmp;
                double mountains = mountainNoise.noise(wx * mountainFrequency, wz * mountainFrequency) * mountAmp;
                double surface = baseHeight + continent + mountains;

                int depth = -1; // Tracks distance below the surface for dirt placement.
                for (int y = Chunk.SIZE - 1; y >= 0; y--) {
                    int wy = cy * Chunk.SIZE + y;

                    // 3D displacement for cliffs, overhangs and floating islands.
                    double displacement = detailNoise.noise(wx * detailFrequency, wy * detailFrequency, wz * detailFrequency)
                            * detailAmp;
                    double density = displacement + (surface - wy);

                    // Vertical monolith pillars jutting from the ground.
                    double monolith = monolithNoise.noise(wx * monolithFrequency, wz * monolithFrequency);
                    if (regionScale > 0.6 && monolith > monolithThreshold) {
                        density += (monolith - monolithThreshold) * monolithAmplitude * regionScale;
                    }

                    // Floating islands high above the surface.
                    if (regionScale > 0.6 && wy > surface + islandBaseHeight) {
                        double island = islandNoise.noise(wx * islandFrequency, wy * islandFrequency, wz * islandFrequency)
                                * islandAmplitude;
                        double islandDensity = island - (wy - (surface + islandBaseHeight));
                        if (islandDensity > density) {
                            density = islandDensity;
                        }
                    }

                    if (density > 0) {
                        // Evaluate density one block above to determine if this is an exposed surface.
                          double displacementAbove = detailNoise.noise(wx * detailFrequency, (wy + 1) * detailFrequency,
                                  wz * detailFrequency) * detailAmp;
                        double densityAbove = displacementAbove + (surface - (wy + 1));

                        BlockType type;
                        if (densityAbove <= 0) {
                            depth = 0;
                            if (wy > snowLine) {
                                type = BlockType.SNOW;
                            } else if (wy <= waterLevel + 1) {
                                type = BlockType.SAND;
                            } else {
                                type = BlockType.GRASS;
                            }
                        } else if (depth >= 0 && depth < 3) {
                            depth++;
                            if (wy > snowLine) {
                                type = BlockType.ICE;
                            } else {
                                type = BlockType.DIRT;
                            }
                        } else {
                            depth = depth < 0 ? 0 : depth + 1;
                            type = BlockType.STONE;
                        }

                        // Carve out caves using a separate noise field.
                        double cave = caveNoise.noise(wx * caveFrequency, wy * caveFrequency, wz * caveFrequency);
                        if (cave > caveThreshold) {
                            if (wy <= waterLevel) {
                                chunk.setBlock(x, y, z, BlockType.WATER);
                            } else {
                                chunk.setBlock(x, y, z, BlockType.AIR);
                            }
                            // Reset depth so surfaces beneath a cave can receive grass again.
                            depth = -1;
                        } else {
                            chunk.setBlock(x, y, z, type);
                        }
                    } else {
                        if (wy <= waterLevel) {
                            chunk.setBlock(x, y, z, BlockType.WATER);
                        } else {
                            chunk.setBlock(x, y, z, BlockType.AIR);
                        }
                        depth = -1;
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

    /**
     * Samples the raw density field used for terrain generation. Positive
     * values indicate solid terrain while negative values represent empty
     * space. This mirrors the density computation performed during chunk
     * generation but omits any block-type assignment so it can be used by
     * marching cubes meshing.
     */
    public double sampleDensity(int wx, int wy, int wz) {
        double regionScale = (regionNoise.noise(wx * regionFrequency, wz * regionFrequency) + 1.0) / 2.0;

        double contAmp = continentAmplitude * (0.3 + 0.7 * regionScale);
        double mountAmp = mountainAmplitude * regionScale;
        double detailAmp = detailAmplitude * (0.5 + 0.5 * regionScale);

        double continent = continentNoise.noise(wx * continentFrequency, wz * continentFrequency) * contAmp;
        double mountains = mountainNoise.noise(wx * mountainFrequency, wz * mountainFrequency) * mountAmp;
        double surface = baseHeight + continent + mountains;

        double displacement = detailNoise.noise(wx * detailFrequency, wy * detailFrequency, wz * detailFrequency)
                * detailAmp;
        double density = displacement + (surface - wy);

        double monolith = monolithNoise.noise(wx * monolithFrequency, wz * monolithFrequency);
        if (regionScale > 0.6 && monolith > monolithThreshold) {
            density += (monolith - monolithThreshold) * monolithAmplitude * regionScale;
        }

        if (regionScale > 0.6 && wy > surface + islandBaseHeight) {
            double island = islandNoise.noise(wx * islandFrequency, wy * islandFrequency, wz * islandFrequency)
                    * islandAmplitude;
            double islandDensity = island - (wy - (surface + islandBaseHeight));
            if (islandDensity > density) {
                density = islandDensity;
            }
        }

        return density;
    }

    /** Returns the configured water level used for coloring. */
    public int getWaterLevel() {
        return waterLevel;
    }

    /** Returns the snow line altitude used for coloring. */
    public int getSnowLine() {
        return snowLine;
    }
}

