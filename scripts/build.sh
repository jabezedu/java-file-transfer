#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
mkdir -p out
echo "Compiling java-file-transfer..."
find src -name "*.java" > sources.txt
javac -d out @sources.txt
rm sources.txt
echo "Build successful. Classes compiled to ./out"
