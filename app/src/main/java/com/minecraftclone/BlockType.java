package com.minecraftclone;

/**
 * Basic block types supported by the toy Minecraft clone.
 */
public enum BlockType {
    AIR(' '),
    DIRT('D'),
    GRASS('G'),
    STONE('S');

    private final char display;

    BlockType(char display) {
        this.display = display;
    }

    /**
     * Character used when printing the world to the console.
     */
    public char getDisplay() {
        return display;
    }
}
