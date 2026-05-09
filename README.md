# XML Resource to Image Converter

Kotlin + Swing desktop GUI for batch converting Android VectorDrawable XML files to PNG, JPG, or WebP.

## Requirements

- JDK 21 or newer
- Gradle, Kotlin compiler, or Android Studio's bundled Kotlin compiler
- Optional: `cwebp` for WebP export when Java has no WebP ImageIO writer

## Run

```sh
./run.sh
```

If you already have Gradle installed, you can also run:

```sh
gradle run
```

The app lets you add individual XML files or a folder, drag and drop files/folders, choose an output folder, set the format, and convert the batch.

## Export DMG

On macOS with JDK 21+:

```sh
./package-dmg.sh
```

The script uses Gradle when available. Otherwise it falls back to `kotlinc`, including Android Studio's bundled Kotlin compiler. Packaging uses a stripped `jlink` runtime with only `java.base` and `java.desktop` to keep the DMG smaller.

For a much smaller installer that does not bundle Java:

```sh
./package-dmg-lite.sh
```

The lite DMG requires Java 21+ to be installed on the target Mac.

The DMG is written to:

```text
build/jpackage/output/
```

The packaged macOS app uses `src/main/resources/macos/AppIcon.icns`, generated from `assets/logo.png`.

## Export Lightweight Windows/Linux Archives

These archives do not bundle Java. Java 21+ must be installed on the target machine.

```sh
gradle packageLite
```

Outputs are written to:

```text
build/distributions/
```

Use `bin/xml2image` on Linux/macOS or `bin\xml2image.bat` on Windows.

## Notes

- Input must be Android `<vector>` drawable XML.
- PNG keeps transparency when `Keep alpha` is enabled.
- JPG is written on a white background.
- WebP uses Java ImageIO if available, otherwise it calls `cwebp` from `PATH`.
