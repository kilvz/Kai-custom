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