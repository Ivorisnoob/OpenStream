package com.ivor.openstream.data.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPresetAdjustmentsTest {
    @Test fun `old settings migrate and each look keeps its adjustments on disk`() {
        val codec = VideoShaderCustomization.serializer()
        val previous = Json.decodeFromString(codec, """{"activePreset":"film","contrast":1.25}""")
        val restore = previous.selectPreset(VideoShaderPreset.ANIMATION_RESTORE)
        assertEquals(1f, restore.contrast, 0f)
        val edited = restore.copy(sharpness = 0.4f).selectPreset(VideoShaderPreset.FILM)
        val reopened = Json.decodeFromString(codec, Json.encodeToString(codec, edited))
        assertEquals(1.25f, reopened.contrast, 0f)
        assertEquals(0.4f, reopened.selectPreset(VideoShaderPreset.ANIMATION_RESTORE).sharpness, 0f)
    }
}
