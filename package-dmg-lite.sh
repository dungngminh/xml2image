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

if ! command -v hdiutil >/dev/null 2>&1; then
  printf '%s\n' "Missing hdiutil. Lite DMG packaging is only available on macOS." >&2
  exit 1
fi

app_name="XML Resource Converter"
bundle_id="com.komkat.xml2image"
build_dir="build/lite-dmg"
app_dir="$build_dir/$app_name.app"
contents_dir="$app_dir/Contents"
macos_dir="$contents_dir/MacOS"
resources_dir="$contents_dir/Resources"
output_dir="build/jpackage/output"
jar_name="xml2image.jar"
dmg_path="$output_dir/$app_name-lite-1.0.0.dmg"

rm -rf "$build_dir"
mkdir -p "$macos_dir" "$resources_dir" "$output_dir"

if [ -x ./gradlew ]; then
  ./gradlew jar
  set -- build/libs/xml2image-*.jar
  cp "$1" "$resources_dir/$jar_name"
elif command -v gradle >/dev/null 2>&1; then
  gradle jar
  set -- build/libs/xml2image-*.jar
  cp "$1" "$resources_dir/$jar_name"
elif kotlinc_bin="$(find_kotlinc)"; then
  "$kotlinc_bin" src/main/kotlin/com/komkat/xml2image/XmlResourceConverterGui.kt -include-runtime -d "$resources_dir/$jar_name"
else
  printf '%s\n' "Missing Kotlin build tool. Install Gradle or Kotlin compiler, then run ./package-dmg-lite.sh again." >&2
  exit 1
fi

cp src/main/resources/macos/AppIcon.icns "$resources_dir/AppIcon.icns"

cat > "$contents_dir/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>en</string>
  <key>CFBundleExecutable</key>
  <string>launcher</string>
  <key>CFBundleIconFile</key>
  <string>AppIcon</string>
  <key>CFBundleIdentifier</key>
  <string>$bundle_id</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>$app_name</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  <key>LSMinimumSystemVersion</key>
  <string>11.0</string>
  <key>NSHighResolutionCapable</key>
  <true/>
</dict>
</plist>
PLIST

cat > "$macos_dir/launcher" <<'LAUNCHER'
#!/usr/bin/env sh
set -eu

contents_dir="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
jar_path="$contents_dir/Resources/xml2image.jar"
icon_path="$contents_dir/Resources/AppIcon.icns"

if ! command -v java >/dev/null 2>&1; then
  osascript -e 'display dialog "Java 21 or newer is required to run XML Resource Converter." buttons {"OK"} default button "OK" with icon stop' >/dev/null 2>&1 || true
  exit 1
fi

exec java \
  -Xdock:name="XML Resource Converter" \
  -Xdock:icon="$icon_path" \
  -jar "$jar_path"
LAUNCHER

chmod +x "$macos_dir/launcher"
xattr -cr "$app_dir" 2>/dev/null || true

hdiutil create \
  -volname "$app_name Lite" \
  -srcfolder "$app_dir" \
  -ov \
  -format UDZO \
  "$dmg_path"

printf '%s\n' "$dmg_path"
