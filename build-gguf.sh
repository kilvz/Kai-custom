#!/usr/bin/env bash
# build-gguf.sh — Cross-compile llama.cpp + JNI wrapper for Android
# Run this in WSL (Ubuntu): bash build-gguf.sh
#
# Prerequisites:
#   export ANDROID_NDK_HOME=/mnt/c/Users/zethk/AppData/Local/Android/Sdk/ndk/29.0.14206865

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/.build-gguf"
CPP_DIR="${SCRIPT_DIR}/composeApp/src/androidMain/cpp"
OUTPUT_DIR="${SCRIPT_DIR}/androidApp/src/main/jniLibs/arm64-v8a"
GGUF_DIR="${SCRIPT_DIR}/gguf"
NDK_DIR="${ANDROID_NDK_HOME}"

echo "=== GGUF Build ==="
echo "Project: ${SCRIPT_DIR}"
echo "NDK: ${NDK_DIR}"

mkdir -p "${BUILD_DIR}/llama" "${BUILD_DIR}/build" "${OUTPUT_DIR}" "${GGUF_DIR}"

# Clone llama.cpp if needed
if [ ! -d "${BUILD_DIR}/llama/.git" ]; then
    echo "Cloning llama.cpp..."
    git clone --depth=1 https://github.com/ggerganov/llama.cpp.git "${BUILD_DIR}/llama"
fi

# Copy our JNI sources
cp "${CPP_DIR}/gguf_context.h"   "${BUILD_DIR}/llama/"
cp "${CPP_DIR}/gguf_context.cc"  "${BUILD_DIR}/llama/"
cp "${CPP_DIR}/gguf_jni.cpp"     "${BUILD_DIR}/llama/"
cp "${CPP_DIR}/CMakeLists.txt"   "${BUILD_DIR}/llama/"

cd "${BUILD_DIR}/build"

TOOLCHAIN="${NDK_DIR}/toolchains/llvm/prebuilt/linux-x86_64"
API=26
TARGET="aarch64-linux-android${API}"

cmake "${BUILD_DIR}/llama" \
    -DCMAKE_TOOLCHAIN_FILE="${NDK_DIR}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-${API} \
    -DANDROID_STL=c++_static \
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

cp "${BUILD_DIR}/build/libgguf_engine.so" "${OUTPUT_DIR}/"
cp "${BUILD_DIR}/build/libgguf_engine.so" "${GGUF_DIR}/"

echo "=== DONE ==="
ls -lh "${OUTPUT_DIR}/libgguf_engine.so"
