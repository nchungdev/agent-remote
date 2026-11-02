#!/usr/bin/env bash
set -e

# Fast multi-platform cross-compilation for Agent Remote Server (Go Native)
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
mkdir -p "$DIST_DIR"

LDFLAGS="-s -w"

echo "=== Building Go Server for All Platforms ==="

echo "1. Building for macOS (ARM64 - Apple Silicon)..."
GOOS=darwin GOARCH=arm64 go build -ldflags="$LDFLAGS" -o "$DIST_DIR/agent-remote-server-macos-arm64" ./cmd/server

echo "2. Building for macOS (x86_64 - Intel)..."
GOOS=darwin GOARCH=amd64 go build -ldflags="$LDFLAGS" -o "$DIST_DIR/agent-remote-server-macos-x64" ./cmd/server

echo "3. Building for Linux (x86_64)..."
GOOS=linux GOARCH=amd64 go build -ldflags="$LDFLAGS" -o "$DIST_DIR/agent-remote-server-linux-x64" ./cmd/server

echo "4. Building for Windows (x86_64)..."
GOOS=windows GOARCH=amd64 go build -ldflags="$LDFLAGS" -o "$DIST_DIR/agent-remote-server-windows-x64.exe" ./cmd/server

# Also create the default local binary
cp "$DIST_DIR/agent-remote-server-macos-arm64" "$DIST_DIR/agent-remote-server"

echo ""
echo "=== BUILD COMPLETE in $DIST_DIR ==="
ls -lh "$DIST_DIR"/agent-remote-server*
