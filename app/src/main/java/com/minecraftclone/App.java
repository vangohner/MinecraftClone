package com.minecraftclone;

/**
 * Entry point of the toy Minecraft clone.
 */
public class App {
    public static void main(String[] args) {
        World world = new World();

        // Initialize a single chunk with a flat grass layer.
        Chunk chunk = world.getChunk(0, 0, 0);
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                chunk.setBlock(x, 0, z, BlockType.GRASS);
                for (int y = 1; y < Chunk.SIZE; y++) {
                    chunk.setBlock(x, y, z, BlockType.AIR);
                }
            }
        }

        Player player = new Player(8, 1, 8);
        System.out.println("Player starting at " + player);

        // Launch the LWJGL-based renderer.
        WorldRenderer renderer = new WorldRenderer(world, player);
        renderer.run();
    }
}
