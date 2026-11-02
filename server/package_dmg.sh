#!/usr/bin/env bash
set -e

# Package Agent Remote Server (Go Native) into a macOS .app and .dmg installer
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
APP_NAME="Agent Remote Server"
BUNDLE_DIR="$DIST_DIR/$APP_NAME.app"
DMG_STAGE="$DIST_DIR/dmg_stage"
DMG_OUTPUT="$DIST_DIR/AgentRemoteServer.dmg"

echo "=== 1. Building Standalone Server Binary (Go Native) ==="
cd "$ROOT_DIR/server"
go build -ldflags="-s -w" -o "$DIST_DIR/agent-remote-server" ./cmd/server

echo "=== 2. Creating macOS App Bundle ==="
rm -rf "$BUNDLE_DIR" "$DMG_STAGE" "$DMG_OUTPUT"
mkdir -p "$BUNDLE_DIR/Contents/MacOS"
mkdir -p "$BUNDLE_DIR/Contents/Resources"

# Copy binary
cp "$DIST_DIR/agent-remote-server" "$BUNDLE_DIR/Contents/MacOS/agent-remote-server"
chmod +x "$BUNDLE_DIR/Contents/MacOS/agent-remote-server"

# Copy Icon if available
if [ -f "$ROOT_DIR/app/build/compose/default-resources/1.9.0/default-icon-mac.icns" ]; then
    cp "$ROOT_DIR/app/build/compose/default-resources/1.9.0/default-icon-mac.icns" "$BUNDLE_DIR/Contents/Resources/app.icns"
fi

# Create launcher script
cat << 'EOF' > "$BUNDLE_DIR/Contents/MacOS/launcher"
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
BIN="$DIR/agent-remote-server"

if [ -t 0 ]; then
    exec "$BIN" "$@"
else
    osascript <<APPLESCRIPT
tell application "Terminal"
    activate
    do script "\"$BIN\""
end tell
APPLESCRIPT
fi
EOF
chmod +x "$BUNDLE_DIR/Contents/MacOS/launcher"

# Create Info.plist
cat << EOF > "$BUNDLE_DIR/Contents/Info.plist"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>agent-remote-server</string>
    <key>CFBundleIdentifier</key>
    <string>app.duinch.agentremote.server</string>
    <key>CFBundleName</key>
    <string>Agent Remote Server</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>0.2.0</string>
    <key>CFBundleIconFile</key>
    <string>app.icns</string>
    <key>CFBundleVersion</key>
    <string>2</string>
    <key>LSMinimumSystemVersion</key>
    <string>12.0</string>
    <key>LSUIElement</key>
    <true/>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

echo "=== 3. Creating DMG Staging Directory ==="
mkdir -p "$DMG_STAGE"
cp -R "$BUNDLE_DIR" "$DMG_STAGE/"
ln -s /Applications "$DMG_STAGE/Applications"

echo "=== 4. Generating DMG with hdiutil ==="
hdiutil create \
    -volname "$APP_NAME" \
    -srcfolder "$DMG_STAGE" \
    -ov \
    -format UDZO \
    "$DMG_OUTPUT"

rm -rf "$DMG_STAGE"

echo ""
echo "=== BUILD SUCCESSFUL ==="
echo "DMG Installer created at: $DMG_OUTPUT"
ls -lh "$DMG_OUTPUT"
