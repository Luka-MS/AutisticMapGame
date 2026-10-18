#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "Usage: $0 <version> [vendor]" >&2
    exit 2
fi

version=$1
vendor=${2:-Luka-MS}
app_name="AutisticMapGame-Version-$version"

echo "==> Building classes"
rm -rf out/classes
mkdir -p out/classes
javac -encoding UTF-8 -d out/classes $(find src -name '*.java')
cp -R res/. out/classes/

echo "==> Packaging runnable jar"
jar_dir=out/jar
rm -rf "$jar_dir"
mkdir -p "$jar_dir"
jar --create --file "$jar_dir/app.jar" --main-class mapgame.Main -C out/classes .
xattr -cr "$jar_dir"

echo "==> Creating macOS app image"
dist_dir=out/dist
rm -rf "$dist_dir"
mkdir -p "$dist_dir"
xattr -cr "$dist_dir"

package_dir=$(mktemp -d "${TMPDIR:-/tmp}/autistic-map-game-package.XXXXXX")
trap 'rm -rf "$package_dir"' EXIT
mkdir -p "$package_dir/input"
cp "$jar_dir/app.jar" "$package_dir/input/app.jar"
xattr -cr "$package_dir"

jpackage \
    --type app-image \
    --input "$package_dir/input" \
    --name "$app_name" \
    --main-jar app.jar \
    --main-class mapgame.Main \
    --dest "$package_dir" \
    --app-version "$version" \
    --vendor "$vendor" \
    --mac-package-identifier "com.lukams.autisticmapgame"

package_app_path="$package_dir/$app_name.app"
codesign --verify --deep --strict "$package_app_path"

app_path="$dist_dir/$app_name.app"
ditto "$package_app_path" "$app_path"

echo "==> Zipping release asset"
zip_path="$dist_dir/$app_name-mac.zip"
ditto -c -k --sequesterRsrc --keepParent "$app_path" "$zip_path"

echo "Release zip -> $zip_path"
