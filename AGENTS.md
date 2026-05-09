# AGENTS.md

## Project Overview

This repository contains a Kotlin + Swing desktop app that batch converts Android
VectorDrawable XML files to PNG, JPG, or WebP.

The application is intentionally small:

- Main GUI, parser, renderer, and exporters live in
  `src/main/kotlin/com/komkat/xml2image/XmlResourceConverterGui.kt`.
- macOS app icon resource lives at `src/main/resources/macos/AppIcon.icns`.
- `assets/logo.png` is the source image used to generate the macOS icon.
- Packaging scripts live at `package-dmg.sh` and `package-dmg-lite.sh`.
  Prefer lite packages unless the user explicitly needs a bundled Java runtime.

Core responsibilities in the Kotlin file:

- `XmlResourceConverterGui`: Swing UI, drag-and-drop, file selection, conversion
  workflow, and progress/log display.
- `VectorDrawable`, `DrawCommand`, `PathData`, and `PathParser`: Android vector
  XML parsing and Java2D rendering.
- `ImageExporter`: PNG/JPG/WebP output. WebP uses Java ImageIO if available or
  falls back to `cwebp` from `PATH`.

## Requirements

- JDK 21 or newer.
- Gradle if using the Gradle tasks. There is currently no Gradle wrapper checked
  in, so do not assume `./gradlew` exists.
- Kotlin compiler (`kotlinc`) as a fallback for the shell scripts.
- Optional: `cwebp` for WebP export when no WebP ImageIO writer is installed.
  Gradle-built packages include a WebP ImageIO writer; direct `kotlinc`
  fallback builds still need `cwebp` for WebP export.
- macOS-only tools for DMG packaging: `jpackage`, `jlink`, and `hdiutil`
  depending on the packaging mode.

## Setup And Run

Run the app using the repository script:

```sh
./run.sh
```

The script prefers `./gradlew run` if a wrapper is later added, then `gradle run`,
then direct `kotlinc` compilation to `build/xml2image.jar`.

If Gradle is installed, this direct command is also supported:

```sh
gradle run
```

Build the runnable JAR with Gradle:

```sh
gradle jar
```

The Gradle build uses Kotlin JVM plugin `2.2.21`, Java toolchain 21, and
application main class `com.komkat.xml2image.XmlResourceConverterGuiKt`.
The JAR is self-contained with runtime dependencies so lightweight packages can
run with only Java 21+ installed.

## Versioning

The project version lives in `build.gradle.kts`. Shell packaging fallbacks also
carry the app version in `package-dmg.sh` and `package-dmg-lite.sh`; keep these
values in sync when bumping a release.

Use semantic versioning. For bug fixes, bump the patch version. For packaged
behavior changes, also add an entry to `CHANGELOG.md` using Keep a Changelog
sections such as `Added`, `Changed`, and `Fixed`.

## Packaging

Prefer lightweight packages that require Java 21+ on the target machine.

Create the preferred smaller macOS DMG that does not bundle Java:

```sh
./package-dmg-lite.sh
```

The lite package requires Java 21+ on the target Mac. DMG files are written under
`build/jpackage/output/`.

Build lightweight Windows/Linux archives that require Java 21+ on the target
machine:

```sh
gradle packageLite
```

Archives are written under `build/distributions/`.

Create a macOS DMG with a bundled stripped Java runtime only when requested or
when the target users cannot install Java separately:

```sh
./package-dmg.sh
```

This script delegates to Gradle's `packageDmg` task when Gradle is available.
Otherwise it compiles with `kotlinc`, builds a `jlink` runtime containing
`java.base` and `java.desktop`, then invokes `jpackage`.

## Testing Instructions

There is no automated test suite in the repository yet.

Before finishing behavior changes, at minimum run one of:

```sh
gradle jar
```

or:

```sh
./run.sh
```

For renderer/parser changes, manually verify representative VectorDrawable XML
inputs that include:

- fills and strokes
- alpha and transparent colors
- groups with rotation, scale, translation, and pivots
- path commands `M`, `L`, `H`, `V`, `C`, `S`, `Q`, `T`, `A`, and `Z`
- PNG with alpha, JPG white background, and WebP export

If adding tests, prefer focused JVM tests for `PathParser`, color parsing,
dimension parsing, output naming, and XML parsing/rendering edge cases.

## Code Style

- Keep the project dependency-light. It currently uses Kotlin stdlib, Swing,
  Java2D, DOM XML parsing, and ImageIO.
- Match existing Kotlin style: top-level helper functions, private classes and
  objects where possible, and explicit names for rendering/conversion concepts.
- Keep Swing work on the EDT and long-running conversion work in `SwingWorker`.
- Preserve the secure XML parser settings in `VectorDrawable.read`; do not enable
  external entity resolution.
- Keep user-facing messages concise and suitable for a desktop GUI dialog/log.
- Prefer structured Java/Kotlin APIs over ad hoc shelling out. The intentional
  exception is the `cwebp` fallback in `ImageExporter`.
- Avoid unrelated refactors in the large Kotlin source file unless they directly
  support the requested change.

## Build Artifacts

Generated outputs live under `build/` and should not be treated as source.
Examples include compiled JARs, jpackage inputs, generated runtimes, iconsets,
and DMG outputs.

## Common Gotchas

- The README mentions Gradle, but this checkout may not include `gradlew`; use
  `gradle` or the fallback scripts unless a wrapper is added.
- DMG packaging is macOS-specific.
- Gradle packages bundle a WebP ImageIO writer. Direct `kotlinc` fallback
  builds may still fail at runtime without `cwebp`.
- Android vector colors that reference resources other than
  `@android:color/transparent` are currently ignored by `parseColor`.
- The renderer supports a practical subset of VectorDrawable behavior; changes
  to path parsing or transforms need manual visual checks.

## Pull Request Guidance

- Keep changes scoped to the requested behavior.
- Update `README.md` and this file when commands, packaging behavior, or project
  structure changes.
- When changes affect releases or packaged app behavior, bump the version in
  `build.gradle.kts`, sync shell packaging fallback versions, and update
  `CHANGELOG.md`.
- Run the relevant build/run command before handing off work, and note any
  command that could not be run.
