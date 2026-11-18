#!/usr/bin/env bash
# Assemble the Infuse Ultimate release: all module jars + InfuseCore + a single merged
# resource pack + install README, zipped into download/Infuse-Ultimate-<ver>.zip.
#
# Run from anywhere; paths are resolved from the repo root.
set -euo pipefail

VER="1.0.0"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAGE="$(mktemp -d)"
OUT="$ROOT/download"
PACK_NAME="Infuse-Ultimate-Pack-${VER}.zip"
BUNDLE_NAME="Infuse-Ultimate-${VER}.zip"

echo ">> Building all module jars..."
./gradlew suite -x test -q

echo ">> Collecting jars..."
mkdir -p "$STAGE/plugins"
cp modules/customs/build/libs/Customs-*.jar      "$STAGE/plugins/"
cp modules/bloodbound/build/libs/bloodbound-*.jar "$STAGE/plugins/Bloodbound-1.0.0.jar"
cp modules/infuse-smp/build/libs/infuse-smp-*.jar "$STAGE/plugins/Infuse-1.0.0.jar"
cp infusecore/build/libs/infusecore-*.jar         "$STAGE/plugins/InfuseCore-1.0.0.jar"
# Limitless Paper ships prebuilt (datapack-driven; not a Gradle module).
cp vendor/Limitless-Paper-1.21.11.jar             "$STAGE/plugins/"

echo ">> Merging resource packs..."
PACKDIR="$STAGE/packbuild"
mkdir -p "$PACKDIR"
unzip -oq modules/customs/build/dist/Customs-ResourcePack.zip -d "$PACKDIR"
unzip -oq modules/infuse-smp/build/InfusePack.zip             -d "$PACKDIR"
# One unified manifest (format 75 = MC 1.21.11, the version both packs target).
cat > "$PACKDIR/pack.mcmeta" <<'MCMETA'
{
  "pack": {
    "description": "§dInfuse Ultimate§r §7— weapons · effects · relics\n§8Customs models: Blades of Majestica · HUD: TurboJax",
    "min_format": [75, 0],
    "max_format": [99, 0]
  }
}
MCMETA
( cd "$PACKDIR" && zip -rq "$STAGE/$PACK_NAME" . )

echo ">> Writing install README..."
cp docs/INSTALL.md "$STAGE/README.txt"

echo ">> Zipping bundle..."
mkdir -p "$OUT"
rm -f "$OUT/$BUNDLE_NAME"
( cd "$STAGE" && zip -rq "$OUT/$BUNDLE_NAME" plugins "$PACK_NAME" README.txt )
cp "$STAGE/$PACK_NAME" "$OUT/$PACK_NAME"

SHA=$(sha1sum "$OUT/$PACK_NAME" | awk '{print $1}')
echo ">> Done."
echo "   Bundle: download/$BUNDLE_NAME"
echo "   Pack:   download/$PACK_NAME"
echo "   Pack SHA-1 (paste into InfuseCore config.yml resource-pack.sha1): $SHA"
rm -rf "$STAGE"
