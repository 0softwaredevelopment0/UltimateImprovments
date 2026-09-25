#!/usr/bin/env bash
# Packages the UltimateImprovments plugin family into a single distribution archive.
#
# Pipeline:
#   1. ./gradlew build           (skipped with --no-build)
#   2. gzip every UI-*.jar       (originals in build/libs stay untouched)
#   3. tar the *.jar.gz files    -> build/distribution/UltimateImprovments-<version>-jars.tar
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
PAYLOAD="$STAGE/gz"
rm -rf "$STAGE"
mkdir -p "$PAYLOAD"

echo "[package] Gzipping JARs (version $VERSION)..."
for jar in build/libs/UI-*-all.jar; do
  name="$(basename "$jar")"
  gzip -9 -c "$jar" > "$PAYLOAD/$name.gz"
done

TAR="$STAGE/UltimateImprovments-$VERSION-jars.tar"
echo "[package] Creating $TAR ..."
tar -cf "$TAR" -C "$PAYLOAD" .

# Show the result
echo "[package] Done:"
ls -lh "$TAR"
echo
sha256sum "$TAR"
echo
echo "[package] Archive contents:"
tar -tvf "$TAR"
