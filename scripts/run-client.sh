#!/usr/bin/env bash
cd "$(dirname "$0")/.."
if [ ! -f "out/com/netapps/filetransfer/ui/MainWindow.class" ]; then
    echo "Binaries not found. Running build first..."
    bash scripts/build.sh
fi
echo "Launching File Transfer Client GUI..."
java -cp out com.netapps.filetransfer.ui.MainWindow "$@"
