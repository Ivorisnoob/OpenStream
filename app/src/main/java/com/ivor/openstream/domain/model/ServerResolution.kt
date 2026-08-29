package com.ivor.openstream.domain.model

data class ServerResolution(
    val servers: List<VideoServer> = emptyList(),
    val completedProviders: Int = 0,
    val totalProviders: Int = 0,
    val failedProviders: List<String> = emptyList(),
    val isComplete: Boolean = false
)
