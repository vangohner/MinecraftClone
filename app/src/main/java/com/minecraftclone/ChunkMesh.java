package com.minecraftclone;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;

/**
 * Represents a cached mesh for a chunk using a single VBO.
 */
public class ChunkMesh {
    private final int vbo;
    private final int vertexCount;

    private ChunkMesh(int vbo, int vertexCount) {
        this.vbo = vbo;
        this.vertexCount = vertexCount;
    }

    /**
     * Builds a mesh for the given chunk at the specified world origin.
     */
    public static ChunkMesh build(World world, Chunk chunk, int baseX, int baseY, int baseZ) {
        FloatBuffer buffer = buildBuffer(world, chunk, baseX, baseY, baseZ);
        int vertexCount = buffer.limit() / 6;
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        return new ChunkMesh(vbo, vertexCount);
    }

    private static FloatBuffer buildBuffer(World world, Chunk chunk, int baseX, int baseY, int baseZ) {
        List<Float> data = new ArrayList<>();

        // Greedy mesh each pair of parallel faces
        meshXY(data, world, chunk, baseX, baseY, baseZ, true);   // +Z
        meshXY(data, world, chunk, baseX, baseY, baseZ, false);  // -Z
        meshYZ(data, world, chunk, baseX, baseY, baseZ, true);   // +X
        meshYZ(data, world, chunk, baseX, baseY, baseZ, false);  // -X
        meshXZ(data, world, chunk, baseX, baseY, baseZ, true);   // +Y
        meshXZ(data, world, chunk, baseX, baseY, baseZ, false);  // -Y

        FloatBuffer buf = BufferUtils.createFloatBuffer(data.size());
        for (Float f : data) {
            buf.put(f);
        }
        buf.flip();
        return buf;
    }

    private static void meshXY(List<Float> data, World world, Chunk chunk,
            int baseX, int baseY, int baseZ, boolean positive) {
        float shadeFactor = positive ? 0.9f : 0.8f;
        for (int z = 0; z < Chunk.SIZE; z++) {
            boolean[][] visited = new boolean[Chunk.SIZE][Chunk.SIZE];
            for (int y = 0; y < Chunk.SIZE; y++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    if (visited[x][y]) {
                        continue;
                    }
                    BlockType type = chunk.getBlock(x, y, z);
                    int nx = baseX + x;
                    int ny = baseY + y;
                    int nz = baseZ + z + (positive ? 1 : -1);
                    if (type == BlockType.AIR || !isAir(world, nx, ny, nz)) {
                        visited[x][y] = true;
                        continue;
                    }

                    int width = 1;
                    while (x + width < Chunk.SIZE && !visited[x + width][y]) {
                        BlockType t = chunk.getBlock(x + width, y, z);
                        if (t != type || !isAir(world, baseX + x + width, ny, nz)) {
                            break;
                        }
                        width++;
                    }

                    int height = 1;
                    outer: while (y + height < Chunk.SIZE) {
                        for (int w = 0; w < width; w++) {
                            if (visited[x + w][y + height]) {
                                break outer;
                            }
                            BlockType t = chunk.getBlock(x + w, y + height, z);
                            if (t != type || !isAir(world, baseX + x + w, baseY + y + height, nz)) {
                                break outer;
                            }
                        }
                        height++;
                    }

                    for (int dy = 0; dy < height; dy++) {
                        for (int dx = 0; dx < width; dx++) {
                            visited[x + dx][y + dy] = true;
                        }
                    }

                    float[] color = shade(colorFor(type), shadeFactor);
                    float x1 = baseX + x;
                    float x2 = baseX + x + width;
                    float y1 = baseY + y;
                    float y2 = baseY + y + height;
                    float zPlane = baseZ + z + (positive ? 1 : 0);
                    if (positive) {
                        addFace(data, color, x1, y1, zPlane, x2, y1, zPlane, x2, y2, zPlane, x1, y2, zPlane);
                    } else {
                        addFace(data, color, x2, y1, zPlane, x1, y1, zPlane, x1, y2, zPlane, x2, y2, zPlane);
                    }
                }
            }
        }
    }

    private static void meshYZ(List<Float> data, World world, Chunk chunk,
            int baseX, int baseY, int baseZ, boolean positive) {
        for (int x = 0; x < Chunk.SIZE; x++) {
            boolean[][] visited = new boolean[Chunk.SIZE][Chunk.SIZE]; // [y][z]
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int y = 0; y < Chunk.SIZE; y++) {
                    if (visited[y][z]) {
                        continue;
                    }
                    BlockType type = chunk.getBlock(x, y, z);
                    int nx = baseX + x + (positive ? 1 : -1);
                    int ny = baseY + y;
                    int nz = baseZ + z;
                    if (type == BlockType.AIR || !isAir(world, nx, ny, nz)) {
                        visited[y][z] = true;
                        continue;
                    }

                    int width = 1;
                    while (y + width < Chunk.SIZE && !visited[y + width][z]) {
                        BlockType t = chunk.getBlock(x, y + width, z);
                        if (t != type || !isAir(world, nx, baseY + y + width, nz)) {
                            break;
                        }
                        width++;
                    }

                    int height = 1;
                    outer: while (z + height < Chunk.SIZE) {
                        for (int w = 0; w < width; w++) {
                            if (visited[y + w][z + height]) {
                                break outer;
                            }
                            BlockType t = chunk.getBlock(x, y + w, z + height);
                            if (t != type || !isAir(world, nx, baseY + y + w, baseZ + z + height)) {
                                break outer;
                            }
                        }
                        height++;
                    }

                    for (int dz = 0; dz < height; dz++) {
                        for (int dy = 0; dy < width; dy++) {
                            visited[y + dy][z + dz] = true;
                        }
                    }

                    float[] color = shade(colorFor(type), 0.7f);
                    float xPlane = baseX + x + (positive ? 1 : 0);
                    float y1 = baseY + y;
                    float y2 = baseY + y + width;
                    float z1 = baseZ + z;
                    float z2 = baseZ + z + height;
                    if (positive) {
                        addFace(data, color, xPlane, y1, z2, xPlane, y1, z1, xPlane, y2, z1, xPlane, y2, z2);
                    } else {
                        addFace(data, color, xPlane, y1, z1, xPlane, y1, z2, xPlane, y2, z2, xPlane, y2, z1);
                    }
                }
            }
        }
    }

    private static void meshXZ(List<Float> data, World world, Chunk chunk,
            int baseX, int baseY, int baseZ, boolean positive) {
        float shadeFactor = positive ? 1.0f : 0.5f;
        for (int y = 0; y < Chunk.SIZE; y++) {
            boolean[][] visited = new boolean[Chunk.SIZE][Chunk.SIZE]; // [x][z]
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    if (visited[x][z]) {
                        continue;
                    }
                    BlockType type = chunk.getBlock(x, y, z);
                    int nx = baseX + x;
                    int ny = baseY + y + (positive ? 1 : -1);
                    int nz = baseZ + z;
                    if (type == BlockType.AIR || !isAir(world, nx, ny, nz)) {
                        visited[x][z] = true;
                        continue;
                    }

                    int width = 1;
                    while (x + width < Chunk.SIZE && !visited[x + width][z]) {
                        BlockType t = chunk.getBlock(x + width, y, z);
                        if (t != type || !isAir(world, baseX + x + width, ny, nz)) {
                            break;
                        }
                        width++;
                    }

                    int height = 1;
                    outer: while (z + height < Chunk.SIZE) {
                        for (int w = 0; w < width; w++) {
                            if (visited[x + w][z + height]) {
                                break outer;
                            }
                            BlockType t = chunk.getBlock(x + w, y, z + height);
                            if (t != type || !isAir(world, baseX + x + w, ny, baseZ + z + height)) {
                                break outer;
                            }
                        }
                        height++;
                    }

                    for (int dz = 0; dz < height; dz++) {
                        for (int dx = 0; dx < width; dx++) {
                            visited[x + dx][z + dz] = true;
                        }
                    }

                    float[] color = shade(colorFor(type), shadeFactor);
                    float x1 = baseX + x;
                    float x2 = baseX + x + width;
                    float z1 = baseZ + z;
                    float z2 = baseZ + z + height;
                    float yPlane = baseY + y + (positive ? 1 : 0);
                    if (positive) {
                        addFace(data, color, x1, yPlane, z2, x2, yPlane, z2, x2, yPlane, z1, x1, yPlane, z1);
                    } else {
                        addFace(data, color, x1, yPlane, z1, x2, yPlane, z1, x2, yPlane, z2, x1, yPlane, z2);
                    }
                }
            }
        }
    }

    private static void addFace(List<Float> data, float[] color,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4) {
        addVertex(data, x1, y1, z1, color);
        addVertex(data, x2, y2, z2, color);
        addVertex(data, x3, y3, z3, color);
        addVertex(data, x4, y4, z4, color);
    }

    private static void addVertex(List<Float> data, float x, float y, float z, float[] c) {
        data.add(x);
        data.add(y);
        data.add(z);
        data.add(c[0]);
        data.add(c[1]);
        data.add(c[2]);
    }

    private static float[] shade(float[] base, float factor) {
        return new float[] { base[0] * factor, base[1] * factor, base[2] * factor };
    }

    private static boolean isAir(World world, int x, int y, int z) {
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cy = Math.floorDiv(y, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        Chunk chunk = world.getChunkIfLoaded(cx, cy, cz);
        if (chunk == null) {
            // Treat missing chunks as solid to avoid temporary seams.
            return false;
        }
        int lx = Math.floorMod(x, Chunk.SIZE);
        int ly = Math.floorMod(y, Chunk.SIZE);
        int lz = Math.floorMod(z, Chunk.SIZE);
        return chunk.getBlock(lx, ly, lz) == BlockType.AIR;
    }

    private static float[] colorFor(BlockType type) {
        return switch (type) {
            case GRASS -> new float[] { 0.235f, 0.69f, 0.26f };
            case DIRT -> new float[] { 0.545f, 0.27f, 0.075f };
            case STONE -> new float[] { 0.5f, 0.5f, 0.5f };
            default -> new float[] { 1f, 1f, 1f };
        };
    }

    /**
     * Renders the mesh using the cached VBO.
     */
    public void render() {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glVertexPointer(3, GL_FLOAT, 24, 0);
        glColorPointer(3, GL_FLOAT, 24, 12);
        glDrawArrays(GL_QUADS, 0, vertexCount);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Releases the underlying GL resources.
     */
    public void dispose() {
        glDeleteBuffers(vbo);
    }
}
