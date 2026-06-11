package com.kai.custom.inference

import kotlinx.serialization.Serializable

// ── Standard models (Apache-2.0, official litert-community) ──
val STANDARD_MODELS = listOf(
    LocalModel(
        id = "qwen3-0.6b",
        displayName = "Qwen3 0.6B",
        fileName = "Qwen3-0.6B.litertlm",
        sizeBytes = 614_236_160L,
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        gpuMemoryMb = 300,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 35_000,
    ),
    LocalModel(
        id = "qwen3-4b",
        displayName = "Qwen3 4B (INT4)",
        fileName = "qwen3_4b_mixed_int4.litertlm",
        sizeBytes = 2_660_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm",
        gpuMemoryMb = 400,
        defaultContextTokens = 2_048,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 45_000,
        isRecommended = true,
    ),
    LocalModel(
        id = "qwen3-8b",
        displayName = "Qwen3 8B (INT4)",
        fileName = "qwen3_8b_mixed_int4.litertlm",
        sizeBytes = 4_890_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-8B/resolve/main/qwen3_8b_mixed_int4.litertlm",
        gpuMemoryMb = 500,
        defaultContextTokens = 2_048,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 60_000,
    ),
    LocalModel(
        id = "tinyswallow-1.5b",
        displayName = "TinySwallow 1.5B",
        fileName = "TinySwallow-1.5B-Instruct.litertlm",
        sizeBytes = 1_570_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/TinySwallow-1.5B-Instruct/resolve/main/TinySwallow-1.5B-Instruct.litertlm",
        gpuMemoryMb = 250,
        defaultContextTokens = 2_048,
        maxContextTokens = 8_192,
        kvPerTokenBytes = 20_000,
    ),
    LocalModel(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_580_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        gpuMemoryMb = 676,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 50_000,
        isRecommended = true,
    ),
    LocalModel(
        id = "gemma-4-e4b-it",
        displayName = "Gemma 4 E4B IT",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_650_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        gpuMemoryMb = 710,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 75_000,
    ),
)

// ── Uncensored models (abliterated, third-party conversions) ──
val UNCENSORED_MODELS = listOf(
    LocalModel(
        id = "uncensored-gemma-4-e2b",
        displayName = "Gemma 4 E2B (Uncensored)",
        fileName = "Gemma-4-E2B-Abliterated.litertlm",
        sizeBytes = 2_560_000_000L,
        downloadUrl = "https://huggingface.co/DuoNeural/Gemma-4-Abliterated-LiteRT/resolve/main/Gemma-4-E2B-Abliterated.litertlm",
        gpuMemoryMb = 676,
        defaultContextTokens = 2_048,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 50_000,
    ),
    LocalModel(
        id = "uncensored-gemma-4-e4b",
        displayName = "Gemma 4 E4B (Uncensored)",
        fileName = "Gemma-4-E4B-Abliterated.litertlm",
        sizeBytes = 4_120_000_000L,
        downloadUrl = "https://huggingface.co/DuoNeural/Gemma-4-Abliterated-LiteRT/resolve/main/Gemma-4-E4B-Abliterated.litertlm",
        gpuMemoryMb = 710,
        defaultContextTokens = 2_048,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 75_000,
    ),
)

val MODEL_CATALOG = STANDARD_MODELS + UNCENSORED_MODELS

val GGUF_MODELS = listOf(
    LocalModel(
        id = "gguf_qwen3_4b_abliterated",
        displayName = "Qwen3 4B Abliterated (GGUF Q4_K_M)",
        fileName = "Huihui-Qwen3-4B-abliterated-v2.i1-Q4_K_M.gguf",
        sizeBytes = 2_600_000_000L,
        downloadUrl = "https://huggingface.co/mradermacher/Huihui-Qwen3-4B-abliterated-v2-i1-GGUF/resolve/main/Huihui-Qwen3-4B-abliterated-v2.i1-Q4_K_M.gguf",
        gpuMemoryMb = 400,
        defaultContextTokens = 2_048,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 45_000,
    ),
    LocalModel(
        id = "gguf_qwen3.5_2b_uncensored",
        displayName = "Qwen3 1.7B Abliterated (GGUF Q4_K_M)",
        fileName = "Huihui-Qwen3-1.7B-abliterated-v2.i1-Q4_K_M.gguf",
        sizeBytes = 1_200_000_000L,
        downloadUrl = "https://huggingface.co/mradermacher/Huihui-Qwen3-1.7B-abliterated-v2-i1-GGUF/resolve/main/Huihui-Qwen3-1.7B-abliterated-v2.i1-Q4_K_M.gguf",
        gpuMemoryMb = 200,
        defaultContextTokens = 4_096,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 25_000,
    ),
    LocalModel(
        id = "gguf_peach_9b_roleplay",
        displayName = "Peach 2.0 9B Roleplay (GGUF Q4_K_M)",
        fileName = "Peach-2.0-9B-8k-Roleplay-heretic.i1-Q4_K_M.gguf",
        sizeBytes = 5_400_000_000L,
        downloadUrl = "https://huggingface.co/mradermacher/Peach-2.0-9B-8k-Roleplay-heretic-i1-GGUF/resolve/main/Peach-2.0-9B-8k-Roleplay-heretic.i1-Q4_K_M.gguf",
        gpuMemoryMb = 500,
        defaultContextTokens = 8_192,
        maxContextTokens = 32_768,
        kvPerTokenBytes = 60_000,
    ),
)

private val THINK_BLOCK_REGEX = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)

// Qwen3 emits <think>…</think> blocks as part of its chat template; strip them before
// the user sees them. Safe for Gemma 4, which never emits these tags.
fun stripThinkBlocks(s: String): String {
    // Qwen3-Thinking sometimes emits </think> without an opening <think> tag;
    // treat everything up to and including </think> as thinking content.
    var fixed = if (!s.contains("<think>") && s.contains("</think>")) {
        s.substringAfter("</think>").trim()
    } else {
        s
    }

    // If generation stopped early inside a <think> block without closing it
    if (fixed.contains("<think>") && !fixed.contains("</think>")) {
        fixed = fixed.substringBefore("<think>").trim()
    }

    return THINK_BLOCK_REGEX.replace(fixed, "").trim()
}

/**
 * Drops UTF-16 surrogate halves from the string. The litert-lm JNI layer passes
 * strings to the native runtime as *modified* UTF-8, which encodes supplementary-plane
 * characters (U+10000–U+10FFFF — most emoji like 🗺️, 🎉, 🔥) as surrogate-pair
 * sequences where each half becomes a 3-byte block. That is invalid as *standard*
 * UTF-8, and the native runtime's `nlohmann::json` parser crashes with "ill-formed
 * UTF-8 byte" the moment it hits one. The Swift bridge on iOS hits the same parser.
 *
 * Filtering surrogates drops every supplementary character (both halves are surrogate
 * code units in UTF-16) while leaving BMP characters — including BMP-only emoji like
 * ⚔️, ♻️, ❤️, and all CJK / extended Latin / accented characters — untouched.
 * No-op for strings that don't contain any supplementary character.
 */
fun sanitizeForLiteRt(s: String?): String? {
    if (s == null) return null
    if (s.none { it.isSurrogate() }) return s
    return s.filter { !it.isSurrogate() }
}

@Serializable
data class ImportedModel(
    val id: String,
    val displayName: String,
    val filePath: String,
    val sizeBytes: Long = 0,
)
