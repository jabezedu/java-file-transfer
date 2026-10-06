@echo off
setlocal
cd /d "%~dp0\.."
if not exist "out\com\netapps\filetransfer\server\FileServer.class" (
    echo Binaries not found. Running build first...
    call scripts\build.bat
)
echo Starting FileServer...
java -cp out com.netapps.filetransfer.server.FileServer %*
