#!/usr/bin/env sh
set -eu

find_kotlinc() {
  if command -v kotlinc >/dev/null 2>&1; then
    command -v kotlinc
    return 0
  fi

  android_studio_kotlinc="$HOME/Applications/Android Studio.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc"
  if [ -x "$android_studio_kotlinc" ]; then
    printf '%s\n' "$android_studio_kotlinc"
    return 0
  fi

  return 1
}

if [ -x ./gradlew ]; then
  exec ./gradlew run
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle run
fi

if kotlinc_bin="$(find_kotlinc)"; then
  mkdir -p build/classes
  "$kotlinc_bin" src/main/kotlin/com/komkat/xml2image/XmlResourceConverterGui.kt -include-runtime -d build/xml2image.jar
  exec java -jar build/xml2image.jar
fi

printf '%s\n' "Missing Kotlin build tool. Install Gradle or Kotlin compiler, then run ./run.sh again." >&2
exit 1
