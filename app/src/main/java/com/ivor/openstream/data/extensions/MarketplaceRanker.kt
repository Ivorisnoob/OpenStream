package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionStatus
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.MarketplaceSort
import kotlin.math.ln
import kotlin.math.max

/**
 * Ordering and search for the marketplace.
 *
 * Store-style charts need more than a raw install count: a source that is flagged down, or that
 * keeps failing on this device, should not sit at the top just because it is old and popular. So
 * the score blends the repository's published signals (installs, rating, status, freshness) with
 * the device's own success history.
 */
object MarketplaceRanker {

    private const val DAY_MS = 24 * 60 * 60 * 1000.0
    private const val TRENDING_WINDOW_DAYS = 30.0

    fun statusFactor(status: ExtensionStatus): Double = when (status) {
        ExtensionStatus.OK -> 1.0
        ExtensionStatus.SLOW -> 0.75
        ExtensionStatus.BETA -> 0.6
        ExtensionStatus.DOWN -> 0.15
    }

    fun popularityScore(extension: MarketplaceExtension): Double {
        val manifest = extension.manifest
        val base = ln(1.0 + manifest.installs)
        val ratingBoost = if (manifest.ratingCount >= 5) (manifest.rating - 3.5) * 0.6 else 0.0
        val localBoost = extension.usage.successRate?.let { (it - 0.5) * 2.0 } ?: 0.0
        val supportPenalty = if (manifest.isSupported) 0.0 else -4.0
        // Repositories that publish no install telemetry (the official one does not) still get a
        // stable order from the curation order they ship in.
        val curationBoost = (100 - manifest.engine.priority).coerceIn(0, 100) / 25.0
        return (base + ratingBoost + localBoost + curationBoost + supportPenalty) *
            statusFactor(manifest.status)
    }

    fun trendingScore(extension: MarketplaceExtension, now: Long): Double {
        val manifest = extension.manifest
        val recent = ln(1.0 + manifest.installsLast7Days) * 1.5
        val freshness = if (manifest.updatedAt <= 0L) {
            0.0
        } else {
            val days = max(0.0, (now - manifest.updatedAt) / DAY_MS)
            max(0.0, 1.0 - days / TRENDING_WINDOW_DAYS) * 2.0
        }
        val base = ln(1.0 + manifest.installs) * 0.2
        return (recent + freshness + base) * statusFactor(manifest.status)
    }

    fun sort(
        extensions: List<MarketplaceExtension>,
        sort: MarketplaceSort,
        now: Long = System.currentTimeMillis()
    ): List<MarketplaceExtension> {
        val byName = compareBy<MarketplaceExtension> { it.manifest.name.lowercase() }
        return when (sort) {
            MarketplaceSort.POPULAR ->
                extensions.sortedWith(compareByDescending<MarketplaceExtension> { popularityScore(it) }.then(byName))
            MarketplaceSort.TRENDING ->
                extensions.sortedWith(compareByDescending<MarketplaceExtension> { trendingScore(it, now) }.then(byName))
            MarketplaceSort.TOP_RATED ->
                extensions.sortedWith(
                    compareByDescending<MarketplaceExtension> { it.manifest.rating.toDouble() * statusFactor(it.manifest.status) }
                        .thenByDescending { it.manifest.ratingCount }
                        .then(byName)
                )
            MarketplaceSort.RECENT ->
                extensions.sortedWith(compareByDescending<MarketplaceExtension> { it.manifest.updatedAt }.then(byName))
            MarketplaceSort.NAME -> extensions.sortedWith(byName)
        }
    }

    fun search(extensions: List<MarketplaceExtension>, query: String): List<MarketplaceExtension> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return extensions
        return extensions.filter { extension ->
            val manifest = extension.manifest
            manifest.name.lowercase().contains(needle) ||
                manifest.description.lowercase().contains(needle) ||
                manifest.language.lowercase().contains(needle) ||
                manifest.tags.any { it.contains(needle) } ||
                manifest.authors.any { it.lowercase().contains(needle) }
        }
    }

    fun filterByTag(extensions: List<MarketplaceExtension>, tag: String?): List<MarketplaceExtension> {
        if (tag.isNullOrBlank()) return extensions
        val needle = tag.lowercase()
        return extensions.filter { extension ->
            needle in extension.manifest.tags || extension.manifest.language.lowercase() == needle
        }
    }

    /** Distinct tags across the catalog, most common first — the marketplace's category rail. */
    fun tags(extensions: List<MarketplaceExtension>, limit: Int = 10): List<String> =
        extensions
            .flatMap { it.manifest.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    /** The "top charts" rail: healthy, installable extensions only. */
    fun topCharts(
        extensions: List<MarketplaceExtension>,
        limit: Int = 5,
        now: Long = System.currentTimeMillis()
    ): List<MarketplaceExtension> =
        sort(
            extensions.filter { it.manifest.isSupported && it.manifest.status != ExtensionStatus.DOWN },
            MarketplaceSort.POPULAR,
            now
        ).take(limit)
}
