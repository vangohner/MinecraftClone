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
        for (int y = 0; y < Chunk.SIZE; y++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    BlockType type = chunk.getBlock(x, y, z);
                    if (type == BlockType.AIR) {
                        continue;
                    }
                    int wx = baseX + x;
                    int wy = baseY + y;
                    int wz = baseZ + z;
                    float[] base = colorFor(type);

                    if (isAir(world, wx, wy, wz + 1)) {
                        addFace(data, shade(base, 0.9f),
                                wx, wy, wz + 1,
                                wx + 1, wy, wz + 1,
                                wx + 1, wy + 1, wz + 1,
                                wx, wy + 1, wz + 1);
                    }
                    if (isAir(world, wx, wy, wz - 1)) {
                        addFace(data, shade(base, 0.8f),
                                wx + 1, wy, wz,
                                wx, wy, wz,
                                wx, wy + 1, wz,
                                wx + 1, wy + 1, wz);
                    }
                    if (isAir(world, wx - 1, wy, wz)) {
                        addFace(data, shade(base, 0.7f),
                                wx, wy, wz,
                                wx, wy, wz + 1,
                                wx, wy + 1, wz + 1,
                                wx, wy + 1, wz);
                    }
                    if (isAir(world, wx + 1, wy, wz)) {
                        addFace(data, shade(base, 0.7f),
                                wx + 1, wy, wz + 1,
                                wx + 1, wy, wz,
                                wx + 1, wy + 1, wz,
                                wx + 1, wy + 1, wz + 1);
                    }
                    if (isAir(world, wx, wy + 1, wz)) {
                        addFace(data, shade(base, 1.0f),
                                wx, wy + 1, wz + 1,
                                wx + 1, wy + 1, wz + 1,
                                wx + 1, wy + 1, wz,
                                wx, wy + 1, wz);
                    }
                    if (isAir(world, wx, wy - 1, wz)) {
                        addFace(data, shade(base, 0.5f),
                                wx, wy, wz,
                                wx + 1, wy, wz,
                                wx + 1, wy, wz + 1,
                                wx, wy, wz + 1);
                    }
                }
            }
        }
        FloatBuffer buf = BufferUtils.createFloatBuffer(data.size());
        for (Float f : data) {
            buf.put(f);
        }
        buf.flip();
        return buf;
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
        return world.getBlock(x, y, z) == BlockType.AIR;
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
