# build-gguf.ps1 — Cross-compile llama.cpp + JNI wrapper for Android
#
# Usage: .\build-gguf.ps1
#
# Requirements:
#   - WSL (Ubuntu) with: git, cmake, make, g++
#   - Android NDK (set $env:ANDROID_NDK_HOME or detected from local.properties)
#
# Output: androidApp/src/main/jniLibs/arm64-v8a/libgguf_engine.so

param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ScriptDir ".build-gguf"
$OutputDir = Join-Path $ScriptDir "androidApp\src\main\jniLibs\arm64-v8a"
$GgufDir = Join-Path $ScriptDir "gguf"

# Detect NDK
if (-not $env:ANDROID_NDK_HOME) {
    $propFile = Join-Path $ScriptDir "local.properties"
    if (Test-Path $propFile) {
        $ndkLine = Select-String -Path $propFile -Pattern "^sdk\.dir" | Select-Object -First 1
        if ($ndkLine) {
            $sdkDir = $ndkLine.ToString().Split("=", 2)[1].Trim()
            $env:ANDROID_NDK_HOME = "$sdkDir\ndk\29.0.14206865"
        }
    }
}
if (-not $env:ANDROID_NDK_HOME) {
    Write-Error "ANDROID_NDK_HOME not set. Set it or add sdk.dir to local.properties"
    exit 1
}
Write-Host "NDK: $env:ANDROID_NDK_HOME"

if ($Clean -and (Test-Path $BuildDir)) {
    Remove-Item -Recurse -Force $BuildDir
}

New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$wslScript = @'
#!/usr/bin/env bash
set -euo pipefail

BUILD_DIR="$1"
NDK_HOME="$2"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# The C++ sources are in the composeApp cpp directory
CPP_SRC_DIR="${SCRIPT_DIR}/composeApp/src/androidMain/cpp"

mkdir -p "${BUILD_DIR}/llama" "${BUILD_DIR}/build"

# Clone llama.cpp
if [ ! -d "${BUILD_DIR}/llama/.git" ]; then
    echo "Cloning llama.cpp..."
    git clone --depth=1 https://github.com/ggerganov/llama.cpp.git "${BUILD_DIR}/llama"
fi

# Copy our JNI wrapper into llama.cpp source tree
cp "${CPP_SRC_DIR}/gguf_context.h" "${BUILD_DIR}/llama/"
cp "${CPP_SRC_DIR}/gguf_context.cc" "${BUILD_DIR}/llama/"
cp "${CPP_SRC_DIR}/gguf_jni.cpp" "${BUILD_DIR}/llama/"

cd "${BUILD_DIR}/build"

TOOLCHAIN="${NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
API=26
ARCH="aarch64-linux-android"
TARGET="${ARCH}${API}"

cmake "${BUILD_DIR}/llama" \
    -DCMAKE_TOOLCHAIN_FILE="${NDK_HOME}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-${API} \
    -DCMAKE_C_COMPILER="${TOOLCHAIN}/bin/${TARGET}-clang" \
    -DCMAKE_CXX_COMPILER="${TOOLCHAIN}/bin/${TARGET}-clang++" \
    -DLLAMA_CUDA=OFF \
    -DLLAMA_VULKAN=OFF \
    -DLLAMA_METAL=OFF \
    -DLLAMA_BUILD_TESTS=OFF \
    -DLLAMA_BUILD_EXAMPLES=OFF \
    -DLLAMA_BUILD_SERVER=OFF \
    -DBUILD_SHARED_LIBS=ON \
    -DCMAKE_BUILD_TYPE=Release

make -j$(nproc) gguf_engine

# Copy output
cp "${BUILD_DIR}/build/libgguf_engine.so" "${SCRIPT_DIR}/androidApp/src/main/jniLibs/arm64-v8a/"
cp "${BUILD_DIR}/build/libgguf_engine.so" "${SCRIPT_DIR}/gguf/"

echo "Done! Output: androidApp/src/main/jniLibs/arm64-v8a/libgguf_engine.so"
'@

$wslScriptPath = Join-Path $BuildDir "_build.sh"
Set-Content -Path $wslScriptPath -Value $wslScript -Encoding ASCII

Write-Host "Running build in WSL..."
wsl -d Ubuntu bash "$(wslpath -a $wslScriptPath)" "$(wslpath -a $BuildDir)" "$(wslpath -a $env:ANDROID_NDK_HOME)"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed"
    exit 1
}

Write-Host "Build successful! libgguf_engine.so is at: $OutputDir"
