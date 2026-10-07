package com.ivor.openstream.presentation.player

import android.content.Context
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer

fun VideoServer.sourceQualityLabel(): String {
    val adaptive = streamType == "HLS" || streamType == "DASH"
    return when {
        quality == StreamQuality.UNKNOWN && adaptive -> "Adaptive"
        quality == StreamQuality.UNKNOWN -> "Quality unknown"
        adaptive -> "Up to ${quality.label}"
        else -> quality.label
    }
}

/** Localized variant of [sourceQualityLabel] for UI; the pure version above stays for tests. */
fun VideoServer.sourceQualityLabel(context: Context): String {
    val adaptive = streamType == "HLS" || streamType == "DASH"
    return when {
        quality == StreamQuality.UNKNOWN && adaptive -> context.getString(R.string.src_adaptive)
        quality == StreamQuality.UNKNOWN -> context.getString(R.string.player_quality_unknown)
        adaptive -> context.getString(R.string.src_up_to_quality, quality.label)
        else -> quality.label
    }
}

fun VideoServer.sourceSummary(): String = buildList {
    add(sourceQualityLabel())
    if (audio.label.isNotBlank()) add(audio.label)
    add(streamType)
}.joinToString(" · ")

/** Localized variant of [sourceSummary] for UI; the pure version above stays for tests. */
fun VideoServer.sourceSummary(context: Context): String = buildList {
    add(sourceQualityLabel(context))
    val audioLabel = audio.labelRes?.let { context.getString(it) } ?: audio.label
    if (audioLabel.isNotBlank()) add(audioLabel)
    add(streamType)
}.joinToString(" · ")
