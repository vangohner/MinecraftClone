# MinecraftClone

This project aims to build a Minecraft-like game from scratch in Java. The current version provides a very small starting point:

- Basic world data structures (`BlockType`, `Chunk`, `World`)
- A simple `Player` class
- A Swing window that renders a flat chunk in a minimal isometric view

## Running

This repository uses the Gradle wrapper. To launch the demo straight from the sources run:

```
./gradlew run
```

To start the world with a specific seed pass it as the first argument:

```
./gradlew run --args="12345"
```

You can optionally specify the initial render distance (in chunks) as the second argument:

```
./gradlew run --args="12345 8"
```

Two additional arguments control where the mid- and far-distance LOD meshes
begin. The following example starts drawing LOD level 1 beyond 8 chunks and LOD
level 2 beyond 16 chunks while rendering out to 24 chunks:

```
./gradlew run --args="12345 24 8 16"
```

Far-away chunks are drawn using simplified heightmap meshes with progressively
coarser steps, allowing much higher render distances without large pauses.

On Windows use `gradlew.bat run` instead.

To build a runnable JAR execute:

```
./gradlew jar
java -jar app/build/libs/app.jar
```

## World Saving

Generated chunks are stored as simple binary files inside the `world/` directory
and are automatically loaded on startup and saved on shutdown.

## Debugging

Pass `--debug-chunks` as a command-line argument to log when chunks are generated or loaded. When enabled, generated chunks are outlined in red while those loaded from disk are shown in green, making it easy to spot persistence issues.

This is only the first step toward a full clone. Future work will include richer rendering, input handling, world generation and more.
