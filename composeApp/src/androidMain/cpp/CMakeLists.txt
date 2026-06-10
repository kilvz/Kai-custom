cmake_minimum_required(VERSION 3.22)
project(libgguf_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ENGINE_SOURCES gguf_context.cc gguf_jni.cpp)

add_library(gguf_engine SHARED ${ENGINE_SOURCES})

target_include_directories(gguf_engine PRIVATE
    include
    ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(gguf_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../build/bin/libllama.so
    dl
)
