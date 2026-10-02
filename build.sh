#!/usr/bin/env bash
# Gradle wrapper that brings its own JDK.
#
# This machine has no `java` on PATH — only Android Studio's bundled JBR and a JDK
# under ~/tools — and the gradlew script needs a JVM to launch before any Gradle
# setting can take effect. So org.gradle.java.home in gradle.properties cannot fix
# it; the launcher's environment has to be right. Hence this.
#
#   ./build.sh test              run the unit tests
#   ./build.sh assembleDebug     build the APK
#   ./build.sh installDebug      build and push to an attached phone
set -euo pipefail

for candidate in "$HOME/tools/jdk-17.0.20.1+1" "$HOME/.jdks/jbr-21.0.11" /snap/android-studio/current/jbr; do
  if [ -x "$candidate/bin/java" ]; then
    export JAVA_HOME="$candidate"
    break
  fi
done

if [ -z "${JAVA_HOME:-}" ]; then
  echo "No JDK found. Install one or set JAVA_HOME." >&2
  exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"
exec "$(dirname "$0")/gradlew" "$@"
