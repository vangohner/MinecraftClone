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

        ChunkGenerator generator = new ChunkGenerator(seed);
        World world = new World(generator);

        // Generate a tall column of chunks at the spawn location so we can
        // find a reasonable starting Y coordinate even in mountainous terrain.
        int spawnChunkX = 0;
        int spawnChunkZ = 0;
        for (int cy = -8; cy <= 8; cy++) {
            world.getChunk(spawnChunkX, cy, spawnChunkZ);
        }

        int spawnX = spawnChunkX * Chunk.SIZE + Chunk.SIZE / 2;
        int spawnZ = spawnChunkZ * Chunk.SIZE + Chunk.SIZE / 2;
        int surfaceY = generator.findSurfaceY(world, spawnX, spawnZ);
        Player player = new Player(spawnX, surfaceY + 1, spawnZ);
        System.out.println("Player starting at " + player);

        // Launch the LWJGL-based renderer.
        WorldRenderer renderer = new WorldRenderer(world, player);
        renderer.run();
        world.shutdown();
    }
}
