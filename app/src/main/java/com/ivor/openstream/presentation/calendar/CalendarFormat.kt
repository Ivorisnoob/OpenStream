package com.ivor.openstream.presentation.calendar

import android.text.format.DateFormat
import android.text.format.DateUtils
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Dates spoken by the system, so every language gets its own grammar for "Tomorrow" and "In 3 days"
 * without a string per plural form.
 */
object CalendarFormat {
    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** "Today", "Tomorrow", "In 3 days", "2 days ago". */
    fun relative(date: LocalDate, today: LocalDate): String =
        DateUtils.getRelativeTimeSpanString(millis(date), millis(today), DateUtils.DAY_IN_MILLIS, 0).toString()

    /** "Mon, Nov 2", in the device's own order. */
    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale).format(date)

    /** "Sunday, November 2, 2026", for screen readers. */
    fun fullDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

    fun weekday(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE", locale).format(date)

    /** Today and tomorrow by name, later days as a date. */
    fun dayHeading(date: LocalDate, today: LocalDate): String =
        if (date.isAfter(today.plusDays(1))) shortDate(date) else relative(date, today)
}
