package com.ivor.openstream.domain.model

/** Someone who watches on this device, with their own library and progress. */
data class Profile(
    val id: Long,
    val name: String,
    /** Built-in avatar key; the UI maps it to an icon and color role. */
    val avatar: String,
    /** Home and Search only show titles rated for children; Settings sits behind a hold gesture. */
    val isKids: Boolean
)
