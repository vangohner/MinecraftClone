package com.minecraftclone;

/**
 * Entry point of the toy Minecraft clone.
 */
public class App {
    public static void main(String[] args) {
        long seed;
        if (args.length > 0) {
            try {
                seed = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                seed = args[0].hashCode();
            }
        } else {
            seed = 0L;
        }
        System.out.println("Using seed: " + seed);

        World world = new World();

        // Generate a grid of chunks using 3D noise terrain.
        ChunkGenerator generator = new ChunkGenerator(seed);
        for (int cx = 0; cx < 16; cx++) {
            for (int cy = 0; cy < 16; cy++) {
                for (int cz = 0; cz < 16; cz++) {
                    generator.generate(world, cx, cy, cz);
                }
            }
        }

        int centerChunk = 16 / 2;
        int spawnX = centerChunk * Chunk.SIZE + Chunk.SIZE / 2;
        int spawnZ = centerChunk * Chunk.SIZE + Chunk.SIZE / 2;
        int surface = generator.findSurfaceY(world, spawnX, spawnZ);
        int spawnY = surface >= 0 ? surface + 1 : Chunk.SIZE;
        Player player = new Player(spawnX, spawnY, spawnZ);
        System.out.println("Player starting at " + player);

        // Launch the LWJGL-based renderer.
        WorldRenderer renderer = new WorldRenderer(world, player);
        renderer.run();
    }
}
