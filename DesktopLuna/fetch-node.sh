#!/bin/sh
# Fetches the Node that runs webOS JS services in Luna DE into DesktopLuna/node/: Node 12, the
# major version nodejs-mobile gives Lunacy on Android (12.19), so a service behaves the same on
# both (Docs/luna-de.md). Official builds exist for Linux and macOS on x64 (an Apple Silicon Mac
# runs the x64 one under Rosetta) and Windows on x64; elsewhere set LUNA_DE_NODE to a Node 12.
#
#   DesktopLuna/fetch-node.sh
#
# The download is checked against nodejs.org's own SHASUMS256.txt.
set -eu
cd "$(dirname "$0")"
V=v12.22.12
case "$(uname -s)" in
  Linux) os=linux ;;
  Darwin) os=darwin ;;
  *) echo "fetch-node: no Node 12 build fetched for $(uname -s); set LUNA_DE_NODE to a Node 12" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64|amd64) arch=x64 ;;
  arm64|aarch64) if [ "$os" = darwin ]; then arch=x64; else arch=arm64; fi ;;
  *) echo "fetch-node: no Node 12 build for $(uname -m); set LUNA_DE_NODE to a Node 12" >&2; exit 1 ;;
esac
name=node-$V-$os-$arch
if [ -x node/bin/node ] && [ "$(node/bin/node --version 2>/dev/null)" = "$V" ]; then echo "fetch-node: node/ already holds Node $V"; exit 0; fi
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
echo "fetch-node: https://nodejs.org/dist/$V/$name.tar.gz"
curl -fsSL -o "$tmp/$name.tar.gz" "https://nodejs.org/dist/$V/$name.tar.gz"
curl -fsSL -o "$tmp/SHASUMS256.txt" "https://nodejs.org/dist/$V/SHASUMS256.txt"
want=$(grep " $name.tar.gz\$" "$tmp/SHASUMS256.txt" | cut -d' ' -f1)
if command -v sha256sum >/dev/null 2>&1; then have=$(sha256sum "$tmp/$name.tar.gz" | cut -d' ' -f1); else have=$(shasum -a 256 "$tmp/$name.tar.gz" | cut -d' ' -f1); fi
[ -n "$want" ] && [ "$want" = "$have" ] || { echo "fetch-node: the download doesn't match nodejs.org's checksum" >&2; exit 1; }
tar -xzf "$tmp/$name.tar.gz" -C "$tmp"
rm -rf node
mkdir -p node/bin
cp "$tmp/$name/bin/node" node/bin/node
cp "$tmp/$name/LICENSE" node/LICENSE
echo "fetch-node: node/bin/node is $(node/bin/node --version)"
