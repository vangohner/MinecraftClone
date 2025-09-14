package com.minecraftclone;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

/**
 * Simple renderer backed by LWJGL and OpenGL providing a rudimentary 3D engine
 * with depth buffering and perspective projection. This replaces the previous
 * Java2D based renderer.
 */
public class WorldRenderer {
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private boolean fullscreen;
    private int windowedX;
    private int windowedY;
    private int windowedWidth = DEFAULT_WIDTH;
    private int windowedHeight = DEFAULT_HEIGHT;
    /** Movement speed in world units per second. */
    private static final double MOVE_SPEED = 6.0;
    private static final double MOUSE_SENSITIVITY = 0.002;
    private static final int LOD1_STEP = 2;
    private static final int LOD2_STEP = 4;
    /** Number of chunks to render in each direction from the player. */
    private int renderDistance;

    private final World world;
    private final Player player;
    private int lod1Start;
    private int lod2Start;
    private long window;
    private double lastMouseX;
    private double lastMouseY;
    /** Whether chunk borders should be outlined. */
    private boolean showChunkBorders;
    /** Whether player coordinates should be shown in the window title. */
    private boolean showCoordinates;
    /** Tracks whether the F3 key is currently pressed for debug shortcuts. */
    private boolean debugShortcutActive;
    /** Whether to display occlusion culling debug information. */
    private boolean debugOcclusion;
    private final OcclusionDebugStats occlusionStats = new OcclusionDebugStats();
    private final OcclusionDebugStats lastOcclusionStats = new OcclusionDebugStats();

    /** Number of chunks rendered in the most recent frame. */
    private int lastRenderedChunkCount;
    /** Scratch counter reset each frame before rendering. */
    private int renderedChunkCount;

    private final ExecutorService lodWorkers;
    private final Set<LodKey> pendingLods;
    private final Queue<LodResult> completedLods;

    /** View frustum planes computed each frame. Each plane is stored as [A,B,C,D]. */
    private final float[][] frustum = new float[6][4];

    /** Occlusion query objects for chunks. */
    private final Map<Chunk, Integer> chunkQueries = new HashMap<>();
    /** Cached visibility results from the last available query. */
    private final Map<Chunk, Boolean> chunkVisibility = new HashMap<>();

    public WorldRenderer(World world, Player player, int renderDistance, int lod1Start, int lod2Start) {
        this.world = world;
        this.player = player;
        this.renderDistance = renderDistance;
        this.lod1Start = lod1Start;
        this.lod2Start = lod2Start;
        this.showChunkBorders = world.isDebug();
        this.showCoordinates = world.isDebug();
        this.debugOcclusion = world.isDebug();
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.lodWorkers = Executors.newFixedThreadPool(threads);
        this.pendingLods = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.completedLods = new ConcurrentLinkedQueue<>();
    }

    /** Launches the rendering loop. */
    public void run() {
        init();
        loop();
        deleteQueries();
        world.shutdown();
        lodWorkers.shutdown();
        Callbacks.glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        window = glfwCreateWindow(width, height, "Minecraft Clone", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwSetKeyCallback(window, this::handleKey);
        glfwSetCursorPosCallback(window, this::handleMouse);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            width = w;
            height = h;
            updateProjection();
        });

        // Initialize the mouse position to the center of the window so that
        // the first mouse delta is well-defined.
        lastMouseX = width / 2.0;
        lastMouseY = height / 2.0;
        glfwSetCursorPos(window, lastMouseX, lastMouseY);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glClearColor(0.53f, 0.81f, 1f, 0f);

        updateProjection();
    }

    private void loop() {
        double lastTime = glfwGetTime();
        double fpsTimer = lastTime;
        int frames = 0;
        while (!glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            double deltaTime = now - lastTime;
            lastTime = now;

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Handle continuous movement input each frame.
            handleMovement(deltaTime);

            glLoadIdentity();
            glRotatef((float) Math.toDegrees(-player.getPitch()), 1f, 0f, 0f);
            glRotatef((float) Math.toDegrees(-player.getYaw()), 0f, 1f, 0f);
            glTranslatef((float) -player.getX(), (float) -player.getY(), (float) -player.getZ());

            updateFrustum();
            renderBlocks();
            ChunkMesh.flushDeletes();
            lastRenderedChunkCount = renderedChunkCount;

            glfwSwapBuffers(window);
            glfwPollEvents();

            frames++;
            if (now - fpsTimer >= 1.0) {
                String title = "Minecraft Clone - FPS: " + frames + " Chunks: " + lastRenderedChunkCount;
                if (showCoordinates) {
                    title += String.format(" XYZ: %.2f / %.2f / %.2f", player.getX(), player.getY(), player.getZ());
                }
                if (debugOcclusion) {
                    title += String.format(" Vis:%d Occl:%d Pend:%d", lastOcclusionStats.visible,
                            lastOcclusionStats.occluded, lastOcclusionStats.pending);
                }
                glfwSetWindowTitle(window, title);
                frames = 0;
                fpsTimer += 1.0;
            }
        }
    }

    private void renderBlocks() {
        processLodResults();
        renderedChunkCount = 0;
        if (debugOcclusion) {
            occlusionStats.reset();
        }
        int playerChunkX = (int) Math.floor(player.getX() / Chunk.SIZE);
        int playerChunkY = (int) Math.floor(player.getY() / Chunk.SIZE);
        int playerChunkZ = (int) Math.floor(player.getZ() / Chunk.SIZE);
        int radius = renderDistance;

        List<int[]> positions = new ArrayList<>();
        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            for (int cy = playerChunkY - radius; cy <= playerChunkY + radius; cy++) {
                for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                    int dx = cx - playerChunkX;
                    int dy = cy - playerChunkY;
                    int dz = cz - playerChunkZ;
                    int distSq = dx * dx + dy * dy + dz * dz;
                    positions.add(new int[] { cx, cy, cz, distSq });
                }
            }
        }
        positions.sort(Comparator.comparingInt(p -> p[3]));

        // Depth prepass rendering bounding boxes of loaded chunks with meshes
        glColorMask(false, false, false, false);
        for (int[] p : positions) {
            int cx = p[0];
            int cy = p[1];
            int cz = p[2];
            int baseX = cx * Chunk.SIZE;
            int baseY = cy * Chunk.SIZE;
            int baseZ = cz * Chunk.SIZE;
            if (!boxInFrustum(baseX, baseY, baseZ,
                    baseX + Chunk.SIZE, baseY + Chunk.SIZE, baseZ + Chunk.SIZE)) {
                continue;
            }
            Chunk chunk = world.getChunkIfLoaded(cx, cy, cz);
            if (chunk == null || chunk.isOccluded() || chunk.getMesh() == null) {
                continue;
            }
            renderBoundingBox(baseX, baseY, baseZ);
        }
        glColorMask(true, true, true, true);

        List<RenderEntry> toRender = new ArrayList<>();
        for (int[] p : positions) {
            int cx = p[0];
            int cy = p[1];
            int cz = p[2];
            int baseX = cx * Chunk.SIZE;
            int baseY = cy * Chunk.SIZE;
            int baseZ = cz * Chunk.SIZE;
            if (!boxInFrustum(baseX, baseY, baseZ,
                    baseX + Chunk.SIZE, baseY + Chunk.SIZE, baseZ + Chunk.SIZE)) {
                continue;
            }
            if (world.isChunkOccluded(cx, cy, cz)) {
                continue;
            }
            world.requestChunk(cx, cy, cz, playerChunkX, playerChunkY, playerChunkZ);
            Chunk chunk = world.getChunkIfLoaded(cx, cy, cz);
            if (chunk == null || chunk.isOccluded()) {
                continue;
            }

            int query = getQuery(chunk);
            boolean available = glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) != 0;
            if (available) {
                int samples = glGetQueryObjecti(query, GL_QUERY_RESULT);
                chunkVisibility.put(chunk, samples > 0);
            }
            boolean visible = chunkVisibility.getOrDefault(chunk, Boolean.TRUE);

            if (!visible) {
                if (debugOcclusion) {
                    occlusionStats.recordResult(false);
                }
                glBeginQuery(GL_SAMPLES_PASSED, query);
                glColorMask(false, false, false, false);
                glDepthMask(false);
                renderBoundingBox(baseX, baseY, baseZ);
                glDepthMask(true);
                glColorMask(true, true, true, true);
                glEndQuery(GL_SAMPLES_PASSED);
                if (debugOcclusion) {
                    renderDebugBox(baseX, baseY, baseZ, 1f, 0f, 0f);
                }
                continue;
            }

            if (debugOcclusion) {
                if (available) {
                    occlusionStats.recordResult(true);
                } else {
                    occlusionStats.recordPending();
                }
            }
            int dist = Math.max(Math.max(Math.abs(cx - playerChunkX), Math.abs(cy - playerChunkY)),
                    Math.abs(cz - playerChunkZ));
            toRender.add(new RenderEntry(chunk, baseX, baseY, baseZ, dist, query, !available));
        }

        // Clear depth from prepass so chunk meshes aren't self-occluded
        glClear(GL_DEPTH_BUFFER_BIT);

        for (RenderEntry entry : toRender) {
            boolean rendered = false;
            glBeginQuery(GL_SAMPLES_PASSED, entry.query);
            if (entry.dist > lod2Start) {
                rendered = renderLod(entry.chunk, entry.baseX, entry.baseY, entry.baseZ, LOD2_STEP);
            } else if (entry.dist > lod1Start) {
                rendered = renderLod(entry.chunk, entry.baseX, entry.baseY, entry.baseZ, LOD1_STEP);
            } else {
                if (entry.chunk.isDirty() || entry.chunk.getMesh() == null) {
                    ChunkMesh old = entry.chunk.getMesh();
                    if (old != null) {
                        old.dispose();
                    }
                    entry.chunk.setMesh(ChunkMesh.build(world, entry.chunk, entry.baseX, entry.baseY, entry.baseZ));
                }
                ChunkMesh mesh = entry.chunk.getMesh();
                if (mesh != null) {
                    mesh.render();
                    rendered = true;
                }
            }
            glEndQuery(GL_SAMPLES_PASSED);

            if (rendered) {
                renderedChunkCount++;
            }
            if (debugOcclusion) {
                if (entry.pending) {
                    renderDebugBox(entry.baseX, entry.baseY, entry.baseZ, 1f, 1f, 0f);
                } else {
                    renderDebugBox(entry.baseX, entry.baseY, entry.baseZ, 0f, 1f, 0f);
                }
            }
            if (showChunkBorders) {
                renderChunkDebug(entry.chunk, entry.baseX, entry.baseY, entry.baseZ);
            }
        }
        if (debugOcclusion) {
            lastOcclusionStats.visible = occlusionStats.visible;
            lastOcclusionStats.occluded = occlusionStats.occluded;
            lastOcclusionStats.pending = occlusionStats.pending;
        }
    }

    private boolean renderLod(Chunk chunk, int baseX, int baseY, int baseZ, int step) {
        if (chunk.isLodStepEmpty(step)) {
            return false;
        }
        ChunkMesh mesh = chunk.getLodMesh(step);
        boolean dirty = chunk.isLodStepDirty(step);
        if (mesh != null && !dirty) {
            mesh.render();
            return true;
        }
        LodKey key = new LodKey(chunk, step);
        if (pendingLods.add(key)) {
            lodWorkers.submit(() -> {
                FloatBuffer buf = ChunkMesh.buildLodBuffer(world, chunk, baseX, baseY, baseZ, step);
                if (buf.limit() == 0) {
                    completedLods.add(new LodResult(chunk, step, null));
                } else {
                    completedLods.add(new LodResult(chunk, step, buf));
                }
                pendingLods.remove(key);
            });
        }
        if (mesh != null) {
            mesh.render();
            return true;
        }
        return false;
    }

    private void processLodResults() {
        LodResult res;
        while ((res = completedLods.poll()) != null) {
            if (res.buffer == null) {
                res.chunk.markLodStepEmpty(res.step);
            } else {
                ChunkMesh mesh = ChunkMesh.upload(res.buffer);
                res.chunk.setLodMesh(res.step, mesh);
            }
        }
    }

    private static class LodKey {
        final Chunk chunk;
        final int step;

        LodKey(Chunk chunk, int step) {
            this.chunk = chunk;
            this.step = step;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LodKey other)) return false;
            return chunk == other.chunk && step == other.step;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(chunk) * 31 + step;
        }
    }

    private static class LodResult {
        final Chunk chunk;
        final int step;
        final FloatBuffer buffer;

        LodResult(Chunk chunk, int step, FloatBuffer buffer) {
            this.chunk = chunk;
            this.step = step;
            this.buffer = buffer;
        }
    }

    /** Data for chunks that will be rendered in the main pass. */
    private static class RenderEntry {
        final Chunk chunk;
        final int baseX;
        final int baseY;
        final int baseZ;
        final int dist;
        final int query;
        final boolean pending;

        RenderEntry(Chunk chunk, int baseX, int baseY, int baseZ, int dist, int query, boolean pending) {
            this.chunk = chunk;
            this.baseX = baseX;
            this.baseY = baseY;
            this.baseZ = baseZ;
            this.dist = dist;
            this.query = query;
            this.pending = pending;
        }
    }

    /** Tracks occlusion query results for debugging. */
    static class OcclusionDebugStats {
        int visible;
        int occluded;
        int pending;

        void reset() {
            visible = occluded = pending = 0;
        }

        void recordResult(boolean vis) {
            if (vis) {
                visible++;
            } else {
                occluded++;
            }
        }

        void recordPending() {
            pending++;
        }
    }

    /** Returns the occlusion query object for the given chunk, creating it if necessary. */
    private int getQuery(Chunk chunk) {
        return chunkQueries.computeIfAbsent(chunk, c -> glGenQueries());
    }

    /** Deletes all allocated occlusion query objects. */
    private void deleteQueries() {
        for (int q : chunkQueries.values()) {
            glDeleteQueries(q);
        }
        chunkQueries.clear();
        chunkVisibility.clear();
    }

    /** Renders a solid axis-aligned bounding box for the chunk. */
    private void renderBoundingBox(int baseX, int baseY, int baseZ) {
        float x1 = baseX;
        float y1 = baseY;
        float z1 = baseZ;
        float x2 = baseX + Chunk.SIZE;
        float y2 = baseY + Chunk.SIZE;
        float z2 = baseZ + Chunk.SIZE;
        glBegin(GL_QUADS);
        // Faces wound counter-clockwise so front sides point outward
        // +X face
        glVertex3f(x2, y1, z1); glVertex3f(x2, y2, z1); glVertex3f(x2, y2, z2); glVertex3f(x2, y1, z2);
        // -X face
        glVertex3f(x1, y1, z2); glVertex3f(x1, y2, z2); glVertex3f(x1, y2, z1); glVertex3f(x1, y1, z1);
        // +Y face
        glVertex3f(x1, y2, z1); glVertex3f(x1, y2, z2); glVertex3f(x2, y2, z2); glVertex3f(x2, y2, z1);
        // -Y face
        glVertex3f(x1, y1, z1); glVertex3f(x2, y1, z1); glVertex3f(x2, y1, z2); glVertex3f(x1, y1, z2);
        // +Z face
        glVertex3f(x1, y1, z2); glVertex3f(x2, y1, z2); glVertex3f(x2, y2, z2); glVertex3f(x1, y2, z2);
        // -Z face
        glVertex3f(x1, y1, z1); glVertex3f(x1, y2, z1); glVertex3f(x2, y2, z1); glVertex3f(x2, y1, z1);
        glEnd();
    }

    private void drawWireBox(float x1, float y1, float z1, float x2, float y2, float z2) {
        glBegin(GL_LINES);
        // bottom square
        glVertex3f(x1, y1, z1); glVertex3f(x2, y1, z1);
        glVertex3f(x2, y1, z1); glVertex3f(x2, y1, z2);
        glVertex3f(x2, y1, z2); glVertex3f(x1, y1, z2);
        glVertex3f(x1, y1, z2); glVertex3f(x1, y1, z1);
        // top square
        glVertex3f(x1, y2, z1); glVertex3f(x2, y2, z1);
        glVertex3f(x2, y2, z1); glVertex3f(x2, y2, z2);
        glVertex3f(x2, y2, z2); glVertex3f(x1, y2, z2);
        glVertex3f(x1, y2, z2); glVertex3f(x1, y2, z1);
        // vertical edges
        glVertex3f(x1, y1, z1); glVertex3f(x1, y2, z1);
        glVertex3f(x2, y1, z1); glVertex3f(x2, y2, z1);
        glVertex3f(x2, y1, z2); glVertex3f(x2, y2, z2);
        glVertex3f(x1, y1, z2); glVertex3f(x1, y2, z2);
        glEnd();
    }

    private void renderChunkDebug(Chunk chunk, int baseX, int baseY, int baseZ) {
        float r, g, b;
        if (chunk.getOrigin() == Chunk.Origin.LOADED) {
            r = 0f; g = 1f; b = 0f; // green for loaded
        } else {
            r = 1f; g = 0f; b = 0f; // red for generated
        }
        glDisable(GL_DEPTH_TEST);
        glColor3f(r, g, b);
        drawWireBox(baseX, baseY, baseZ, baseX + Chunk.SIZE, baseY + Chunk.SIZE, baseZ + Chunk.SIZE);
        glColor3f(1f, 1f, 1f);
        glEnable(GL_DEPTH_TEST);
    }

    private void renderDebugBox(int baseX, int baseY, int baseZ, float r, float g, float b) {
        glDisable(GL_DEPTH_TEST);
        glColor3f(r, g, b);
        drawWireBox(baseX, baseY, baseZ, baseX + Chunk.SIZE, baseY + Chunk.SIZE, baseZ + Chunk.SIZE);
        glColor3f(1f, 1f, 1f);
        glEnable(GL_DEPTH_TEST);
    }

    /** Extracts the six view frustum planes from the current projection and modelview matrices. */
    private void updateFrustum() {
        FloatBuffer proj = BufferUtils.createFloatBuffer(16);
        FloatBuffer modl = BufferUtils.createFloatBuffer(16);
        glGetFloatv(GL_PROJECTION_MATRIX, proj);
        glGetFloatv(GL_MODELVIEW_MATRIX, modl);
        proj.rewind();
        modl.rewind();
        float[] p = new float[16];
        float[] m = new float[16];
        proj.get(p);
        modl.get(m);

        float[] clip = new float[16];
        clip[0] = m[0] * p[0] + m[1] * p[4] + m[2] * p[8] + m[3] * p[12];
        clip[1] = m[0] * p[1] + m[1] * p[5] + m[2] * p[9] + m[3] * p[13];
        clip[2] = m[0] * p[2] + m[1] * p[6] + m[2] * p[10] + m[3] * p[14];
        clip[3] = m[0] * p[3] + m[1] * p[7] + m[2] * p[11] + m[3] * p[15];

        clip[4] = m[4] * p[0] + m[5] * p[4] + m[6] * p[8] + m[7] * p[12];
        clip[5] = m[4] * p[1] + m[5] * p[5] + m[6] * p[9] + m[7] * p[13];
        clip[6] = m[4] * p[2] + m[5] * p[6] + m[6] * p[10] + m[7] * p[14];
        clip[7] = m[4] * p[3] + m[5] * p[7] + m[6] * p[11] + m[7] * p[15];

        clip[8] = m[8] * p[0] + m[9] * p[4] + m[10] * p[8] + m[11] * p[12];
        clip[9] = m[8] * p[1] + m[9] * p[5] + m[10] * p[9] + m[11] * p[13];
        clip[10] = m[8] * p[2] + m[9] * p[6] + m[10] * p[10] + m[11] * p[14];
        clip[11] = m[8] * p[3] + m[9] * p[7] + m[10] * p[11] + m[11] * p[15];

        clip[12] = m[12] * p[0] + m[13] * p[4] + m[14] * p[8] + m[15] * p[12];
        clip[13] = m[12] * p[1] + m[13] * p[5] + m[14] * p[9] + m[15] * p[13];
        clip[14] = m[12] * p[2] + m[13] * p[6] + m[14] * p[10] + m[15] * p[14];
        clip[15] = m[12] * p[3] + m[13] * p[7] + m[14] * p[11] + m[15] * p[15];

        // Right
        frustum[0][0] = clip[3] - clip[0];
        frustum[0][1] = clip[7] - clip[4];
        frustum[0][2] = clip[11] - clip[8];
        frustum[0][3] = clip[15] - clip[12];
        normalizePlane(0);
        // Left
        frustum[1][0] = clip[3] + clip[0];
        frustum[1][1] = clip[7] + clip[4];
        frustum[1][2] = clip[11] + clip[8];
        frustum[1][3] = clip[15] + clip[12];
        normalizePlane(1);
        // Bottom
        frustum[2][0] = clip[3] + clip[1];
        frustum[2][1] = clip[7] + clip[5];
        frustum[2][2] = clip[11] + clip[9];
        frustum[2][3] = clip[15] + clip[13];
        normalizePlane(2);
        // Top
        frustum[3][0] = clip[3] - clip[1];
        frustum[3][1] = clip[7] - clip[5];
        frustum[3][2] = clip[11] - clip[9];
        frustum[3][3] = clip[15] - clip[13];
        normalizePlane(3);
        // Far
        frustum[4][0] = clip[3] - clip[2];
        frustum[4][1] = clip[7] - clip[6];
        frustum[4][2] = clip[11] - clip[10];
        frustum[4][3] = clip[15] - clip[14];
        normalizePlane(4);
        // Near
        frustum[5][0] = clip[3] + clip[2];
        frustum[5][1] = clip[7] + clip[6];
        frustum[5][2] = clip[11] + clip[10];
        frustum[5][3] = clip[15] + clip[14];
        normalizePlane(5);
    }

    private void normalizePlane(int i) {
        float a = frustum[i][0];
        float b = frustum[i][1];
        float c = frustum[i][2];
        float t = (float) Math.sqrt(a * a + b * b + c * c);
        frustum[i][0] /= t;
        frustum[i][1] /= t;
        frustum[i][2] /= t;
        frustum[i][3] /= t;
    }

    private boolean boxInFrustum(float x1, float y1, float z1, float x2, float y2, float z2) {
        for (int i = 0; i < 6; i++) {
            float a = frustum[i][0];
            float b = frustum[i][1];
            float c = frustum[i][2];
            float d = frustum[i][3];
            if (a * x1 + b * y1 + c * z1 + d < 0 &&
                a * x2 + b * y1 + c * z1 + d < 0 &&
                a * x1 + b * y2 + c * z1 + d < 0 &&
                a * x2 + b * y2 + c * z1 + d < 0 &&
                a * x1 + b * y1 + c * z2 + d < 0 &&
                a * x2 + b * y1 + c * z2 + d < 0 &&
                a * x1 + b * y2 + c * z2 + d < 0 &&
                a * x2 + b * y2 + c * z2 + d < 0) {
                return false;
            }
        }
        return true;
    }

    private void handleKey(long window, int key, int scancode, int action, int mods) {
        // Handle F3 separately so we can track key release.
        if (key == GLFW_KEY_F3) {
            if (action == GLFW_PRESS) {
                debugShortcutActive = true;
            } else if (action == GLFW_RELEASE) {
                debugShortcutActive = false;
            }
            return;
        }

        if (key == GLFW_KEY_F11 && action == GLFW_PRESS) {
            toggleFullscreen();
            return;
        }

        if (action != GLFW_PRESS && action != GLFW_REPEAT) {
            return;
        }

        if (key == GLFW_KEY_G && debugShortcutActive) {
            showChunkBorders = !showChunkBorders;
            System.out.println("Chunk borders " + (showChunkBorders ? "enabled" : "disabled"));
            return;
        }
        if (key == GLFW_KEY_C && debugShortcutActive) {
            showCoordinates = !showCoordinates;
            System.out.println("Coordinates " + (showCoordinates ? "shown" : "hidden"));
            return;
        }
        if (key == GLFW_KEY_O && debugShortcutActive) {
            debugOcclusion = !debugOcclusion;
            System.out.println("Occlusion debug " + (debugOcclusion ? "enabled" : "disabled"));
            return;
        }

        switch (key) {
            case GLFW_KEY_LEFT -> player.rotate(0.1);
            case GLFW_KEY_RIGHT -> player.rotate(-0.1);
            case GLFW_KEY_UP -> player.pitch(0.05);
            case GLFW_KEY_DOWN -> player.pitch(-0.05);
            case GLFW_KEY_PAGE_UP -> adjustRenderDistance(1);
            case GLFW_KEY_PAGE_DOWN -> adjustRenderDistance(-1);
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);
            default -> {}
        }
    }

    private void handleMovement(double deltaTime) {
        double forward = 0;
        double right = 0;
        double up = 0;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            forward += 1;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            forward -= 1;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            right += 1;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            right -= 1;
        }
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
            up += 1;
        }
        if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
            up -= 1;
        }

        if (forward != 0 || right != 0) {
            moveRelative(right, forward, deltaTime);
        }
        if (up != 0) {
            moveVertical(up, deltaTime);
        }
    }

    private void moveRelative(double right, double forward, double deltaTime) {
        double yaw = player.getYaw();
        double speed = MOVE_SPEED * deltaTime;
        double dx = (-forward * Math.sin(yaw) + right * Math.cos(yaw)) * speed;
        double dz = (-forward * Math.cos(yaw) - right * Math.sin(yaw)) * speed;
        player.move(dx, 0, dz);
    }

    private void moveVertical(double up, double deltaTime) {
        player.move(0, up * MOVE_SPEED * deltaTime, 0);
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) {
            int[] x = new int[1];
            int[] y = new int[1];
            int[] w = new int[1];
            int[] h = new int[1];
            glfwGetWindowPos(window, x, y);
            glfwGetWindowSize(window, w, h);
            windowedX = x[0];
            windowedY = y[0];
            windowedWidth = w[0];
            windowedHeight = h[0];

            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode mode = glfwGetVideoMode(monitor);
            glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), GLFW_DONT_CARE);
            width = mode.width();
            height = mode.height();
        } else {
            glfwSetWindowMonitor(window, NULL, windowedX, windowedY, windowedWidth, windowedHeight, GLFW_DONT_CARE);
            width = windowedWidth;
            height = windowedHeight;
        }
        lastMouseX = width / 2.0;
        lastMouseY = height / 2.0;
        glfwSetCursorPos(window, lastMouseX, lastMouseY);
        updateProjection();
    }

    private void handleMouse(long window, double xpos, double ypos) {
        double dx = xpos - lastMouseX;
        double dy = ypos - lastMouseY;
        lastMouseX = xpos;
        lastMouseY = ypos;
        player.rotate(-dx * MOUSE_SENSITIVITY);
        player.pitch(-dy * MOUSE_SENSITIVITY);
    }

    private void adjustRenderDistance(int delta) {
        renderDistance = Math.max(1, renderDistance + delta);
        System.out.println("Render distance: " + renderDistance);
        updateProjection();
    }

    private void updateProjection() {
        glViewport(0, 0, width, height);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) width / height;
        float far = (renderDistance + 2) * Chunk.SIZE * (float) Math.sqrt(3);
        setPerspective(70f, aspect, 0.1f, far);
        glMatrixMode(GL_MODELVIEW);
    }

    private void setPerspective(float fov, float aspect, float near, float far) {
        double y = near * Math.tan(Math.toRadians(fov) / 2.0);
        double x = y * aspect;
        glFrustum(-x, x, -y, y, near, far);
    }
}
