package com.minecraftclone;

/**
 * Represents the player in the world.
 */
public class Player {
    private double x;
    private double y;
    private double z;
    /**
     * Player yaw in radians. 0 means looking toward positive Z.
     */
    private double yaw;
    /**
     * Player pitch in radians. 0 means looking horizontally.
     */
    private double pitch;

    public Player(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = 0;
        this.pitch = 0;
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

    public double getYaw() {
        return yaw;
    }

    public void rotate(double dyaw) {
        this.yaw += dyaw;
    }

    public double getPitch() {
        return pitch;
    }

    public void pitch(double dpitch) {
        this.pitch = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, this.pitch + dpitch));
    }

    @Override
    public String toString() {
        return "Player{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
