package com.autholau.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.autholau.model.Event
import com.autholau.storage.Prefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CalendarSync {

    // SYNC_DATA1 stores our Autholau event.id so we can find calendar entries later
    private const val AUTHOLAU_ID_FIELD = CalendarContract.Events.SYNC_DATA1

    fun isEnabled(ctx: Context) = Prefs.calendarEnabled(ctx)

    // ── Public API ────────────────────────────────────────────────────────────

    fun upsertEvent(ctx: Context, event: Event) {
        if (!isEnabled(ctx)) return
        try {
            val calId = resolveCalendarId(ctx) ?: return
            val existingRowId = findRowId(ctx, event.id)
            if (existingRowId != null) {
                update(ctx, existingRowId, event)
            } else {
                insert(ctx, calId, event)
            }
        } catch (_: Exception) {}
    }

    fun deleteEvent(ctx: Context, event: Event) {
        if (!isEnabled(ctx)) return
        try {
            val rowId = findRowId(ctx, event.id) ?: return
            ctx.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "_id = ?",
                arrayOf(rowId.toString())
            )
        } catch (_: Exception) {}
    }

    fun syncAll(ctx: Context, events: List<Event>) {
        if (!isEnabled(ctx)) return
        try {
            val calId = resolveCalendarId(ctx) ?: return

            // Upsert all current events
            for (event in events) {
                val existingRowId = findRowId(ctx, event.id)
                if (existingRowId != null) {
                    update(ctx, existingRowId, event)
                } else {
                    insert(ctx, calId, event)
                }
            }

            // Delete calendar entries whose Autholau ID is no longer in the list
            val currentIds = events.map { it.id }.toSet()
            val staleRowIds = findAllAutholauRowIds(ctx, currentIds)
            for (rowId in staleRowIds) {
                ctx.contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "_id = ?",
                    arrayOf(rowId.toString())
                )
            }
        } catch (_: Exception) {}
    }

    // ── Calendar ID resolution ─────────────────────────────────────────────

    /** Returns saved calendar ID, or auto-detects the primary calendar. */
    fun resolveCalendarId(ctx: Context): Long? {
        val saved = Prefs.calendarId(ctx)
        if (saved != -1L) return saved
        return findPrimaryCalendarId(ctx)
    }

    /** Queries CalendarContract for non-local calendars. Returns list of (id, displayName). */
    fun listCalendars(ctx: Context): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
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
                    // Skip local-only calendars
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
        val fmt  = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val zone = ZoneId.systemDefault()

        val (beginMs, endMs, allDay) = if (event.time != null) {
            val dt    = LocalDateTime.parse("${event.date}T${event.time}")
            val begin = dt.atZone(zone).toInstant().toEpochMilli()
            Triple(begin, begin + 60 * 60 * 1000L, false) // 1 hour duration
        } else {
            val date  = LocalDate.parse(event.date, fmt)
            val begin = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end   = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Triple(begin, end, true)
        }

        return ContentValues().apply {
            if (calendarId != null) put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE,       event.title)
            put(CalendarContract.Events.DTSTART,     beginMs)
            put(CalendarContract.Events.DTEND,       endMs)
            put(CalendarContract.Events.ALL_DAY,     if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(AUTHOLAU_ID_FIELD,                   event.id)
        }
    }

    private fun insert(ctx: Context, calendarId: Long, event: Event) {
        val values = eventToValues(event, calendarId)
        ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
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

    private fun findRowId(ctx: Context, autholauId: String): Long? {
        val projection = arrayOf(CalendarContract.Events._ID)
        ctx.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "$AUTHOLAU_ID_FIELD = ?",
            arrayOf(autholauId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    /** Returns row IDs of calendar entries whose Autholau ID is NOT in [keepIds]. */
    private fun findAllAutholauRowIds(ctx: Context, keepIds: Set<String>): List<Long> {
        val result = mutableListOf<Long>()
        val projection = arrayOf(CalendarContract.Events._ID, AUTHOLAU_ID_FIELD)
        // Only look at entries we created (SYNC_DATA1 non-null)
        ctx.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "$AUTHOLAU_ID_FIELD IS NOT NULL AND $AUTHOLAU_ID_FIELD != ''",
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val rowId      = cursor.getLong(0)
                val autholauId = cursor.getString(1) ?: continue
                if (autholauId !in keepIds) result.add(rowId)
            }
        }
        return result
    }
}
