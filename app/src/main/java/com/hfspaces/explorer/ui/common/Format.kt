package com.hfspaces.explorer.ui.common

import android.text.format.DateUtils
import java.text.DateFormat
import java.time.Instant
import java.util.Date

private fun parseInstant(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/** "3 days ago" for one of the Hub's ISO timestamps; null when it can't be read. */
fun relativeTime(iso: String?, now: Long = System.currentTimeMillis()): String? = parseInstant(iso)?.let { then ->
    DateUtils.getRelativeTimeSpanString(then.coerceAtMost(now), now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** "12 Nov 2026" (in the device's format) for one of the Hub's ISO timestamps. */
fun mediumDate(iso: String?): String? = parseInstant(iso)?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }

/** 1234 -> "1.2k", 2500000 -> "2.5M"; small numbers stay as they are. */
fun compactCount(count: Int): String = when {
    count >= 1_000_000 -> trimZero(count / 1_000_000.0) + "M"
    count >= 1_000 -> trimZero(count / 1_000.0) + "k"
    else -> count.toString()
}

private fun trimZero(value: Double): String {
    // Round down, so 999_999 doesn't claim to be "1000.0k".
    val tenths = (value * 10).toLong()
    return if (tenths % 10 == 0L || tenths >= 1000) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
