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
     * Player pitch in radians. 0 means looking horizontally. Positive looks downward.

     */
    private double pitch;

    /** Current velocity along each axis. */
    private double vx;
    private double vy;
    private double vz;

    /** Whether the player is currently on the ground. */
    private boolean onGround;

    private static final double MAX_SPEED = 6.0;
    private static final double FRICTION = 6.0;
    private static final double GRAVITY = 20.0;
    private static final double JUMP_VELOCITY = 8.0;

    public Player(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = 0;
        this.pitch = 0;
        this.vx = 0;
        this.vy = 0;
        this.vz = 0;
        this.onGround = false;
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

    /**
     * Applies horizontal acceleration relative to the player's current yaw.
     */
    public void accelerate(double ax, double az, double dt) {
        vx += ax * dt;
        vz += az * dt;
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed > MAX_SPEED) {
            double scale = MAX_SPEED / speed;
            vx *= scale;
            vz *= scale;
        }
    }

    /**
     * Attempts to make the player jump. Does nothing if the player is airborne.
     */
    public void jump() {
        if (onGround) {
            vy = JUMP_VELOCITY;
            onGround = false;
        }
    }

    /**
     * Updates the player's position applying velocity, gravity and ground
     * collision against the world blocks.
     */
    public void update(World world, double dt) {
        vy -= GRAVITY * dt;
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;

        if (onGround) {
            vx -= vx * Math.min(1.0, FRICTION * dt);
            vz -= vz * Math.min(1.0, FRICTION * dt);
        }

        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y - 1);
        int bz = (int) Math.floor(z);
        if (world.getBlock(bx, by, bz) != BlockType.AIR) {
            if (vy <= 0 && y < by + 1) {
                y = by + 1;
                vy = 0;
                onGround = true;
            }
        } else {
            onGround = false;
        }
    }

    @Override
    public String toString() {
        return "Player{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
