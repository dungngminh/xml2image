# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1] - 2026-05-10

### Added

- Add a bundled WebP ImageIO writer to Gradle-built packages.
- Add focused JVM test configuration.

### Fixed

- Fix WebP batch export failures when `cwebp` is not installed.

### Changed

- Prefer lite packaging in contributor instructions.
- Document WebP behavior for Gradle-built packages and direct `kotlinc` fallback builds.

## [0.1.0] - 2026-05-10

### Added

- Initial Kotlin + Swing VectorDrawable batch converter.
- PNG, JPG, and WebP export support.
- macOS, Windows, and Linux lightweight packaging tasks.

[Unreleased]: https://github.com/dungngminh/xml2image/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/dungngminh/xml2image/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/dungngminh/xml2image/releases/tag/v0.1.0
