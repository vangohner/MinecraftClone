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
    /** Width of the player collision box. */
    private static final double WIDTH = 0.6;
    /** Height of the player collision box. */
    private static final double HEIGHT = 2.0;
    private static final double HALF_WIDTH = WIDTH / 2.0;
    /** Height of the player's eyes above their feet. */
    private static final double EYE_HEIGHT = 1.7;

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

    /** Returns the world-space height of the player's eyes. */
    public double getEyeY() {
        return y + EYE_HEIGHT;
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
     * Updates the player's position applying velocity, gravity and collision with
     * world blocks.
     */
    public void update(World world, double dt) {
        vy -= GRAVITY * dt;

        double dxOrig = vx * dt;
        double dyOrig = vy * dt;
        double dzOrig = vz * dt;

        double dx = moveX(world, dxOrig);
        double dy = moveY(world, dyOrig);
        double dz = moveZ(world, dzOrig);

        if (dx != dxOrig) {
            vx = 0;
        }
        if (dy != dyOrig) {
            vy = 0;
        }
        if (dz != dzOrig) {
            vz = 0;
        }

        if (onGround) {
            vx -= vx * Math.min(1.0, FRICTION * dt);
            vz -= vz * Math.min(1.0, FRICTION * dt);
        }
    }

    private double moveX(World world, double dx) {
        if (dx > 0) {
            int start = (int) Math.floor(x + HALF_WIDTH);
            int end = (int) Math.floor(x + dx + HALF_WIDTH);
            int minY = (int) Math.floor(y);
            int maxY = (int) Math.floor(y + HEIGHT - 1e-9);
            int minZ = (int) Math.floor(z - HALF_WIDTH);
            int maxZ = (int) Math.floor(z + HALF_WIDTH - 1e-9);
            for (int bx = start; bx <= end; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        if (world.getBlock(bx, by, bz) != BlockType.AIR) {
                            double allowed = bx - (x + HALF_WIDTH);
                            if (allowed < dx) {
                                dx = allowed;
                            }
                        }
                    }
                }
            }
        } else if (dx < 0) {
            int start = (int) Math.floor(x + dx - HALF_WIDTH);
            int end = (int) Math.floor(x - HALF_WIDTH);
            int minY = (int) Math.floor(y);
            int maxY = (int) Math.floor(y + HEIGHT - 1e-9);
            int minZ = (int) Math.floor(z - HALF_WIDTH);
            int maxZ = (int) Math.floor(z + HALF_WIDTH - 1e-9);
            for (int bx = start; bx <= end; bx++) {
                for (int by = minY; by <= maxY; by++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        if (world.getBlock(bx, by, bz) != BlockType.AIR) {
                            double allowed = (bx + 1) - (x - HALF_WIDTH);
                            if (allowed > dx) {
                                dx = allowed;
                            }
                        }
                    }
                }
            }
        }
        x += dx;
        return dx;
    }

    private double moveY(World world, double dy) {
        double startDy = dy;
        if (dy > 0) {
            int start = (int) Math.floor(y + HEIGHT);
            int end = (int) Math.floor(y + HEIGHT + dy);
            int minX = (int) Math.floor(x - HALF_WIDTH);
            int maxX = (int) Math.floor(x + HALF_WIDTH - 1e-9);
            int minZ = (int) Math.floor(z - HALF_WIDTH);
            int maxZ = (int) Math.floor(z + HALF_WIDTH - 1e-9);
            for (int by = start; by <= end; by++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        if (world.getBlock(bx, by, bz) != BlockType.AIR) {
                            double allowed = by - (y + HEIGHT);
                            if (allowed < dy) {
                                dy = allowed;
                            }
                        }
                    }
                }
            }
        } else if (dy < 0) {
            int start = (int) Math.floor(y + dy);
            int end = (int) Math.floor(y - 1e-9);
            int minX = (int) Math.floor(x - HALF_WIDTH);
            int maxX = (int) Math.floor(x + HALF_WIDTH - 1e-9);
            int minZ = (int) Math.floor(z - HALF_WIDTH);
            int maxZ = (int) Math.floor(z + HALF_WIDTH - 1e-9);
            for (int by = start; by <= end; by++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    for (int bz = minZ; bz <= maxZ; bz++) {
                        if (world.getBlock(bx, by, bz) != BlockType.AIR) {
                            double allowed = (by + 1) - y;
                            if (allowed > dy) {
                                dy = allowed;
                            }
                        }
                    }
                }
            }
        }
        y += dy;
        onGround = startDy < 0 && dy != startDy;
        return dy;
    }

    private double moveZ(World world, double dz) {
        if (dz > 0) {
            int start = (int) Math.floor(z + HALF_WIDTH);
            int end = (int) Math.floor(z + dz + HALF_WIDTH);
            int minX = (int) Math.floor(x - HALF_WIDTH);
            int maxX = (int) Math.floor(x + HALF_WIDTH - 1e-9);
            int minY = (int) Math.floor(y);
            int maxY = (int) Math.floor(y + HEIGHT - 1e-9);
            for (int bz = start; bz <= end; bz++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    for (int by = minY; by <= maxY; by++) {
                        if (world.getBlock(bx, by, bz) != BlockType.AIR) {
                            double allowed = bz - (z + HALF_WIDTH);
                            if (allowed < dz) {
                                dz = allowed;
                            }
                        }
                    }
                }
            }
        } else if (dz < 0) {
            int start = (int) Math.floor(z + dz - HALF_WIDTH);
            int end = (int) Math.floor(z - HALF_WIDTH);
            int minX = (int) Math.floor(x - HALF_WIDTH);
            int maxX = (int) Math.floor(x + HALF_WIDTH - 1e-9);
            int minY = (int) Math.floor(y);
            int maxY = (int) Math.floor(y + HEIGHT - 1e-9);
            for (int bz = start; bz <= end; bz++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    for (int by = minY; by <= maxY; by++) {
                        if (world.getBlock(bx, by, bz) != BlockType.AIR) {
                            double allowed = (bz + 1) - (z - HALF_WIDTH);
                            if (allowed > dz) {
                                dz = allowed;
                            }
                        }
                    }
                }
            }
        }
        z += dz;
        return dz;
    }

    @Override
    public String toString() {
        return "Player{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
