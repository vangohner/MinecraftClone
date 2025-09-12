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
        setPerspective(70f, aspect, 0.1f, 100f);
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
        Chunk chunk = world.getChunk(0, 0, 0);
        for (int y = 0; y < Chunk.SIZE; y++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    BlockType type = chunk.getBlock(x, y, z);
                    if (type != BlockType.AIR) {
                        drawCube(chunk, x, y, z, type);
                    }
                }
            }
        }
    }

    private void drawCube(Chunk chunk, int x, int y, int z, BlockType type) {
        float[] base = colorFor(type);
        glBegin(GL_QUADS);

        if (isAir(chunk, x, y, z + 1)) {
            glColor3f(base[0] * 0.9f, base[1] * 0.9f, base[2] * 0.9f);
            glVertex3f(x, y, z + 1);
            glVertex3f(x + 1, y, z + 1);
            glVertex3f(x + 1, y + 1, z + 1);
            glVertex3f(x, y + 1, z + 1);
        }

        if (isAir(chunk, x, y, z - 1)) {
            glColor3f(base[0] * 0.8f, base[1] * 0.8f, base[2] * 0.8f);
            glVertex3f(x + 1, y, z);
            glVertex3f(x, y, z);
            glVertex3f(x, y + 1, z);
            glVertex3f(x + 1, y + 1, z);
        }

        if (isAir(chunk, x - 1, y, z)) {
            glColor3f(base[0] * 0.7f, base[1] * 0.7f, base[2] * 0.7f);
            glVertex3f(x, y, z);
            glVertex3f(x, y, z + 1);
            glVertex3f(x, y + 1, z + 1);
            glVertex3f(x, y + 1, z);
        }

        if (isAir(chunk, x + 1, y, z)) {
            glColor3f(base[0] * 0.7f, base[1] * 0.7f, base[2] * 0.7f);
            glVertex3f(x + 1, y, z + 1);
            glVertex3f(x + 1, y, z);
            glVertex3f(x + 1, y + 1, z);
            glVertex3f(x + 1, y + 1, z + 1);
        }

        if (isAir(chunk, x, y + 1, z)) {
            glColor3f(base[0], base[1], base[2]);
            glVertex3f(x, y + 1, z + 1);
            glVertex3f(x + 1, y + 1, z + 1);
            glVertex3f(x + 1, y + 1, z);
            glVertex3f(x, y + 1, z);
        }

        if (isAir(chunk, x, y - 1, z)) {
            glColor3f(base[0] * 0.5f, base[1] * 0.5f, base[2] * 0.5f);
            glVertex3f(x, y, z);
            glVertex3f(x + 1, y, z);
            glVertex3f(x + 1, y, z + 1);
            glVertex3f(x, y, z + 1);
        }

        glEnd();
    }

    private boolean isAir(Chunk chunk, int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            return true;
        }
        return chunk.getBlock(x, y, z) == BlockType.AIR;
    }

    private float[] colorFor(BlockType type) {
        return switch (type) {
            case GRASS -> new float[] {0.235f, 0.69f, 0.26f};
            case DIRT -> new float[] {0.545f, 0.27f, 0.075f};
            case STONE -> new float[] {0.5f, 0.5f, 0.5f};
            default -> new float[] {1f, 1f, 1f};
        };
    }

    private void handleKey(long window, int key, int scancode, int action, int mods) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) {
            return;
        }
        switch (key) {
            case GLFW_KEY_LEFT -> player.rotate(-0.1);
            case GLFW_KEY_RIGHT -> player.rotate(0.1);
            case GLFW_KEY_UP -> player.pitch(-0.05);
            case GLFW_KEY_DOWN -> player.pitch(0.05);
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
        double dx = (forward * Math.sin(yaw) + right * Math.cos(yaw)) * MOVE_SPEED;
        double dz = (forward * Math.cos(yaw) - right * Math.sin(yaw)) * MOVE_SPEED;
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
        player.rotate(dx * MOUSE_SENSITIVITY);
        player.pitch(dy * MOUSE_SENSITIVITY);
    }

    private void setPerspective(float fov, float aspect, float near, float far) {
        double y = near * Math.tan(Math.toRadians(fov) / 2.0);
        double x = y * aspect;
        glFrustum(-x, x, -y, y, near, far);
    }
}
