plugins {
    application
}

// No external repositories or dependencies are used to avoid network access.

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.minecraftclone.App"
}
