REM @echo off
REM build-gguf.bat — Cross-compile llama.cpp + JNI wrapper for Android
REM Run this after cloning llama.cpp into .build-gguf/llama

if not exist ".build-gguf\llama\CMakeLists.txt" (
    echo llama.cpp not found. Cloning...
    wsl -d Ubuntu -- bash -c "git clone --depth=1 https://github.com/ggerganov/llama.cpp.git /mnt/f/Kai/.build-gguf/llama"
) else (
    wsl -d Ubuntu -- bash -c "cd /mnt/f/Kai/.build-gguf/llama && git reset --hard && git clean -fd"
)

copy /Y composeApp\src\androidMain\cpp\gguf_context.h .build-gguf\ >nul
copy /Y composeApp\src\androidMain\cpp\gguf_context.cc .build-gguf\ >nul
copy /Y composeApp\src\androidMain\cpp\gguf_jni.cpp .build-gguf\ >nul
copy /Y composeApp\src\androidMain\cpp\CMakeLists.txt .build-gguf\ >nul

rmdir /s /q .build-gguf\build 2>nul
mkdir .build-gguf\build 2>nul

wsl -d Ubuntu bash -c "cd /mnt/f/Kai/.build-gguf/build && cmake /mnt/f/Kai/.build-gguf -DCMAKE_TOOLCHAIN_FILE=/opt/android-ndk-r29/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_static -DCMAKE_C_COMPILER=/opt/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang -DCMAKE_CXX_COMPILER=/opt/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang++ -DLLAMA_CUDA=OFF -DGGML_VULKAN=ON -DLLAMA_METAL=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF -DGGML_OPENMP=OFF -DLLAMA_OPENMP=OFF -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_TYPE=Release -DVulkan_INCLUDE_DIR=/opt/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/vulkan -DVulkan_LIBRARY=/opt/android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/28/libvulkan.so -DSPIRV-Headers_DIR=/usr/share/cmake/SPIRV-Headers" && wsl -d Ubuntu bash -c "cd /mnt/f/Kai/.build-gguf/build && make VERBOSE=1 -j4 gguf_engine"

if errorlevel 1 (
    echo Build failed
    exit /b 1
)

copy .build-gguf\build\bin\*.so androidApp\src\main\jniLibs\arm64-v8a\ >nul
copy .build-gguf\build\libgguf_engine.so androidApp\src\main\jniLibs\arm64-v8a\ >nul
for /f %%i in ('dir .build-gguf\build\bin\ /b ^| find "vulkan"') do copy .build-gguf\build\bin\%%i androidApp\src\main\jniLibs\arm64-v8a\ >nul
copy .build-gguf\build\libgguf_engine.so gguf\ >nul
echo OK — libraries are copied to jniLibs\ and gguf\
