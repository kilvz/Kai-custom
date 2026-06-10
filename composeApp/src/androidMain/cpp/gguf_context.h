#ifndef GGUF_CONTEXT_H
#define GGUF_CONTEXT_H

#include <string>
#include <vector>
#include <mutex>
#include "include/llama.h"

struct GgufChatMessage {
    std::string role;
    std::string content;
};

class GgufContext {
public:
    GgufContext();
    ~GgufContext();

    // Load a GGUF model. Returns true on success.
    bool loadModel(const std::string& modelPath, int nCtx);

    // Run chat inference. Takes system prompt, message history, and sampling params.
    // Returns the assistant response text.
    std::string chat(
        const std::string& systemPrompt,
        const std::vector<GgufChatMessage>& messages,
        int topK,
        float topP,
        float temperature,
        int maxTokens
    );

    // Release all resources.
    void release();

    bool isLoaded() const { return model != nullptr && ctx != nullptr; }

private:
    llama_model* model;
    llama_context* ctx;
    std::mutex mutex;
    bool terminated;

    static constexpr int N_THREADS = 4;
};

#endif // GGUF_CONTEXT_H
