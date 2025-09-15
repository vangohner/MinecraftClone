#include <iostream>
#include "BlockType.hpp"

int main() {
    BlockType block = BlockType::Grass;
    std::cout << getDisplay(block) << std::endl;
    return 0;
}
