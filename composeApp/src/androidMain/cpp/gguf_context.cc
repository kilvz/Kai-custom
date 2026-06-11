#include "gguf_context.h"
#include <cstdio>
#include <sstream>
#include <algorithm>

#define LOGI(...) fprintf(stdout, "[GGUF] " __VA_ARGS__); fprintf(stdout, "\n")
#define LOGE(...) fprintf(stderr, "[GGUF ERROR] " __VA_ARGS__); fprintf(stderr, "\n")

GgufContext::GgufContext() : model(nullptr), ctx(nullptr), terminated(false) {}

GgufContext::~GgufContext() { release(); }

bool GgufContext::loadModel(const std::string& modelPath, int nCtx) {
    std::lock_guard<std::mutex> lock(mutex);
    if (model) release();

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;

    model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (!model) { LOGE("Failed to load model"); return false; }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = (nCtx > 0) ? nCtx : 2048;
    ctxParams.n_batch = ctxParams.n_ctx;
    ctxParams.n_threads = N_THREADS;
    ctxParams.n_threads_batch = N_THREADS;

    ctx = llama_init_from_model(model, ctxParams);
    if (!ctx) { LOGE("Failed to create context"); llama_model_free(model); model = nullptr; return false; }

    LOGI("Model loaded, n_ctx=%d", ctxParams.n_ctx);
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
    const auto* vocab = llama_model_get_vocab(model);
    std::string result;

    // Build prompt using built-in template
    std::vector<llama_chat_message> chat_msgs;
    if (!systemPrompt.empty()) {
        chat_msgs.push_back({"system", systemPrompt.c_str()});
    }
    for (const auto& msg : messages) {
        chat_msgs.push_back({msg.role.c_str(), msg.content.c_str()});
    }

    const char * tmpl = llama_model_chat_template(model, nullptr);
    std::vector<char> formatted(8192);
    int32_t len = llama_chat_apply_template(tmpl, chat_msgs.data(), chat_msgs.size(), true, formatted.data(), formatted.size());
    
    if (len > (int32_t)formatted.size()) {
        formatted.resize(len);
        len = llama_chat_apply_template(tmpl, chat_msgs.data(), chat_msgs.size(), true, formatted.data(), formatted.size());
    }

    std::string promptStr;
    if (len > 0) {
        promptStr = std::string(formatted.data(), len);
    } else {
        // Fallback if template application fails
        std::ostringstream prompt;
        if (!systemPrompt.empty()) {
            prompt << "<|im_start|>system\n" << systemPrompt << "<|im_end|>\n";
        }
        for (const auto& msg : messages) {
            prompt << "<|im_start|>" << msg.role << "\n" << msg.content << "<|im_end|>\n";
        }
        prompt << "<|im_start|>assistant\n";
        promptStr = prompt.str();
    }

    // Tokenize, parse_special = true
    int nTokensEst = (promptStr.size() / 2) + 1024;
    std::vector<llama_token> tokens(nTokensEst);
    int nTokens = llama_tokenize(vocab, promptStr.data(), promptStr.size(), tokens.data(), tokens.size(), true, true);
    if (nTokens < 0) { LOGE("Tokenization failed"); return ""; }
    tokens.resize(nTokens);

    // Check context bounds
    uint32_t n_ctx = llama_n_ctx(ctx);
    if (tokens.size() > n_ctx - 4) {
        LOGE("Prompt too long for context window");
        return "Error: Context window exceeded. Please clear chat or increase context size.";
    }

    // Eval prompt in chunks
    uint32_t n_batch = llama_n_batch(ctx);
    for (size_t i = 0; i < tokens.size(); i += n_batch) {
        size_t chunk = std::min((size_t)n_batch, tokens.size() - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
        if (llama_decode(ctx, batch)) { LOGE("Prompt eval failed at chunk %zu", i); return ""; }
    }

    // Build sampler chain
    auto* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (topK > 0) {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    }
    if (topP > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    }
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(69420));

    // Generate
    int generated = 0;
    int maxGen = (maxTokens > 0) ? maxTokens : 512;
    while (generated < maxGen && !terminated) {
        llama_token newToken = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(vocab, newToken)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, newToken, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        llama_batch batch2 = llama_batch_get_one(&newToken, 1);
        if (llama_decode(ctx, batch2)) { LOGE("Decode failed"); break; }
        generated++;
    }

    llama_sampler_free(smpl);
    return result;
}

void GgufContext::release() {
    std::lock_guard<std::mutex> lock(mutex);
    terminated = true;
    if (ctx) { llama_free(ctx); ctx = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }
}
