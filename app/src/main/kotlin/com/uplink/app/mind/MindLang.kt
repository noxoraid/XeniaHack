package com.uplink.app

object MindLang {
    private val byExtension = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "java" to "java", "py" to "python",
        "js" to "javascript", "jsx" to "javascript", "mjs" to "javascript", "cjs" to "javascript",
        "ts" to "typescript", "tsx" to "typescript",
        "c" to "c", "h" to "c", "cpp" to "cpp", "cc" to "cpp", "cxx" to "cpp", "hpp" to "cpp", "hh" to "cpp",
        "go" to "go", "rs" to "rust", "rb" to "ruby", "php" to "php", "swift" to "swift", "cs" to "csharp",
        "dart" to "dart", "sh" to "shell", "lua" to "lua", "html" to "html", "css" to "css", "scss" to "scss",
        "vue" to "vue", "sql" to "sql", "gradle" to "gradle",
        "md" to "markdown", "json" to "json", "yml" to "yaml", "yaml" to "yaml", "toml" to "toml",
        "xml" to "xml", "txt" to "text", "properties" to "properties",
    )

    private val byName = mapOf("Dockerfile" to "docker", "Makefile" to "make", "CMakeLists.txt" to "cmake")

    val skippedDirs = setOf(
        ".git", "node_modules", "build", ".gradle", ".idea", "dist", "out", "target", "__pycache__",
        ".venv", "venv", ".next", ".cache", "vendor", ".dart_tool", "Pods",
    )

    fun of(name: String): String? = byName[name] ?: byExtension[MindDetect.extensionOf(name)]

    fun outlined(lang: String): Boolean = lang in setOf("kotlin", "java", "python", "javascript", "typescript", "c", "cpp")
}
