#!/usr/bin/env bash
cd "$(dirname "$0")/.."
if [ ! -f "out/com/netapps/filetransfer/server/FileServer.class" ]; then
    echo "Binaries not found. Running build first..."
    bash scripts/build.sh
fi
echo "Starting FileServer..."
java -cp out com.netapps.filetransfer.server.FileServer "$@"
