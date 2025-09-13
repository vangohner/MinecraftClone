# MinecraftClone

This project aims to build a Minecraft-like game from scratch in Java. The current version provides a very small starting point:

- Basic world data structures (`BlockType`, `Chunk`, `World`)
- A simple `Player` class
- A Swing window that renders a flat chunk in a minimal isometric view
- An "amplified" terrain generator featuring continents, towering mountains,
  floating islands and large cave networks

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

Chunks persist to disk as you play. Newly generated chunks are written
immediately to the `world/` directory, and any chunk whose blocks change is
saved again right after the modification. When the game shuts down, it flushes
any remaining chunks whose data differs from the last save and reports progress
so you know why the application stays open.

## Debugging

Pass `--debug-chunks` as a command-line argument to log when chunks are generated or loaded. When enabled, generated chunks are outlined in red while those loaded from disk are shown in green, making it easy to spot persistence issues.
Once in game, press **F3 + G** to toggle chunk border outlines on or off and **F3 + C** to show or hide your current coordinates in the window title.

This is only the first step toward a full clone. Future work will include richer rendering, input handling, world generation and more.
