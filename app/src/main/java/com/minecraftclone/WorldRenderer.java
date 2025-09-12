package com.minecraftclone;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

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
    private static final double MOVE_SPEED = 0.1;
    private static final double MOUSE_SENSITIVITY = 0.002;
    /** Number of chunks to render in each direction from the player. */
    private static final int RENDER_DISTANCE = 4;

    private final World world;
    private final Player player;
    private long window;
    private double lastMouseX;
    private double lastMouseY;

    public WorldRenderer(World world, Player player) {
        this.world = world;
        this.player = player;
    }

    /** Launches the rendering loop. */
    public void run() {
        init();
        loop();
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

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) WIDTH / HEIGHT;
        // Extend the far plane so distant chunks remain visible when using a
        // larger render distance.
        setPerspective(70f, aspect, 0.1f, 500f);
        glMatrixMode(GL_MODELVIEW);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Handle continuous movement input each frame.
            handleMovement();

            glLoadIdentity();
            glRotatef((float) Math.toDegrees(-player.getPitch()), 1f, 0f, 0f);
            glRotatef((float) Math.toDegrees(-player.getYaw()), 0f, 1f, 0f);
            glTranslatef((float) -player.getX(), (float) -player.getY(), (float) -player.getZ());

            renderBlocks();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderBlocks() {
        int playerChunkX = (int) Math.floor(player.getX() / Chunk.SIZE);
        int playerChunkY = (int) Math.floor(player.getY() / Chunk.SIZE);
        int playerChunkZ = (int) Math.floor(player.getZ() / Chunk.SIZE);
        int radius = RENDER_DISTANCE;

        for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
            int baseX = cx * Chunk.SIZE;
            for (int cy = playerChunkY - radius; cy <= playerChunkY + radius; cy++) {
                int baseY = cy * Chunk.SIZE;
                for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                    int baseZ = cz * Chunk.SIZE;
                    Chunk chunk = world.getChunk(cx, cy, cz);
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
            }
        }
    }

    private void handleKey(long window, int key, int scancode, int action, int mods) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) {
            return;
        }
        switch (key) {
            case GLFW_KEY_LEFT -> player.rotate(0.1);
            case GLFW_KEY_RIGHT -> player.rotate(-0.1);
            case GLFW_KEY_UP -> player.pitch(0.05);
            case GLFW_KEY_DOWN -> player.pitch(-0.05);
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);
            default -> {}
        }
    }

    private void handleMovement() {
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
            moveRelative(right, forward);
        }
        if (up != 0) {
            moveVertical(up);
        }
    }

    private void moveRelative(double right, double forward) {
        double yaw = player.getYaw();
        double dx = (-forward * Math.sin(yaw) + right * Math.cos(yaw)) * MOVE_SPEED;
        double dz = (-forward * Math.cos(yaw) - right * Math.sin(yaw)) * MOVE_SPEED;
        player.move(dx, 0, dz);
    }

    private void moveVertical(double up) {
        player.move(0, up * MOVE_SPEED, 0);
    }

    private void handleMouse(long window, double xpos, double ypos) {
        double dx = xpos - lastMouseX;
        double dy = ypos - lastMouseY;
        lastMouseX = xpos;
        lastMouseY = ypos;
        player.rotate(-dx * MOUSE_SENSITIVITY);
        player.pitch(-dy * MOUSE_SENSITIVITY);
    }

    private void setPerspective(float fov, float aspect, float near, float far) {
        double y = near * Math.tan(Math.toRadians(fov) / 2.0);
        double x = y * aspect;
        glFrustum(-x, x, -y, y, near, far);
    }
}
