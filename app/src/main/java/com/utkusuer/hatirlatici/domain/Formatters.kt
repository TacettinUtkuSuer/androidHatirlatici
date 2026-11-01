package com.utkusuer.hatirlatici.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Formatters {
    val TR: Locale = Locale.forLanguageTag("tr-TR")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", TR)

    private val monthYear = DateTimeFormatter.ofPattern("LLLL yyyy", TR)
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", TR)

    fun shortDate(date: LocalDate): String = shortDate.format(date)

    fun monthYear(month: YearMonth): String =
        monthYear.format(month).replaceFirstChar { it.titlecase(TR) }

    fun dateTime(millis: Long): String =
        dateTime.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun money(amountMinor: Long, currency: String): String {
        val f = NumberFormat.getCurrencyInstance(TR)
        runCatching { f.currency = Currency.getInstance(currency) }
        return f.format(amountMinor / 100.0)
    }

    fun minuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
