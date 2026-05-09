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
  exec ./gradlew packageDmg
fi

if command -v gradle >/dev/null 2>&1; then
  exec gradle packageDmg
fi

if ! command -v jpackage >/dev/null 2>&1; then
  printf '%s\n' "Missing jpackage. Install JDK 21+, then run ./package-dmg.sh again." >&2
  exit 1
fi

if ! command -v jlink >/dev/null 2>&1; then
  printf '%s\n' "Missing jlink. Install JDK 21+, then run ./package-dmg.sh again." >&2
  exit 1
fi

if kotlinc_bin="$(find_kotlinc)"; then
  app_version="0.1.1"
  rm -rf build/jpackage/runtime
  mkdir -p build/jpackage/input build/jpackage/output
  "$kotlinc_bin" src/main/kotlin/com/komkat/xml2image/XmlResourceConverterGui.kt -include-runtime -d build/jpackage/input/xml2image.jar
  jlink \
    --add-modules java.base,java.desktop \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress zip-9 \
    --output build/jpackage/runtime
  exec jpackage \
    --type dmg \
    --name "XML Resource Converter" \
    --app-version "$app_version" \
    --vendor "Komkat" \
    --input build/jpackage/input \
    --main-jar xml2image.jar \
    --main-class com.komkat.xml2image.XmlResourceConverterGuiKt \
    --runtime-image build/jpackage/runtime \
    --icon src/main/resources/macos/AppIcon.icns \
    --dest build/jpackage/output \
    --java-options "-Dapple.laf.useScreenMenuBar=true"
fi

printf '%s\n' "Missing Kotlin build tool. Install Gradle or Kotlin compiler, then run ./package-dmg.sh again." >&2
exit 1
