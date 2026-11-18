#!/bin/sh
# Builds the release APK twice from scratch and compares what is inside: the same files, byte for byte, except the signature block.
# What makes a build reproducible, and the result of the first proof: docs/progetti/build-riproducibile.md.
# Needs JAVA_HOME on a JDK 21. Run from the root of the project: tools/check-reproducible.sh
set -eu
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
for n in a b; do
  ./gradlew --no-build-cache --rerun-tasks :app:assembleRelease -q
  apk=$(ls app/build/outputs/apk/release/*.apk | head -1)
  mkdir "$work/$n" && unzip -q "$apk" -d "$work/$n"
done
# The signature files of the zip are the only ones that may differ (F-Droid signs with its own key).
if diff -r -x 'META-INF' "$work/a" "$work/b" && diff -r "$work/a/META-INF" "$work/b/META-INF" -x '*.RSA' -x '*.SF' -x '*.MF' >/dev/null; then
  echo "Reproducible: two builds hold the same files."
else
  echo "NOT reproducible: the builds differ (see above)." >&2
  exit 1
fi
