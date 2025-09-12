package com.minecraftclone;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;

import javax.swing.JPanel;

/**
 * Very simple isometric renderer that draws blocks in a Swing window.
 * This is not a full 3D engine but provides a basic 3D-like view using
 * isometric projection so the world can be visualised outside of the
 * console.
 */
public class WorldRenderer extends JPanel {
    private static final int TILE_WIDTH = 40;
    private static final int TILE_HEIGHT = 20;

    private final World world;

    public WorldRenderer(World world) {
        this.world = world;
        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.CYAN.darker());
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        int offsetX = getWidth() / 2;
        int offsetY = 50;

        Chunk chunk = world.getChunk(0, 0, 0);
        for (int y = 0; y < Chunk.SIZE; y++) {
            for (int x = 0; x < Chunk.SIZE; x++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    BlockType type = chunk.getBlock(x, y, z);
                    if (type != BlockType.AIR) {
                        drawBlock(g2d, type, x, y, z, offsetX, offsetY);
                    }
                }
            }
        }
    }

    private void drawBlock(Graphics2D g2d, BlockType type, int x, int y, int z, int offsetX, int offsetY) {
        int sx = (x - z) * TILE_WIDTH / 2 + offsetX;
        int sy = (x + z) * TILE_HEIGHT / 2 - y * TILE_HEIGHT + offsetY;

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
}

