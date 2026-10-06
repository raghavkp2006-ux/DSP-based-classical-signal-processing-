package com.signalchain.app.ui

import java.io.File

/** Keeps private audio intermediates short-lived and confines cleanup to our cache-owned names. */
internal object CacheFileCleanup {
    private val prefixes = listOf(
        "signalchain-enhanced-",
        "signalchain-canonical-",
        "signalchain-input-",
        "signalchain-mic-"
    )

    fun cleanupStaleOnStartup(cacheDir: File) {
        deleteManaged(cacheDir) { true }
    }

    fun clearHistoryArtifacts(cacheDir: File) {
        deleteManaged(cacheDir) { it.name.startsWith("signalchain-enhanced-") || isBridge(it) }
    }

    fun deleteIfUnreferenced(cacheDir: File, path: String?) {
        if (path == null) return
        val root = cacheDir.canonicalFile.toPath()
        val candidate = File(path).canonicalFile
        if (!candidate.toPath().startsWith(root) || !isManaged(candidate)) return
        candidate.delete()
    }

    private fun deleteManaged(cacheDir: File, predicate: (File) -> Boolean) {
        if (!cacheDir.exists()) return
        cacheDir.walkTopDown().filter { it.isFile && isManaged(it) && predicate(it) }.forEach { it.delete() }
    }

    private fun isManaged(file: File) = prefixes.any { prefix -> file.name.startsWith(prefix) }
    private fun isBridge(file: File) = file.name.startsWith("signalchain-canonical-") ||
        file.name.startsWith("signalchain-input-") || file.name.startsWith("signalchain-mic-")
}
