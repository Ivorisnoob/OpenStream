package com.ivor.openstream.data.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class SubtitleCharsetTest {

    private val turkish = "1\n00:00:01,000 --> 00:00:03,000\nKorsanın gemisi şimdi limana giriyor.\n\n" +
        "2\n00:00:04,000 --> 00:00:06,000\nİstanbul'a gidiyoruz, değil mi? Işıklar söndü.\n"
    private val russian = "1\n00:00:01,000 --> 00:00:03,000\nПривет, как дела? Мы идём домой.\n"
    private val french = "1\n00:00:01,000 --> 00:00:03,000\nÇa va très bien, merci. Où êtes-vous allé ?\n"

    private fun String.bytesIn(charset: String) = toByteArray(Charset.forName(charset))

    @Test
    fun validUtf8IsReadAsUtf8WhateverTheLanguage() {
        assertEquals(turkish, SubtitleCharset.decode(turkish.toByteArray(Charsets.UTF_8), "tr"))
        assertEquals(russian, SubtitleCharset.decode(russian.toByteArray(Charsets.UTF_8), null))
    }

    @Test
    fun byteOrderMarksDecideAndAreDropped() {
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + turkish.toByteArray(Charsets.UTF_8)
        assertEquals(turkish, SubtitleCharset.decode(utf8, "en"))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + turkish.toByteArray(Charsets.UTF_16LE)
        assertEquals(turkish, SubtitleCharset.decode(utf16, null))
    }

    @Test
    fun utf16WithoutBomIsRecognised() {
        assertEquals(french, SubtitleCharset.decode(french.toByteArray(Charsets.UTF_16LE), null))
        assertEquals(french, SubtitleCharset.decode(french.toByteArray(Charsets.UTF_16BE), null))
    }

    @Test
    fun turkishCodepageIsReadByLanguage() {
        val text = SubtitleCharset.decode(turkish.bytesIn("windows-1254"), "tr")
        assertEquals(turkish, text)
    }

    @Test
    fun turkishCodepageIsGuessedWithoutLanguage() {
        assertEquals(turkish, SubtitleCharset.decode(turkish.bytesIn("windows-1254"), null))
    }

    @Test
    fun cyrillicCodepageIsReadByLanguageOrGuessed() {
        assertEquals(russian, SubtitleCharset.decode(russian.bytesIn("windows-1251"), "ru"))
        assertEquals(russian, SubtitleCharset.decode(russian.bytesIn("windows-1251"), null))
    }

    @Test
    fun otherLanguagesUseTheirCodepage() {
        val greek = "Καλημέρα, τι κάνεις;\n"
        assertEquals(greek, SubtitleCharset.decode(greek.bytesIn("windows-1253"), "el"))
        val polish = "Zjadłem jabłko, a potem poszedłem spać. Będę tam za chwilę.\n"
        assertEquals(polish, SubtitleCharset.decode(polish.bytesIn("windows-1250"), "pl"))
        val hebrew = "שלום, מה שלומך?\n"
        assertEquals(hebrew, SubtitleCharset.decode(hebrew.bytesIn("windows-1255"), "he"))
    }

    @Test
    fun westernTextStaysWindows1252() {
        assertEquals(french, SubtitleCharset.decode(french.bytesIn("windows-1252"), "fr"))
        assertEquals(french, SubtitleCharset.decode(french.bytesIn("windows-1252"), null))
        assertEquals(Charset.forName("windows-1252"), SubtitleCharset.legacyCharset(byteArrayOf(0x41), "en"))
    }
}
