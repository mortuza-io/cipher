package com.rork.cipher.ui

import java.util.Calendar
import java.util.Locale

private fun calendarOf(millis: Long): Calendar =
    Calendar.getInstance().apply { timeInMillis = millis }

fun clockTime(millis: Long): String {
    val cal = calendarOf(millis)
    return String.format(
        Locale.US,
        "%02d:%02d",
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE)
    )
}

/** Relative label for chat rows: time today, "Yesterday", then weekday. */
fun relativeStamp(millis: Long): String {
    if (millis <= 0L) return ""
    val now = Calendar.getInstance()
    val then = calendarOf(millis)
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> clockTime(millis)
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && dayDiff in 2..6 -> then.getDisplayName(
            Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US
        ) ?: ""
        else -> String.format(
            Locale.US,
            "%02d/%02d",
            then.get(Calendar.DAY_OF_MONTH),
            then.get(Calendar.MONTH) + 1
        )
    }
}

/**
 * Day separator label inside a conversation. Older days name themselves in
 * full — a bare weekday would be ambiguous once a thread is months long.
 */
fun daySeparator(millis: Long): String {
    val now = Calendar.getInstance()
    val then = calendarOf(millis)
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    val weekday = then.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.US) ?: ""
    val month = then.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US) ?: ""
    val day = then.get(Calendar.DAY_OF_MONTH)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && dayDiff in 2..6 -> weekday
        sameYear -> "$weekday, $day $month"
        else -> "$day $month ${then.get(Calendar.YEAR)}"
    }
}

/** Full stamp used when a message is inspected: `Today 14:32`, `12 March 09:04`. */
fun exactStamp(millis: Long): String =
    "${daySeparator(millis)} ${clockTime(millis)}"

fun dayBucket(millis: Long): Int {
    val cal = calendarOf(millis)
    return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}

fun burnLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes < 1440 -> "${minutes / 60}h"
    else -> "${minutes / 1440}d"
}

/** Compact countdown for a self-destructing message, e.g. `42s`, `9m`, `3h`. */
fun burnRemaining(expiresAt: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((expiresAt - now) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        seconds < 86_400L -> "${seconds / 3600L}h"
        else -> "${seconds / 86_400L}d"
    }
}
