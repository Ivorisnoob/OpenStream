package com.ivor.openstream.data.extensions

/**
 * Users paste repository links from wherever they found them. Normalise the common shapes into
 * something fetchable, the way CloudStream accepts both a raw link and a GitHub page link.
 */
object RepoUrlNormalizer {

    private val GITHUB_BLOB = Regex(
        "^https://github\\.com/([^/]+)/([^/]+)/blob/(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val GITHUB_TREE_ROOT = Regex(
        "^https://github\\.com/([^/]+)/([^/]+)/?$",
        RegexOption.IGNORE_CASE
    )

    fun normalize(input: String): String? {
        val trimmed = input.trim().trim('"', '\'')
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.removePrefix("http://")
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains(' ') -> return null
            trimmed.contains('.') -> "https://$trimmed"
            else -> return null
        }.trimEnd('/')

        GITHUB_BLOB.find(withScheme)?.let { match ->
            val (owner, repo, path) = match.destructured
            return "https://raw.githubusercontent.com/$owner/$repo/$path"
        }
        GITHUB_TREE_ROOT.find(withScheme)?.let { match ->
            val (owner, repo) = match.destructured
            return "https://raw.githubusercontent.com/$owner/$repo/main/extensions/index.json"
        }
        return withScheme
    }

    /** Stable, filesystem-safe identifier for a repository URL. */
    fun repoId(url: String): String {
        val slug = url
            .removePrefix("https://")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
        return if (slug.isEmpty()) "repo-${url.hashCode().toUInt()}" else "$slug-${url.hashCode().toUInt()}"
    }
}
