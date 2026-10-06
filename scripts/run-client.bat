@echo off
setlocal
cd /d "%~dp0\.."
if not exist "out\com\netapps\filetransfer\ui\MainWindow.class" (
    echo Binaries not found. Running build first...
    call scripts\build.bat
)
echo Launching File Transfer Client GUI...
java -cp out com.netapps.filetransfer.ui.MainWindow %*
