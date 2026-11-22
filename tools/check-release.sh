#!/bin/sh
# Checks a built release APK before it goes out: the size and dex budget (tools/release-budget.txt), a versionCode above the one of the previous
# release tag, a version name that matches the tag being built, and writes SHA256SUMS next to the APK.
# Run from the root of the project, after assembleRelease:  tools/check-release.sh [apk]
# Only the version checks (no APK needed, what a pull request runs):  tools/check-release.sh --version-only
set -eu
here=$(cd "$(dirname "$0")" && pwd)
only_version=0
apk=""
if [ "${1:-}" = "--version-only" ]; then only_version=1; else apk=${1:-$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)}; fi

build=app/build.gradle.kts
code=$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$build" | head -1)
name=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$build" | head -1)
[ -n "$code" ] && [ -n "$name" ] || { echo "FAIL: versionCode/versionName not found in $build" >&2; exit 1; }
fail=0

# The previous release is the nearest earlier tag "v*" (on a tag build, the one before the tag itself).
prev=$(git describe --tags --abbrev=0 --match 'v*' "$(git rev-parse HEAD)^" 2>/dev/null || true)
if [ -n "$prev" ]; then
  before=$(git show "$prev:$build" 2>/dev/null | sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' | head -1)
  if [ -n "$before" ] && [ "$code" -le "$before" ]; then
    echo "FAIL: versionCode $code is not above $before of $prev" >&2; fail=1
  else
    echo "ok: versionCode $code is above ${before:-?} of $prev"
  fi
else
  echo "ok: no earlier tag to compare the versionCode with"
fi

# On a tag build the tag must be v + the version name.
tag=${GITHUB_REF_NAME:-}
case "$tag" in
  v*) if [ "$tag" != "v$name" ]; then echo "FAIL: tag $tag does not match versionName $name" >&2; fail=1; else echo "ok: tag $tag matches versionName"; fi ;;
esac

if [ "$only_version" = 0 ]; then
  [ -f "$apk" ] || { echo "FAIL: no APK found (run assembleRelease first)" >&2; exit 1; }
  limit() { sed -n "s/^$1=\([0-9][0-9]*\).*/\1/p" "$here/release-budget.txt"; }
  size=$(wc -c < "$apk" | tr -d ' ')
  if [ "$size" -gt "$(limit apk_bytes)" ]; then echo "FAIL: APK is $size bytes, the budget is $(limit apk_bytes)" >&2; fail=1; else echo "ok: APK $size bytes (budget $(limit apk_bytes))"; fi
  dex=$(unzip -l "$apk" | awk '/ classes[0-9]*\.dex$/ { n++; b += $1 } END { print n + 0, b + 0 }')
  files=${dex% *}; bytes=${dex#* }
  if [ "$files" -gt "$(limit dex_files)" ]; then echo "FAIL: $files dex files, the budget is $(limit dex_files)" >&2; fail=1; else echo "ok: $files dex file(s)"; fi
  if [ "$bytes" -gt "$(limit dex_bytes)" ]; then echo "FAIL: dex is $bytes bytes, the budget is $(limit dex_bytes)" >&2; fail=1; else echo "ok: dex $bytes bytes (budget $(limit dex_bytes))"; fi
  dir=$(dirname "$apk")
  if command -v sha256sum >/dev/null 2>&1; then (cd "$dir" && sha256sum "$(basename "$apk")" > SHA256SUMS); else (cd "$dir" && shasum -a 256 "$(basename "$apk")" > SHA256SUMS); fi
  echo "ok: $dir/SHA256SUMS written"
fi

[ "$fail" = 0 ] || exit 1
echo "Release checks passed."
