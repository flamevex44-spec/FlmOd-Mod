#!/bin/sh
# Gradle wrapper bootstrap script.
# If the wrapper JAR is present (gradle/wrapper/gradle-wrapper.jar),
# it is used automatically. Otherwise this script falls through to a
# system-installed Gradle. See README for setup notes.

APP_NAME="Gradle"
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
JAVA_OPTS="${JAVA_OPTS:-}"

# Prefer wrapper JAR when present
if [ -f "$CLASSPATH" ]; then
    exec "$JAVA_HOME/bin/java" $JAVA_OPTS \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain "$@"
fi

# Fall back to system gradle
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

echo "ERROR: gradle-wrapper.jar not found and no system Gradle detected." >&2
echo "Run: ./setup-wrapper.sh   or install Gradle 8.11.1 manually." >&2
exit 1
