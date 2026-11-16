package com.example.focusflow.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.focusflow.domain.CalEvent
import java.time.LocalDate
import java.time.ZoneId

/** Read-only access to the phone's calendars. Nothing is written, and nothing leaves the phone. */
object CalendarReader {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun eventsOn(ctx: Context, date: LocalDate): List<CalEvent> = eventsBetween(ctx, date, date.plusDays(1))

    /** Events that overlap the days from [from] up to, but not including, [toExclusive]. */
    fun eventsBetween(ctx: Context, from: LocalDate, toExclusive: LocalDate): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY
        )
        val out = ArrayList<CalEvent>()
        try {
            CalendarContract.Instances.query(ctx.contentResolver, projection, start, end)?.use { c ->
                while (c.moveToNext()) {
                    out.add(CalEvent(c.getString(0) ?: "(No title)", c.getLong(1), c.getLong(2), c.getInt(3) == 1))
                }
            }
        } catch (e: Exception) {
            // Calendar unavailable or permission revoked: show nothing.
        }
        return out.sortedBy { it.startMs }
    }
}
