package com.minecraftclone;

/**
 * Entry point of the toy Minecraft clone.
 */
public class App {
    public static void main(String[] args) {
        World world = new World();

        // Initialize a single cubic chunk with basic terrain layers.
        Chunk chunk = world.getChunk(0, 0, 0);
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = 0; y < Chunk.SIZE; y++) {
                    if (y == Chunk.SIZE - 1) {
                        chunk.setBlock(x, y, z, BlockType.GRASS);
                    } else if (y >= Chunk.SIZE - 4) {
                        chunk.setBlock(x, y, z, BlockType.DIRT);
                    } else {
                        chunk.setBlock(x, y, z, BlockType.STONE);
                    }
                }
            }
        }

        Player player = new Player(8, Chunk.SIZE + 1, 8);
        System.out.println("Player starting at " + player);

        // Launch the LWJGL-based renderer.
        WorldRenderer renderer = new WorldRenderer(world, player);
        renderer.run();
    }
}
