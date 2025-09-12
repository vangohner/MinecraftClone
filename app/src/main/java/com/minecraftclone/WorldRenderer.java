package com.minecraftclone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JPanel;

/**
 * Very simple isometric renderer that draws blocks in a Swing window.
 * This is not a full 3D engine but provides a basic 3D-like view using
 * isometric projection so the world can be visualised outside of the
 * console.
 */
public class WorldRenderer extends JPanel implements KeyListener {
    private static final int TILE_WIDTH = 40;
    private static final int TILE_HEIGHT = 20;

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

        int offsetX = getWidth() / 2;
        int offsetY = getHeight() / 2;

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        Chunk chunk = world.getChunk(0, 0, 0);
        for (int y = 0; y < Chunk.SIZE; y++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    BlockType type = chunk.getBlock(x, y, z);
                    if (type != BlockType.AIR) {
                        drawBlock(g2d, type, x, y, z, px, py, pz, offsetX, offsetY);
                    }
                }
            }
        }
    }

    private void drawBlock(Graphics2D g2d, BlockType type, int x, int y, int z,
            double px, double py, double pz, int offsetX, int offsetY) {
        double relX = x - px;
        double relY = y - py;
        double relZ = z - pz;

        int sx = (int) ((relX - relZ) * TILE_WIDTH / 2) + offsetX;
        int sy = (int) ((relX + relZ) * TILE_HEIGHT / 2 - relY * TILE_HEIGHT) + offsetY;

        // Top face
        Polygon top = new Polygon();
        top.addPoint(sx, sy);
        top.addPoint(sx + TILE_WIDTH / 2, sy + TILE_HEIGHT / 2);
        top.addPoint(sx, sy + TILE_HEIGHT);
        top.addPoint(sx - TILE_WIDTH / 2, sy + TILE_HEIGHT / 2);
        g2d.setColor(colorFor(type));
        g2d.fillPolygon(top);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawPolygon(top);

        // Left face
        Polygon left = new Polygon();
        left.addPoint(sx - TILE_WIDTH / 2, sy + TILE_HEIGHT / 2);
        left.addPoint(sx, sy + TILE_HEIGHT);
        left.addPoint(sx, sy + TILE_HEIGHT * 2);
        left.addPoint(sx - TILE_WIDTH / 2, sy + TILE_HEIGHT + TILE_HEIGHT / 2);
        g2d.setColor(colorFor(type).darker());
        g2d.fillPolygon(left);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawPolygon(left);

        // Right face
        Polygon right = new Polygon();
        right.addPoint(sx + TILE_WIDTH / 2, sy + TILE_HEIGHT / 2);
        right.addPoint(sx, sy + TILE_HEIGHT);
        right.addPoint(sx, sy + TILE_HEIGHT * 2);
        right.addPoint(sx + TILE_WIDTH / 2, sy + TILE_HEIGHT + TILE_HEIGHT / 2);
        g2d.setColor(colorFor(type).darker().darker());
        g2d.fillPolygon(right);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawPolygon(right);
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
        double dx = 0;
        double dz = 0;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> dz = -1;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> dz = 1;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> dx = -1;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> dx = 1;
            default -> {
                return;
            }
        }

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
}

