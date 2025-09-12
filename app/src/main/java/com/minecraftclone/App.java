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

        // Generate a single chunk using noise-based terrain heights.
        ChunkGenerator generator = new ChunkGenerator(seed);
        generator.generate(world, 0, 0);

        int spawnX = Chunk.SIZE / 2;
        int spawnZ = Chunk.SIZE / 2;
        int spawnY = generator.sampleHeight(spawnX, spawnZ) + 1;
        Player player = new Player(spawnX, spawnY, spawnZ);
        System.out.println("Player starting at " + player);

        // Launch the LWJGL-based renderer.
        WorldRenderer renderer = new WorldRenderer(world, player);
        renderer.run();
    }
}
