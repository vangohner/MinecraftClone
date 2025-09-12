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

On Windows use `gradlew.bat run` instead.

To build a runnable JAR execute:

```
./gradlew jar
java -jar app/build/libs/app.jar
```

This is only the first step toward a full clone. Future work will include richer rendering, input handling, world generation and more.
