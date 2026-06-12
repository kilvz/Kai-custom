@echo off
cd /d "%~dp0.."
set "FFMPEG_DIR=C:\Users\zethk\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.1-full_build\bin"
set "PATH=%FFMPEG_DIR%;%PATH%"
set "FFMPEG_BINARY=%FFMPEG_DIR%\ffmpeg.exe"
echo ffmpeg: %FFMPEG_DIR%\ffmpeg.exe
py -3.11 tools\train_hey_kai.py --kai-count 300 --epochs 20 --output hey_kai.tflite --sc-dir tools\speech_commands
if exist hey_kai.tflite (
    echo Copying model to androidApp assets...
    copy /Y hey_kai.tflite androidApp\src\main\assets\hey_kai.tflite
    echo Done! Model deployed to androidApp/src/main/assets/hey_kai.tflite
) else (
    echo WARNING: hey_kai.tflite not found at repo root
)
echo.
pause
