cmake_minimum_required(VERSION 3.22)
project(libgguf_engine LANGUAGES C CXX)



add_subdirectory(llama)

set(ENGINE_SOURCES gguf_context.cc gguf_jni.cpp)

add_library(gguf_engine SHARED ${ENGINE_SOURCES})

set_target_properties(gguf_engine PROPERTIES
    CXX_STANDARD 23
    CXX_STANDARD_REQUIRED ON
)

target_include_directories(gguf_engine PRIVATE
    llama/include
    llama/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(gguf_engine PRIVATE
    llama
    dl
)
