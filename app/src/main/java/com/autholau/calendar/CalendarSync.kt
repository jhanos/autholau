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
import java.time.format.DateTimeParseException

object CalendarSync {

    // ── Debug log ─────────────────────────────────────────────────────────────

    private val logs = mutableListOf<String>()
    private const val MAX_LOGS = 200

    fun getLogs(): List<String> = synchronized(logs) { logs.toList() }

    fun clearLogs() = synchronized(logs) { logs.clear() }

    private fun log(msg: String) {
        val ts   = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val date = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"))
        val line = "[$date $ts] $msg"
        android.util.Log.d("CalendarSync", line)
        synchronized(logs) {
            logs.add(line)
            if (logs.size > MAX_LOGS) logs.removeAt(0)
        }
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    fun isEnabled(ctx: Context) = Prefs.calendarEnabled(ctx)

    private fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun guardCheck(ctx: Context): Boolean {
        if (!isEnabled(ctx)) {
            log("SKIP — sync désactivé dans les paramètres")
            return false
        }
        val readOk  = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR)  == PackageManager.PERMISSION_GRANTED
        val writeOk = ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        log("Permissions — READ_CALENDAR=${if (readOk) "OK" else "REFUSÉE"}, WRITE_CALENDAR=${if (writeOk) "OK" else "REFUSÉE"}")
        if (!readOk || !writeOk) {
            log("SKIP — permission(s) manquante(s)")
            return false
        }
        return true
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun upsertEvent(ctx: Context, event: Event) {
        log("upsertEvent — \"${event.title}\" (id=${event.id})")
        if (!guardCheck(ctx)) return
        try {
            val calId = resolveCalendarId(ctx)
            if (calId == null) {
                log("ERREUR — aucun calendrier trouvé")
                return
            }
            log("Calendrier cible : id=$calId")
            val rowMap = Prefs.calendarRowMap(ctx)
            val existingRowId = rowMap[event.id]
            if (existingRowId != null) {
                log("Mise à jour entrée existante (rowId=$existingRowId)")
                update(ctx, existingRowId, event)
                log("OK — mis à jour")
            } else {
                log("Insertion nouvelle entrée")
                val newRowId = insert(ctx, calId, event)
                if (newRowId != null) {
                    rowMap[event.id] = newRowId
                    Prefs.saveCalendarRowMap(ctx, rowMap)
                    log("OK — inséré (rowId=$newRowId)")
                } else {
                    log("ERREUR — insert a retourné null (URI invalide)")
                }
            }
        } catch (e: Exception) {
            log("EXCEPTION — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun deleteEvent(ctx: Context, event: Event) {
        log("deleteEvent — \"${event.title}\" (id=${event.id})")
        if (!guardCheck(ctx)) return
        try {
            val rowMap = Prefs.calendarRowMap(ctx)
            val rowId = rowMap[event.id]
            if (rowId == null) {
                log("SKIP — aucune entrée connue pour cet événement")
                return
            }
            val deleted = ctx.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "_id = ?",
                arrayOf(rowId.toString())
            )
            rowMap.remove(event.id)
            Prefs.saveCalendarRowMap(ctx, rowMap)
            log("OK — $deleted ligne(s) supprimée(s) (rowId=$rowId)")
        } catch (e: Exception) {
            log("EXCEPTION — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun syncAll(ctx: Context, events: List<Event>) {
        log("syncAll — ${events.size} événement(s)")
        if (!guardCheck(ctx)) return
        try {
            val calId = resolveCalendarId(ctx)
            if (calId == null) {
                log("ERREUR — aucun calendrier trouvé")
                return
            }
            log("Calendrier cible : id=$calId")
            val rowMap = Prefs.calendarRowMap(ctx)

            for (event in events) {
                val existingRowId = rowMap[event.id]
                if (existingRowId != null) {
                    log("Mise à jour \"${event.title}\" (rowId=$existingRowId)")
                    update(ctx, existingRowId, event)
                } else {
                    log("Insertion \"${event.title}\"")
                    val newRowId = insert(ctx, calId, event)
                    if (newRowId != null) {
                        rowMap[event.id] = newRowId
                        log("OK — inséré (rowId=$newRowId)")
                    } else {
                        log("ERREUR — insert null pour \"${event.title}\"")
                    }
                }
            }

            val currentIds = events.map { it.id }.toSet()
            val staleIds = rowMap.keys.filter { it !in currentIds }
            if (staleIds.isNotEmpty()) {
                log("Suppression de ${staleIds.size} entrée(s) obsolète(s)")
            }
            for (autholauId in staleIds) {
                val rowId = rowMap[autholauId] ?: continue
                ctx.contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "_id = ?",
                    arrayOf(rowId.toString())
                )
                rowMap.remove(autholauId)
                log("Supprimé rowId=$rowId")
            }

            Prefs.saveCalendarRowMap(ctx, rowMap)
            log("syncAll terminé")
        } catch (e: Exception) {
            log("EXCEPTION — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ── Calendar ID resolution ─────────────────────────────────────────────

    fun resolveCalendarId(ctx: Context): Long? {
        val saved = Prefs.calendarId(ctx)
        if (saved != -1L) {
            log("ID calendrier sauvegardé : $saved")
            return saved
        }
        val primary = findPrimaryCalendarId(ctx)
        if (primary != null) {
            log("ID calendrier auto-détecté : $primary")
        } else {
            log("ERREUR — findPrimaryCalendarId a retourné null (aucun calendrier disponible)")
        }
        return primary
    }

    fun listCalendars(ctx: Context): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
        val readOk  = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR)  == PackageManager.PERMISSION_GRANTED
        val writeOk = ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!readOk || !writeOk) {
            log("listCalendars — permission manquante (READ=$readOk, WRITE=$writeOk), abandon")
            return result
        }
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE
            )
            val cursor = ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, null, null, null
            )
            if (cursor == null) {
                log("listCalendars — query a retourné null (ContentResolver indisponible ?)")
                return result
            }
            val total = cursor.count
            log("listCalendars — $total ligne(s) trouvée(s) dans CalendarContract")
            cursor.use {
                while (it.moveToNext()) {
                    val id   = it.getLong(0)
                    val name = it.getString(1) ?: "<sans nom>"
                    val type = it.getString(2) ?: "<type inconnu>"
                    // Keep all calendars — local calendars are valid too
                    log("  → id=$id, nom=\"$name\", type=$type → GARDÉ")
                    result.add(Pair(id, name))
                }
            }
            if (result.isEmpty()) {
                log("listCalendars — 0 calendrier retourné (appareil sans agenda configuré ?)")
            } else {
                log("listCalendars — ${result.size} calendrier(s) disponible(s)")
            }
        } catch (e: Exception) {
            log("listCalendars EXCEPTION — ${e.javaClass.simpleName}: ${e.message}")
        }
        return result
    }

    fun findPrimaryCalendarId(ctx: Context): Long? =
        listCalendars(ctx).firstOrNull()?.first

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun eventToValues(event: Event, calendarId: Long? = null): ContentValues {
        val zone = ZoneId.systemDefault()

        val (beginMs, endMs, allDay) = if (event.time != null) {
            try {
                val dt    = LocalDateTime.parse("${event.date}T${event.time}")
                val begin = dt.atZone(zone).toInstant().toEpochMilli()
                Triple(begin, begin + 60 * 60 * 1000L, false)
            } catch (e: DateTimeParseException) {
                log("ERREUR parsing heure \"${event.time}\" : ${e.message}")
                val date  = LocalDate.parse(event.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val begin = date.atStartOfDay(zone).toInstant().toEpochMilli()
                Triple(begin, begin + 24 * 60 * 60 * 1000L, true)
            }
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

    private fun insert(ctx: Context, calendarId: Long, event: Event): Long? {
        val values = eventToValues(event, calendarId)
        val uri: Uri? = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull()
    }

    private fun update(ctx: Context, rowId: Long, event: Event) {
        val values = eventToValues(event)
        val rows = ctx.contentResolver.update(
            CalendarContract.Events.CONTENT_URI,
            values,
            "_id = ?",
            arrayOf(rowId.toString())
        )
        log("update — $rows ligne(s) modifiée(s)")
    }
}
