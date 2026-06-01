package com.inspiredandroid.kai.data

enum class FileCategory {
    IMAGE,
    TEXT,
    PDF,
    BINARY,
    UNSUPPORTED,
}

const val MAX_TEXT_FILE_BYTES = 200_000
const val MAX_PDF_BYTES = 20_000_000
const val MAX_IMAGE_BYTES = 15_000_000
const val MAX_BINARY_BYTES = 50_000_000

// Raw image input cap before compression â€” images typically shrink after compression,
// so we allow larger raw files than MAX_IMAGE_BYTES while still preventing an OOM
// from reading a multi-gigabyte file into memory.
const val MAX_RAW_IMAGE_BYTES = 50_000_000

private val textMimeTypes = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-yaml",
    "application/yaml",
    "application/x-sh",
    "application/sql",
    "application/graphql",
    "application/toml",
)

private val textExtensions = setOf(
    "txt", "md", "json", "csv", "xml", "yaml", "yml",
    "html", "css", "js", "ts", "kt", "kts", "java",
    "py", "rb", "rs", "go", "c", "h", "cpp", "hpp",
    "swift", "sh", "bash", "zsh", "sql", "graphql",
    "toml", "ini", "cfg", "conf", "log", "properties",
    "gradle", "tsx", "jsx", "gsc",
)

internal val imageExtensions = setOf(
    "jpg",
    "jpeg",
    "png",
    "gif",
    "webp",
    "bmp",
    "svg",
)

val supportedFileExtensions = emptyList<String>() // Empty = accept all file types

fun classifyFile(mimeType: String?, fileName: String?): FileCategory {
    if (mimeType != null) {
        if (mimeType.startsWith("image/")) return FileCategory.IMAGE
        if (mimeType == "application/pdf") return FileCategory.PDF
        if (mimeType.startsWith("text/") || mimeType in textMimeTypes) return FileCategory.TEXT
    }
    // Fall back to extension
    val ext = fileName?.substringAfterLast('.', "")?.lowercase()
    if (ext != null && ext in imageExtensions) return FileCategory.IMAGE
    if (ext != null && ext in textExtensions) return FileCategory.TEXT
    if (ext == "pdf") return FileCategory.PDF

    // Unknown type â€” treat as binary
    return FileCategory.BINARY
}
