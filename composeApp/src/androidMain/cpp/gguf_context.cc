#include "gguf_context.h"
#include <android/log.h>
#include <sstream>
#include <algorithm>

#define LOG_TAG "GGUFEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

GgufContext::GgufContext() : model(nullptr), ctx(nullptr), smpl(nullptr), terminated(false) {}

GgufContext::~GgufContext() {
    release();
}

bool GgufContext::loadModel(const std::string& modelPath, int nCtx) {
    std::lock_guard<std::mutex> lock(mutex);

    if (model) release();

    // Default context params
    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0; // CPU only for now

    model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (!model) {
        LOGE("Failed to load model from %s", modelPath.c_str());
        return false;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = (nCtx > 0) ? nCtx : 2048;
    ctxParams.n_threads = N_THREADS;
    ctxParams.n_threads_batch = N_THREADS;

    ctx = llama_init_from_model(model, ctxParams);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        model = nullptr;
        return false;
    }

    LOGI("Model loaded: %s, n_ctx=%d", modelPath.c_str(), ctxParams.n_ctx);
    return true;
}

std::string GgufContext::chat(
    const std::string& systemPrompt,
    const std::vector<GgufChatMessage>& messages,
    int topK,
    float topP,
    float temperature,
    int maxTokens
) {
    std::lock_guard<std::mutex> lock(mutex);
    if (!model || !ctx) return "";

    terminated = false;
    std::string result;

    // Build prompt from chat template
    std::ostringstream prompt;
    if (!systemPrompt.empty()) {
        prompt << "<|im_start|>system\n" << systemPrompt << "<|im_end|>\n";
    }
    for (const auto& msg : messages) {
        if (msg.role == "user") {
            prompt << "<|im_start|>user\n" << msg.content << "<|im_end|>\n";
        } else if (msg.role == "assistant") {
            prompt << "<|im_start|>assistant\n" << msg.content << "<|im_end|>\n";
        }
    }
    prompt << "<|im_start|>assistant\n";

    // Tokenize
    std::string promptStr = prompt.str();
    int nTokens = promptStr.length() / 2 + 1024; // rough upper bound
    std::vector<llama_token> tokens(nTokens);
    nTokens = llama_tokenize(model, promptStr.data(), promptStr.size(), tokens.data(), tokens.size(), true, false);
    if (nTokens < 0) {
        LOGE("Tokenization failed");
        return "";
    }
    tokens.resize(nTokens);

    // Eval prompt
    if (llama_decode(ctx, llama_batch_get_one(tokens.data(), tokens.size(), 0, 0))) {
        LOGE("Prompt eval failed");
        return "";
    }

    // Sampling params
    auto* smplChain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smplChain, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(smplChain, llama_sampler_init_temp(temperature));
    }
    llama_sampler_chain_add(smplChain, llama_sampler_init_dist(topK, topP, 69420));

    // Generate
    int generated = 0;
    int maxGen = (maxTokens > 0) ? maxTokens : 512;
    while (generated < maxGen && !terminated) {
        llama_token newToken = llama_sampler_sample(smplChain, ctx, -1);

        if (llama_token_is_eog(model, newToken)) break;

        // Append to result
        char buf[256];
        int n = llama_token_to_piece(model, newToken, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        // Feed back
        llama_token id = newToken;
        if (llama_decode(ctx, llama_batch_get_one(&id, 1, llama_n_ctx(ctx) - 1, 0))) {
            LOGE("Generation decode failed");
            break;
        }
        generated++;
    }

    llama_sampler_free(smplChain);
    return result;
}

void GgufContext::release() {
    std::lock_guard<std::mutex> lock(mutex);
    terminated = true;
    if (smpl) {
        llama_sampler_free(smpl);
        smpl = nullptr;
    }
    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
}
