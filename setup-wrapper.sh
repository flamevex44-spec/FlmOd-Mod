#!/bin/sh
# Downloads the official Gradle 8.11.1 wrapper JAR.
# Run once before your first ./gradlew build.
set -e
DEST="gradle/wrapper/gradle-wrapper.jar"
URL="https://github.com/gradle/gradle/raw/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
echo "Fetching $URL -> $DEST"
curl -fL "$URL" -o "$DEST"
echo "Done. You can now run: ./gradlew build"
