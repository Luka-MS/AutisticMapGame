#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d out/classes ]; then
    mkdir -p out/classes
    javac -encoding UTF-8 -d out/classes $(find src -name '*.java')
    cp -R res/. out/classes/
fi

opts=()
if [ "$(uname)" = "Darwin" ]; then
    opts+=("-Xdock:name=Map Game")
fi

exec java "${opts[@]}" -cp out/classes mapgame.Main
