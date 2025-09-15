package com.minecraftclone;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;

/**
 * Minimal marching cubes implementation that operates on the raw density
 * field produced by {@link ChunkGenerator}. The algorithm subdivides each
 * cube into six tetrahedra and extracts triangles wherever the implicit
 * surface crosses the edges of a tetrahedron. This avoids the large lookup
 * tables of the classic implementation while still producing smooth terrain.
 */
public class MarchingCubes {

    /** Offsets for the eight cube corners. */
    private static final float[][] CUBE_CORNERS = {
            {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0},
            {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}
    };

    /** Indices of the six tetrahedra composing a cube. */
    private static final int[][] TETRAHEDRA = {
            {0, 5, 1, 6},
            {0, 1, 2, 6},
            {0, 2, 3, 6},
            {0, 3, 7, 6},
            {0, 7, 4, 6},
            {0, 4, 5, 6}
    };

    /** Edge pairs within a tetrahedron. */
    private static final int[][] TET_EDGES = {
            {0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}
    };

    private MarchingCubes() {
    }

    /**
     * Builds a FloatBuffer containing position and color data for the specified
     * chunk region using marching cubes. Each vertex consists of 6 floats:
     * x, y, z, r, g, b.
     */
    public static FloatBuffer buildChunk(ChunkGenerator generator, int baseX, int baseY, int baseZ, int size) {
        double[][][] densities = new double[size + 1][size + 1][size + 1];
        for (int x = 0; x <= size; x++) {
            for (int y = 0; y <= size; y++) {
                for (int z = 0; z <= size; z++) {
                    densities[x][y][z] = generator.sampleDensity(baseX + x, baseY + y, baseZ + z);
                }
            }
        }

        List<Float> data = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    double[] cube = new double[8];
                    float[][] cubePos = new float[8][3];
                    for (int i = 0; i < 8; i++) {
                        int dx = i & 1;
                        int dy = (i >> 1) & 1;
                        int dz = (i >> 2) & 1;
                        cube[i] = densities[x + dx][y + dy][z + dz];
                        cubePos[i][0] = baseX + x + CUBE_CORNERS[i][0];
                        cubePos[i][1] = baseY + y + CUBE_CORNERS[i][1];
                        cubePos[i][2] = baseZ + z + CUBE_CORNERS[i][2];
                    }

                    for (int[] tet : TETRAHEDRA) {
                        double[] d = {cube[tet[0]], cube[tet[1]], cube[tet[2]], cube[tet[3]]};
                        float[][] p = {cubePos[tet[0]], cubePos[tet[1]], cubePos[tet[2]], cubePos[tet[3]]};

                        int inside = 0;
                        for (double v : d) {
                            if (v > 0) {
                                inside++;
                            }
                        }
                        if (inside == 0 || inside == 4) {
                            continue;
                        }

                        List<float[]> verts = new ArrayList<>();
                        for (int[] e : TET_EDGES) {
                            int a = e[0];
                            int b = e[1];
                            double da = d[a];
                            double db = d[b];
                            if ((da > 0 && db <= 0) || (da <= 0 && db > 0)) {
                                double t = da / (da - db);
                                float[] pa = p[a];
                                float[] pb = p[b];
                                float vx = (float) (pa[0] + t * (pb[0] - pa[0]));
                                float vy = (float) (pa[1] + t * (pb[1] - pa[1]));
                                float vz = (float) (pa[2] + t * (pb[2] - pa[2]));
                                verts.add(new float[] { vx, vy, vz });
                            }
                        }

                        if (verts.size() < 3) {
                            continue;
                        }

                        addTriangle(data, verts.get(0), verts.get(1), verts.get(2), generator);
                        if (verts.size() == 4) {
                            addTriangle(data, verts.get(0), verts.get(2), verts.get(3), generator);
                        }
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

    private static void addTriangle(List<Float> data, float[] a, float[] b, float[] c, ChunkGenerator gen) {
        float[] ca = colorFor(a[1], gen);
        float[] cb = colorFor(b[1], gen);
        float[] cc = colorFor(c[1], gen);
        addVertex(data, a, ca);
        addVertex(data, b, cb);
        addVertex(data, c, cc);
    }

    private static void addVertex(List<Float> data, float[] p, float[] c) {
        data.add(p[0]);
        data.add(p[1]);
        data.add(p[2]);
        data.add(c[0]);
        data.add(c[1]);
        data.add(c[2]);
    }

    private static float[] colorFor(float y, ChunkGenerator gen) {
        if (y <= gen.getWaterLevel()) {
            return new float[] { 0f, 0.3f, 0.8f };
        }
        if (y > gen.getSnowLine()) {
            return new float[] { 1f, 1f, 1f };
        }
        return new float[] { 0.235f, 0.69f, 0.26f };
    }
}

