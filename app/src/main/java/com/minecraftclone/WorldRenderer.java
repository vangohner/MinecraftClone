package com.minecraftclone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JPanel;

/**
 * Simple first-person renderer using a very naive perspective projection.
 * This is not a real 3D engine but provides a basic first-person view so
 * the player can look and move around the world similar to Minecraft.
 */
public class WorldRenderer extends JPanel implements KeyListener {
    private static final double FOV = Math.toRadians(70);

    private final World world;
    private final Player player;

    public WorldRenderer(World world, Player player) {
        this.world = world;
        this.player = player;
        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.CYAN.darker());
        setFocusable(true);
        addKeyListener(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        int width = getWidth();
        int height = getHeight();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double yaw = player.getYaw();
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double scale = width / (2 * Math.tan(FOV / 2));

        List<Face> faces = new ArrayList<>();
        Chunk chunk = world.getChunk(0, 0, 0);
        for (int y = 0; y < Chunk.SIZE; y++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    BlockType type = chunk.getBlock(x, y, z);
                    if (type != BlockType.AIR) {
                        addBlockFaces(faces, type, x, y, z, px, py, pz, cosYaw, sinYaw, scale, width / 2, height / 2);
                    }
                }
            }
        }

        faces.sort(Comparator.comparingDouble(f -> -f.depth));
        for (Face f : faces) {
            g2d.setColor(f.color);
            g2d.fillPolygon(f.poly);
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawPolygon(f.poly);
        }
    }

    private void addBlockFaces(List<Face> faces, BlockType type, int x, int y, int z,
            double px, double py, double pz, double cosYaw, double sinYaw, double scale, int offsetX, int offsetY) {
        // Vertices for each face of the unit cube
        double[][][] faceVerts = {
                { {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1} }, // front
                { {1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0} }, // back
                { {0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0} }, // left
                { {1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1} }, // right
                { {0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0} }, // top
                { {0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1} }  // bottom
        };
        double[][] normals = {
                {0, 0, 1},
                {0, 0, -1},
                {-1, 0, 0},
                {1, 0, 0},
                {0, 1, 0},
                {0, -1, 0}
        };

        Color base = colorFor(type);
        for (int i = 0; i < faceVerts.length; i++) {
            double[] n = normals[i];
            double rnx = n[0] * cosYaw - n[2] * sinYaw;
            double rnz = n[0] * sinYaw + n[2] * cosYaw;
            double rny = n[1];
            if (rnz <= 0) {
                continue; // facing away
            }
            Polygon poly = new Polygon();
            double depth = 0;
            boolean visible = true;
            for (double[] v : faceVerts[i]) {
                double wx = x + v[0];
                double wy = y + v[1];
                double wz = z + v[2];

                double dx = wx - px;
                double dy = wy - py;
                double dz = wz - pz;
                double rx = dx * cosYaw - dz * sinYaw;
                double rz = dx * sinYaw + dz * cosYaw;
                double ry = dy;
                if (rz <= 0.1) {
                    visible = false;
                    break;
                }
                int sx = (int) (rx * scale / rz) + offsetX;
                int sy = (int) (-ry * scale / rz) + offsetY;
                poly.addPoint(sx, sy);
                depth += rz;
            }
            if (!visible) {
                continue;
            }
            depth /= 4.0;
            double brightness = 0.6 + 0.4 * rnz + 0.2 * Math.max(0, rny);
            Color shaded = shade(base, brightness);
            faces.add(new Face(poly, shaded, depth));
        }
    }

    private Color shade(Color color, double factor) {
        factor = Math.max(0, Math.min(1, factor));
        int r = (int) (color.getRed() * factor);
        int g = (int) (color.getGreen() * factor);
        int b = (int) (color.getBlue() * factor);
        return new Color(r, g, b);
    }

    private Color colorFor(BlockType type) {
        return switch (type) {
            case GRASS -> new Color(0x3CB043);
            case DIRT -> new Color(0x8B4513);
            case STONE -> Color.GRAY;
            default -> Color.WHITE;
        };
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT -> {
                player.rotate(-0.1);
                repaint();
            }
            case KeyEvent.VK_RIGHT -> {
                player.rotate(0.1);
                repaint();
            }
            case KeyEvent.VK_W, KeyEvent.VK_UP -> moveRelative(0, 1);
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> moveRelative(0, -1);
            case KeyEvent.VK_A -> moveRelative(-1, 0);
            case KeyEvent.VK_D -> moveRelative(1, 0);
            default -> {
            }
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
            repaint();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // no-op
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // no-op
    }

    private static class Face {
        final Polygon poly;
        final Color color;
        final double depth;

        Face(Polygon poly, Color color, double depth) {
            this.poly = poly;
            this.color = color;
            this.depth = depth;
        }
    }
}

