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

    private final World world;
    private final Player player;
    private long window;

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
                        drawCube(x, y, z, type);
                    }
                }
            }
        }
    }

    private void drawCube(int x, int y, int z, BlockType type) {
        float[] base = colorFor(type);
        glBegin(GL_QUADS);
        // front
        glColor3f(base[0] * 0.9f, base[1] * 0.9f, base[2] * 0.9f);
        glVertex3f(x, y, z + 1);
        glVertex3f(x + 1, y, z + 1);
        glVertex3f(x + 1, y + 1, z + 1);
        glVertex3f(x, y + 1, z + 1);
        // back
        glColor3f(base[0] * 0.8f, base[1] * 0.8f, base[2] * 0.8f);
        glVertex3f(x + 1, y, z);
        glVertex3f(x, y, z);
        glVertex3f(x, y + 1, z);
        glVertex3f(x + 1, y + 1, z);
        // left
        glColor3f(base[0] * 0.7f, base[1] * 0.7f, base[2] * 0.7f);
        glVertex3f(x, y, z);
        glVertex3f(x, y, z + 1);
        glVertex3f(x, y + 1, z + 1);
        glVertex3f(x, y + 1, z);
        // right
        glColor3f(base[0] * 0.7f, base[1] * 0.7f, base[2] * 0.7f);
        glVertex3f(x + 1, y, z + 1);
        glVertex3f(x + 1, y, z);
        glVertex3f(x + 1, y + 1, z);
        glVertex3f(x + 1, y + 1, z + 1);
        // top
        glColor3f(base[0], base[1], base[2]);
        glVertex3f(x, y + 1, z + 1);
        glVertex3f(x + 1, y + 1, z + 1);
        glVertex3f(x + 1, y + 1, z);
        glVertex3f(x, y + 1, z);
        // bottom
        glColor3f(base[0] * 0.5f, base[1] * 0.5f, base[2] * 0.5f);
        glVertex3f(x, y, z);
        glVertex3f(x + 1, y, z);
        glVertex3f(x + 1, y, z + 1);
        glVertex3f(x, y, z + 1);
        glEnd();
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

            case GLFW_KEY_W -> moveRelative(0, 1);
            case GLFW_KEY_S -> moveRelative(0, -1);
            case GLFW_KEY_A -> moveRelative(-1, 0);
            case GLFW_KEY_D -> moveRelative(1, 0);
            default -> {}
        }
    }

    private void moveRelative(double right, double forward) {
        double yaw = player.getYaw();
        double dx = forward * Math.sin(yaw) + right * Math.cos(yaw);

        double dz = forward * Math.cos(yaw) - right * Math.sin(yaw);

        double newX = player.getX() + dx;
        double newZ = player.getZ() + dz;
        if (newX >= 0 && newX < Chunk.SIZE && newZ >= 0 && newZ < Chunk.SIZE) {
            player.move(dx, 0, dz);
        }
    }

    private void setPerspective(float fov, float aspect, float near, float far) {
        double y = near * Math.tan(Math.toRadians(fov) / 2.0);
        double x = y * aspect;
        glFrustum(-x, x, -y, y, near, far);
    }
}
