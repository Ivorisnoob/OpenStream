package com.ivor.openstream.data.streaming

internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

internal const val VIDKING_ORIGIN = "https://www.vidking.net"

internal fun vidkingRequestHeaders(): Map<String, String> = mapOf(
    "User-Agent" to BROWSER_USER_AGENT,
    "Referer" to "$VIDKING_ORIGIN/",
    "Origin" to VIDKING_ORIGIN
)
