#pragma once

// Basic block types supported by the toy Minecraft clone.
// Ported from the Java version.

enum class BlockType : char {
    Air   = ' ',
    Dirt  = 'D',
    Grass = 'G',
    Stone = 'S',
    Sand  = 'A',
    Water = 'W',
    Snow  = 'N',
    Ice   = 'I'
};

inline char getDisplay(BlockType type) {
    return static_cast<char>(type);
}
