package com.autholau.calendar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import com.autholau.model.Event
import com.autholau.storage.Prefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CalendarSync {

    fun isEnabled(ctx: Context) = Prefs.calendarEnabled(ctx)

    private fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    // ── Public API ────────────────────────────────────────────────────────────
    // All methods are safe to call from any thread.

    fun upsertEvent(ctx: Context, event: Event) {
        if (!isEnabled(ctx) || !hasPermission(ctx)) return
        try {
            val calId = resolveCalendarId(ctx) ?: return
            val rowMap = Prefs.calendarRowMap(ctx)
            val existingRowId = rowMap[event.id]
            if (existingRowId != null) {
                update(ctx, existingRowId, event)
            } else {
                val newRowId = insert(ctx, calId, event)
                if (newRowId != null) {
                    rowMap[event.id] = newRowId
                    Prefs.saveCalendarRowMap(ctx, rowMap)
                }
            }
        } catch (_: Exception) {}
    }

    fun deleteEvent(ctx: Context, event: Event) {
        if (!isEnabled(ctx) || !hasPermission(ctx)) return
        try {
            val rowMap = Prefs.calendarRowMap(ctx)
            val rowId = rowMap[event.id] ?: return
            ctx.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "_id = ?",
                arrayOf(rowId.toString())
            )
            rowMap.remove(event.id)
            Prefs.saveCalendarRowMap(ctx, rowMap)
        } catch (_: Exception) {}
    }

    fun syncAll(ctx: Context, events: List<Event>) {
        if (!isEnabled(ctx) || !hasPermission(ctx)) return
        try {
            val calId = resolveCalendarId(ctx) ?: return
            val rowMap = Prefs.calendarRowMap(ctx)

            // Upsert all current events
            for (event in events) {
                val existingRowId = rowMap[event.id]
                if (existingRowId != null) {
                    update(ctx, existingRowId, event)
                } else {
                    val newRowId = insert(ctx, calId, event)
                    if (newRowId != null) rowMap[event.id] = newRowId
                }
            }

            // Delete calendar entries whose Autholau ID is no longer in the list
            val currentIds = events.map { it.id }.toSet()
            val staleIds = rowMap.keys.filter { it !in currentIds }
            for (autholauId in staleIds) {
                val rowId = rowMap[autholauId] ?: continue
                ctx.contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "_id = ?",
                    arrayOf(rowId.toString())
                )
                rowMap.remove(autholauId)
            }

            Prefs.saveCalendarRowMap(ctx, rowMap)
        } catch (_: Exception) {}
    }

    // ── Calendar ID resolution ─────────────────────────────────────────────

    fun resolveCalendarId(ctx: Context): Long? {
        val saved = Prefs.calendarId(ctx)
        if (saved != -1L) return saved
        return findPrimaryCalendarId(ctx)
    }

    fun listCalendars(ctx: Context): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
        if (!hasPermission(ctx)) return result
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE
            )
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id   = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    val type = cursor.getString(2) ?: ""
                    if (type == CalendarContract.ACCOUNT_TYPE_LOCAL) continue
                    result.add(Pair(id, name))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun findPrimaryCalendarId(ctx: Context): Long? =
        listCalendars(ctx).firstOrNull()?.first

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun eventToValues(event: Event, calendarId: Long? = null): ContentValues {
        val zone = ZoneId.systemDefault()

        val (beginMs, endMs, allDay) = if (event.time != null) {
            val dt    = LocalDateTime.parse("${event.date}T${event.time}")
            val begin = dt.atZone(zone).toInstant().toEpochMilli()
            Triple(begin, begin + 60 * 60 * 1000L, false)
        } else {
            val date  = LocalDate.parse(event.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val begin = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end   = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Triple(begin, end, true)
        }

        return ContentValues().apply {
            if (calendarId != null) put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE,          event.title)
            put(CalendarContract.Events.DTSTART,        beginMs)
            put(CalendarContract.Events.DTEND,          endMs)
            put(CalendarContract.Events.ALL_DAY,        if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
    }

    /** Returns the new calendar row ID, or null on failure. */
    private fun insert(ctx: Context, calendarId: Long, event: Event): Long? {
        val values = eventToValues(event, calendarId)
        val uri: Uri? = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull()
    }

    private fun update(ctx: Context, rowId: Long, event: Event) {
        val values = eventToValues(event)
        ctx.contentResolver.update(
            CalendarContract.Events.CONTENT_URI,
            values,
            "_id = ?",
            arrayOf(rowId.toString())
        )
    }
}
