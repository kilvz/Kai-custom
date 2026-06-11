# build-gguf.ps1 — Cross-compile llama.cpp + JNI wrapper for Android
# Run from PowerShell. Requires WSL Ubuntu + git + cmake + make + g++.

param([switch]$Clean)

$ErrorActionPreference = "Stop"
$ScriptDir = (Get-Item ".").FullName

# Resolve NDK
if (-not $env:ANDROID_NDK_HOME) {
    $ndkBase = "$env:USERPROFILE\AppData\Local\Android\Sdk\ndk"
    $latest = Get-ChildItem $ndkBase -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if ($latest) { $env:ANDROID_NDK_HOME = $latest.FullName }
}
if (-not $env:ANDROID_NDK_HOME) { Write-Error "ANDROID_NDK_HOME not set"; exit 1 }
Write-Host "NDK: $env:ANDROID_NDK_HOME"

$BuildDir = Join-Path $ScriptDir ".build-gguf"
if ($Clean -and (Test-Path $BuildDir)) { Remove-Item -Recurse -Force $BuildDir }
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

# Write bash script
$bashScript = @'
#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$1"
NDK_DIR="$2"
BUILD_DIR="${PROJECT_DIR}/.build-gguf"
CPP_DIR="${PROJECT_DIR}/composeApp/src/androidMain/cpp"
OUTPUT_DIR="${PROJECT_DIR}/androidApp/src/main/jniLibs/arm64-v8a"
GGUF_DIR="${PROJECT_DIR}/gguf"
mkdir -p "${BUILD_DIR}/llama" "${BUILD_DIR}/build" "${OUTPUT_DIR}" "${GGUF_DIR}"
echo "=== Cloning llama.cpp ==="
git clone --depth=1 https://github.com/ggerganov/llama.cpp.git "${BUILD_DIR}/llama"
echo "=== Copying JNI sources ==="
cp "${CPP_DIR}/gguf_context.h" "${CPP_DIR}/gguf_context.cc" "${CPP_DIR}/gguf_jni.cpp" "${CPP_DIR}/CMakeLists.txt" "${BUILD_DIR}/llama/"
cd "${BUILD_DIR}/build"
TOOLCHAIN="${NDK_DIR}/toolchains/llvm/prebuilt/linux-x86_64"
API=26
TARGET="aarch64-linux-android${API}"
echo "=== Configuring CMake ==="
cmake "${BUILD_DIR}/llama" \
    -DCMAKE_TOOLCHAIN_FILE="${NDK_DIR}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-${API} \
    -DCMAKE_C_COMPILER="${TOOLCHAIN}/bin/${TARGET}-clang" \
    -DCMAKE_CXX_COMPILER="${TOOLCHAIN}/bin/${TARGET}-clang++" \
    -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF \
    -DGGML_OPENMP=OFF -DLLAMA_OPENMP=OFF \
    -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_TYPE=Release
echo "=== Building ==="
make -j$(nproc) gguf_engine
cp "${BUILD_DIR}/build/bin/"*.so "${OUTPUT_DIR}/"
cp "${BUILD_DIR}/build/libgguf_engine.so" "${OUTPUT_DIR}/"
cp "${BUILD_DIR}/build/libgguf_engine.so" "${GGUF_DIR}/"
echo "=== DONE ==="
ls -lh "${OUTPUT_DIR}/libgguf_engine.so"
'@

$bashPath = Join-Path $BuildDir "build.sh"
[IO.File]::WriteAllText($bashPath, $bashScript.Replace("`r`n", "`n"))

# Convert paths to WSL format: C:\path -> /mnt/c/path
function ConvertTo-WslPath {
    param([string]$WinPath)
    $drive = $WinPath[0].ToString().ToLower()
    return "/mnt/$drive" + $WinPath.Substring(2).Replace("\", "/")
}

$wslProject = ConvertTo-WslPath $ScriptDir
$wslNdk = "/opt/android-ndk-r29"

Write-Host "Running WSL build..."
Write-Host "  Project: $wslProject"
Write-Host "  NDK (Linux): $wslNdk"

wsl -d Ubuntu -- bash -c "bash '${wslProject}/.build-gguf/build.sh' '${wslProject}' '${wslNdk}'" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed"
    exit 1
}

Write-Host "Build successful! libgguf_engine.so in gguf/"
