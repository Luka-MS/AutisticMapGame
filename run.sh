#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f out/classes/mapgame/Main.class ] || [ -n "$(find src res -newer out/classes/mapgame/Main.class -type f | head -1)" ]; then
    rm -rf out/classes
    mkdir -p out/classes
    javac -encoding UTF-8 -d out/classes $(find src -name '*.java')
    cp -R res/. out/classes/
fi

opts=()
if [ "$(uname)" = "Darwin" ]; then
    opts+=("-Xdock:name=Autistic Map Game")
fi

exec java "${opts[@]}" -cp out/classes mapgame.Main
