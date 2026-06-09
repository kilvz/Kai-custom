cmake_minimum_required(VERSION 3.22)
project(libgguf_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp sources (core only, no examples)
file(GLOB LLAMA_SOURCES
    llama.cpp/*.cpp
    llama.cpp/ggml*.c
    llama.cpp/ggml*.cpp
)

# Our JNI wrapper
set(ENGINE_SOURCES
    gguf_context.cc
    gguf_jni.cpp
)

add_library(gguf_engine SHARED ${LLAMA_SOURCES} ${ENGINE_SOURCES})

target_include_directories(gguf_engine PRIVATE
    llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(gguf_engine PRIVATE
    GGML_USE_CPU
    LLAMA_SHARED
)

find_library(log-lib log)
target_link_libraries(gguf_engine PRIVATE ${log-lib} dl)
