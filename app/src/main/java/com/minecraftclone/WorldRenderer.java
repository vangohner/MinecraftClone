package com.minecraftclone;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

/**
 * Simple renderer backed by LWJGL and OpenGL providing a rudimentary 3D engine
 * with depth buffering and perspective projection. This replaces the previous
 * Java2D based renderer.
 */
public class WorldRenderer {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
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
    /** Tracks whether the F3 key is currently pressed for debug shortcuts. */
    private boolean debugShortcutActive;

    /** View frustum planes computed each frame. Each plane is stored as [A,B,C,D]. */
    private final float[][] frustum = new float[6][4];
    // TODO: Track visible neighbors for occlusion culling.

    public WorldRenderer(World world, Player player, int renderDistance, int lod1Start, int lod2Start) {
        this.world = world;
        this.player = player;
        this.renderDistance = renderDistance;
        this.lod1Start = lod1Start;
        this.lod2Start = lod2Start;
        this.showChunkBorders = world.isDebug();
    }

    /** Launches the rendering loop. */
    public void run() {
        init();
        loop();
        world.shutdown();
        Callbacks.glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        window = glfwCreateWindow(WIDTH, HEIGHT, "Minecraft Clone", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwSetKeyCallback(window, this::handleKey);
        glfwSetCursorPosCallback(window, this::handleMouse);
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        // Initialize the mouse position to the center of the window so that
        // the first mouse delta is well-defined.
        lastMouseX = WIDTH / 2.0;
        lastMouseY = HEIGHT / 2.0;
        glfwSetCursorPos(window, lastMouseX, lastMouseY);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
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

            glfwSwapBuffers(window);
            glfwPollEvents();

            frames++;
            if (now - fpsTimer >= 1.0) {
                glfwSetWindowTitle(window, "Minecraft Clone - FPS: " + frames);
                frames = 0;
                fpsTimer += 1.0;
            }
        }
    }

    private void renderBlocks() {
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
            world.requestChunk(cx, cy, cz, playerChunkX, playerChunkY, playerChunkZ);
            Chunk chunk = world.getChunkIfLoaded(cx, cy, cz);
            if (chunk == null) {
                continue;
            }
            int dist = Math.max(Math.max(Math.abs(cx - playerChunkX), Math.abs(cy - playerChunkY)),
                    Math.abs(cz - playerChunkZ));
            if (dist > lod2Start) {
                renderLod(chunk, baseX, baseY, baseZ, LOD2_STEP);
            } else if (dist > lod1Start) {
                renderLod(chunk, baseX, baseY, baseZ, LOD1_STEP);
            } else {
                if (chunk.isDirty() || chunk.getMesh() == null) {
                    ChunkMesh old = chunk.getMesh();
                    if (old != null) {
                        old.dispose();
                    }
                    chunk.setMesh(ChunkMesh.build(world, chunk, baseX, baseY, baseZ));
                }
                ChunkMesh mesh = chunk.getMesh();
                if (mesh != null) {
                    mesh.render();
                }
            }
            if (showChunkBorders) {
                renderChunkDebug(chunk, baseX, baseY, baseZ);
            }
        }
    }

    private void renderLod(Chunk chunk, int baseX, int baseY, int baseZ, int step) {
        ChunkMesh mesh = chunk.getLodMesh(step);
        if (mesh == null) {
            mesh = ChunkMesh.buildLod(chunk, baseX, baseY, baseZ, step);
            chunk.setLodMesh(step, mesh);
        }
        mesh.render();
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
        glBegin(GL_LINES);
        float x1 = baseX;
        float y1 = baseY;
        float z1 = baseZ;
        float x2 = baseX + Chunk.SIZE;
        float y2 = baseY + Chunk.SIZE;
        float z2 = baseZ + Chunk.SIZE;
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

        if (action != GLFW_PRESS && action != GLFW_REPEAT) {
            return;
        }

        if (key == GLFW_KEY_G && debugShortcutActive) {
            showChunkBorders = !showChunkBorders;
            System.out.println("Chunk borders " + (showChunkBorders ? "enabled" : "disabled"));
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
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) WIDTH / HEIGHT;
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
