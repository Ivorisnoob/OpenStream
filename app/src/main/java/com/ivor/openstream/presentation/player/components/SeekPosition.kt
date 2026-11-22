package com.ivor.openstream.presentation.player.components

import kotlin.math.abs

internal fun snapSeekPosition(target: Long, origin: Long, duration: Long): Long {
    if (target <= 0L || target >= duration) return target
    val tolerance = (duration / 200).coerceIn(100L, 750L)
    return if (abs(target - origin) <= tolerance) origin else target
}
