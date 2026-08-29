package com.ivor.openstream.data.extensions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the last successful index of every repository on disk so the marketplace still renders
 * (and installed extensions still resolve) with no network.
 */
@Singleton
class ExtensionCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: ExtensionIndexParser
) {
    private val directory: File by lazy {
        File(context.filesDir, "extension-cache").apply { mkdirs() }
    }

    fun read(repoId: String): CachedRepoSnapshot? {
        val file = fileFor(repoId)
        if (!file.exists()) return null
        return runCatching { parser.decodeSnapshot(file.readText()) }.getOrNull()
    }

    fun write(repoId: String, snapshot: CachedRepoSnapshot) {
        runCatching { fileFor(repoId).writeText(parser.encodeSnapshot(snapshot)) }
    }

    fun delete(repoId: String) {
        runCatching { fileFor(repoId).delete() }
    }

    private fun fileFor(repoId: String) = File(directory, "$repoId.json")
}
