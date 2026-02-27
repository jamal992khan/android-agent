# Gradle Wrapper JAR

The `gradle-wrapper.jar` file is intentionally not included in this repository to keep it lightweight.

GitHub Actions will download Gradle automatically when building.

If you want to build locally, you have two options:

## Option 1: Let Gradle download itself (recommended)
```bash
# First build will download Gradle wrapper
./gradlew assembleDebug
```

## Option 2: Use Android Studio
Android Studio includes Gradle and will handle wrapper setup automatically.

## Why no JAR?

- Keeps repo smaller
- GitHub Actions doesn't need it (uses `setup-java` action)
- Local builds auto-download on first run
- Reduces potential security concerns (verifiable download from Gradle servers)
