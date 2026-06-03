#!/bin/bash
# Deploy WhatsApp Baileys MCP bridge into the proot sandbox
# Run from host: wsl -d Ubuntu bash deploy.sh
# Or inside sandbox: bash /path/to/deploy.sh

set -e

BRIDGE_SRC="$(cd "$(dirname "$0")" && pwd)"
DEST_DIR="/root/whatsapp-bridge"

echo "=== Deploying WhatsApp Bridge ==="

# Create destination
mkdir -p "$DEST_DIR"

# Copy bridge files
cp "$BRIDGE_SRC/bridge.js" "$DEST_DIR/"
cp "$BRIDGE_SRC/package.json" "$DEST_DIR/"

cd "$DEST_DIR"

# Install dependencies
echo "Installing npm dependencies..."
if [ ! -d "node_modules" ]; then
    npm install --production 2>&1
fi

echo "=== WhatsApp Bridge deployed ==="
echo "Start with: node bridge.js"
echo "The bridge will listen on stdio (MCP stdio protocol)."
echo "QR code will appear when first connecting."
