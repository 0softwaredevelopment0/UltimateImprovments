#!/usr/bin/env bash
# Packages the UltimateImprovments plugin family into a single distribution archive.
#
# Pipeline:
#   1. ./gradlew build           (skipped with --no-build)
#   2. tar the UI-*.jar files    -> build/distribution/UltimateImprovments-<version>-jars.tar
#
# Note: NO gzip stage. JARs are already ZIP archives (DEFLATE-compressed inside),
# so gzipping them shrinks the archive by only ~2-5% while taking minutes of CPU.
# A plain tar of the .jar files is the right trade-off.
#
# Usage (from anywhere):
#   Scripts/build/package_jars.sh [--no-build]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [[ "${1:-}" == "--no-build" ]]; then
  echo "[package] Skipping build (--no-build)"
else
  echo "[package] Building..."
  ./gradlew build -q
fi

if ! ls build/libs/UI-*-all.jar >/dev/null 2>&1; then
  echo "[package] ERROR: no UI-*-all.jar in build/libs/" >&2
  exit 1
fi

# Detect the version from the core jar name (UI-Core-<version>-all.jar)
CORE_JAR="$(ls build/libs/UI-Core-*-all.jar | head -n1)"
VERSION="$(sed -E 's/.*UI-Core-(.*)-all\.jar/\1/' "$CORE_JAR")"

STAGE="build/distribution"
rm -rf "$STAGE"
mkdir -p "$STAGE"

TAR="$STAGE/UltimateImprovments-$VERSION-jars.tar"
echo "[package] Creating $TAR (version $VERSION, no gzip — JARs are already compressed)..."
tar -cf "$TAR" -C build/libs UI-*-all.jar

# Show the result
echo "[package] Done:"
ls -lh "$TAR"
echo
sha256sum "$TAR"
echo
echo "[package] Archive contents:"
tar -tvf "$TAR"
