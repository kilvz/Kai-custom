#include "gguf_context.h"
#include <cstdio>
#include <sstream>
#include <algorithm>

#define LOGI(...) fprintf(stdout, "[GGUF] " __VA_ARGS__); fprintf(stdout, "\n")
#define LOGE(...) fprintf(stderr, "[GGUF ERROR] " __VA_ARGS__); fprintf(stderr, "\n")

GgufContext::GgufContext() : model(nullptr), ctx(nullptr), terminated(false) {}

GgufContext::~GgufContext() { release(); }

bool GgufContext::loadModel(const std::string& modelPath, int nCtx, int nGpuLayers, int nThreads, int nBatch) {
    std::lock_guard<std::mutex> lock(mutex);
    if (model) release();

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = (nGpuLayers > 0) ? nGpuLayers : 0;

    model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (!model) { LOGE("Failed to load model"); return false; }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = (nCtx > 0) ? nCtx : 2048;
    ctxParams.n_batch = (nBatch > 0) ? nBatch : 512;
    ctxParams.n_threads = (nThreads > 0) ? nThreads : 4;
    ctxParams.n_threads_batch = (nThreads > 0) ? nThreads : 4;

    ctx = llama_init_from_model(model, ctxParams);
    if (!ctx) { LOGE("Failed to create context"); llama_model_free(model); model = nullptr; return false; }

    LOGI("Model loaded, n_ctx=%d, n_gpu_layers=%d, n_threads=%d, n_batch=%d",
         ctxParams.n_ctx, modelParams.n_gpu_layers, ctxParams.n_threads, ctxParams.n_batch);
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

// ---------------------------------------------------------------------------
// Lightweight GGUF header-only metadata reader
// Reads only the KV pairs from the GGUF header (first few KB of the file).
// No model weights are loaded. Returns a JSON string with the metadata.
// Uses the GGUF value type enum from gguf.h (included via llama headers).
// ---------------------------------------------------------------------------

#include <cstdint>
#include <fstream>
#include <sstream>
#include <unordered_map>
#include <vector>
#include "gguf.h"

static std::string read_gguf_string(std::istream& stream) {
    uint64_t len;
    stream.read(reinterpret_cast<char*>(&len), sizeof(len));
    std::string s(len, '\0');
    if (len > 0) stream.read(&s[0], len);
    return s;
}

static std::string gguf_value_to_json(std::istream& stream, uint32_t type) {
    std::ostringstream out;
    switch (type) {
        case GGUF_TYPE_UINT8: { uint8_t v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << (int)v; break; }
        case GGUF_TYPE_INT8: { int8_t v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << (int)v; break; }
        case GGUF_TYPE_UINT16: { uint16_t v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << v; break; }
        case GGUF_TYPE_INT16: { int16_t v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << v; break; }
        case GGUF_TYPE_UINT32: { uint32_t v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << v; break; }
        case GGUF_TYPE_INT32: { int32_t v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << v; break; }
        case GGUF_TYPE_FLOAT32: { float v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << v; break; }
        case GGUF_TYPE_BOOL: { uint8_t v; stream.read(reinterpret_cast<char*>(&v), sizeof(v)); out << (v ? "true" : "false"); break; }
        case GGUF_TYPE_STRING: {
            std::string s = read_gguf_string(stream);
            out << "\"";
            for (char c : s) {
                if (c == '"' || c == '\\') out << '\\';
                out << c;
            }
            out << "\"";
            break;
        }
        case GGUF_TYPE_ARRAY: {
            uint32_t elemType; stream.read(reinterpret_cast<char*>(&elemType), sizeof(elemType));
            uint64_t count; stream.read(reinterpret_cast<char*>(&count), sizeof(count));
            out << "[";
            for (uint64_t i = 0; i < count; i++) {
                if (i > 0) out << ",";
                out << gguf_value_to_json(stream, elemType);
            }
            out << "]";
            break;
        }
        default: out << "null"; break;
    }
    return out.str();
}

std::string gguf_read_metadata(const std::string& modelPath) {
    std::ifstream file(modelPath, std::ios::binary);
    if (!file.is_open()) {
        return "{\"error\":\"cannot open file\"}";
    }

    // Read magic
    char magic[4];
    file.read(magic, 4);
    if (magic[0] != 'G' || magic[1] != 'G' || magic[2] != 'U' || magic[3] != 'F') {
        return "{\"error\":\"not a GGUF file\"}";
    }

    // Read version, tensor count, metadata count
    uint32_t version;   file.read(reinterpret_cast<char*>(&version), sizeof(version));
    uint64_t tensorCount; file.read(reinterpret_cast<char*>(&tensorCount), sizeof(tensorCount));
    uint64_t metadataCount; file.read(reinterpret_cast<char*>(&metadataCount), sizeof(metadataCount));

    std::ostringstream json;
    json << "{";
    bool first = true;

    for (uint64_t i = 0; i < metadataCount && file.good(); i++) {
        std::string key = read_gguf_string(file);
        if (!file.good()) break;

        uint32_t valueType;
        file.read(reinterpret_cast<char*>(&valueType), sizeof(valueType));
        if (!file.good()) break;

        if (!first) json << ",";
        first = false;

        json << "\"" << key << "\":";
        json << gguf_value_to_json(file, valueType);
    }

    json << ",\"_file_size\":" << (uint64_t)file.tellg();
    file.seekg(0, std::ios::end);
    json << ",\"_total_file_size\":" << (uint64_t)file.tellg();
    json << "}";

    return json.str();
}
