package com.minecraftclone;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the toy Minecraft clone.
 */
public class App {
    public static void main(String[] args) {
        boolean debugChunks = false;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if ("--debug-chunks".equalsIgnoreCase(arg)) {
                debugChunks = true;
            } else {
                positional.add(arg);
            }
        }

        long seed;
        if (positional.size() > 0) {
            try {
                seed = Long.parseLong(positional.get(0));
            } catch (NumberFormatException e) {
                seed = positional.get(0).hashCode();
            }
        } else {
            seed = 0L;
        }
        System.out.println("Using seed: " + seed);

        int renderDistance = 4;
        if (positional.size() > 1) {
            try {
                renderDistance = Integer.parseInt(positional.get(1));
            } catch (NumberFormatException e) {
                System.err.println("Invalid render distance '" + positional.get(1) + "', using default " + renderDistance);
            }
        }

        int lod1Start = 8;
        if (positional.size() > 2) {
            try {
                lod1Start = Integer.parseInt(positional.get(2));
            } catch (NumberFormatException e) {
                System.err.println("Invalid LOD1 start '" + positional.get(2) + "', using default " + lod1Start);
            }
        }

        int lod2Start = 16;
        if (positional.size() > 3) {
            try {
                lod2Start = Integer.parseInt(positional.get(3));
            } catch (NumberFormatException e) {
                System.err.println("Invalid LOD2 start '" + positional.get(3) + "', using default " + lod2Start);
            }
        }

        ChunkGenerator generator = new ChunkGenerator(seed);
        World world = new World(generator, debugChunks);

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
        WorldRenderer renderer = new WorldRenderer(world, player, renderDistance, lod1Start, lod2Start);
        renderer.run();
    }
}
