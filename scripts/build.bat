@echo off
setlocal
cd /d "%~dp0\.."
if not exist "out" mkdir "out"
echo Compiling java-file-transfer...
javac -d out -sourcepath src src\com\netapps\filetransfer\common\*.java src\com\netapps\filetransfer\util\*.java src\com\netapps\filetransfer\server\*.java src\com\netapps\filetransfer\client\*.java src\com\netapps\filetransfer\ui\*.java
if %ERRORLEVEL% equ 0 (
    echo Build successful. Classes compiled to ./out
) else (
    echo Build failed with error code %ERRORLEVEL%
)
