package com.minecraftclone;

/**
 * Represents the player in the world.
 */
public class Player {
    private double x;
    private double y;
    private double z;

    public Player(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void move(double dx, double dy, double dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Override
    public String toString() {
        return "Player{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
