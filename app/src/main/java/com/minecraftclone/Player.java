package com.minecraftclone;

/**
 * Represents the player in the world.
 */
public class Player {
    private double x;
    private double y;
    private double z;
    /**
     * Player yaw in radians. 0 means looking toward negative Z (forward).
     */
    private double yaw;
    /**
     * Player pitch in radians. Positive looks downward.
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

    public double getPitch() {
        return pitch;
    }

    public void rotateYaw(double dyaw) {
        this.yaw += dyaw;
    }

    public void rotatePitch(double dpitch) {
        this.pitch += dpitch;
        // clamp to avoid flipping
        double limit = Math.PI / 2 - 0.01;
        if (this.pitch > limit) this.pitch = limit;
        if (this.pitch < -limit) this.pitch = -limit;
    }

    @Override
    public String toString() {
        return "Player{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
