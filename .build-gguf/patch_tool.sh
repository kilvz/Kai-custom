#!/usr/bin/env bash
set -euo pipefail
FILE="/mnt/f/Kai/.build-gguf/llama/ggml/src/ggml-vulkan/vulkan-shaders/vulkan-shaders-gen.cpp"
# Add -I include path to glslc command
sed -i 's|GLSLC, "-fshader-stage=compute", target_env, in_path, "-o", out_path|GLSLC, "-fshader-stage=compute", target_env, "-I" + input_filepath, in_path, "-o", out_path|' "$FILE"
echo "Patched $FILE"
