package com.ivor.openstream.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private val theme = SettingsSearch.Candidate("theme", "Theme", null, "Appearance", listOf("System", "Light", "Dark"))
    private val amoled = SettingsSearch.Candidate("amoled", "AMOLED black", "Pure black dark theme", "Appearance")
    private val subtitles = SettingsSearch.Candidate("subtitles", "Subtitles", "Saved with downloads: English", "Downloads")
    private val quality = SettingsSearch.Candidate("quality", "Qualité limite", "Plus haute qualité", "Qualité et données")

    private val all = listOf(subtitles, amoled, theme, quality)

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(SettingsSearch.search(all, "   ").isEmpty())
    }

    @Test
    fun titleStartBeatsMatchesElsewhere() {
        // "Theme" starts with it; AMOLED only mentions it in its summary.
        assertEquals(listOf("theme", "amoled"), SettingsSearch.search(all, "the").map { it.key })
    }

    @Test
    fun choicesAndSectionsAreSearchable() {
        assertEquals(listOf("amoled", "theme"), SettingsSearch.search(all, "dark").map { it.key })
        assertEquals(listOf("subtitles"), SettingsSearch.search(all, "downloads").map { it.key })
    }

    @Test
    fun everyWordHasToMatchInAnyOrder() {
        assertEquals(listOf("amoled"), SettingsSearch.search(all, "black pure").map { it.key })
        assertTrue(SettingsSearch.search(all, "black subtitles").isEmpty())
    }

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals(listOf("quality"), SettingsSearch.search(all, "QUALITE").map { it.key })
    }

    @Test
    fun highlightsPointAtTheOriginalText() {
        val text = "Qualité limite"
        val ranges = SettingsSearch.highlights(text, "qualite lim")
        assertEquals(listOf("Qualité", "lim"), ranges.map { text.substring(it.first, it.last + 1) })
    }

    @Test
    fun overlappingHighlightsMerge() {
        assertEquals(listOf(0..5), SettingsSearch.highlights("Amoled black", "amo moled"))
    }
}
