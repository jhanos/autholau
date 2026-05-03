package com.autholau.storage

import android.content.Context
import android.content.SharedPreferences
import com.autholau.model.Event
import com.autholau.model.RecurringItem
import com.autholau.model.ShoppingItem
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    const val NAME              = "autholau"
    const val SERVER_URL        = "server_url"
    const val TOKEN             = "token"
    const val FIRST_LAUNCH      = "first_launch"
    const val NOTIF_LEAD        = "notif_lead_days"
    const val EVENTS_CACHE      = "events_cache"
    const val SHOPPING_CACHE    = "shopping_cache"
    const val CATEGORIES_CACHE  = "categories_cache"
    const val CALENDAR_ENABLED  = "calendar_enabled"
    const val CALENDAR_ID       = "calendar_id"
    const val CALENDAR_ROW_MAP  = "calendar_row_map"
    const val COURSE_MODE       = "course_mode"
    const val RECURRING_ITEMS   = "recurring_items"

    const val DEFAULT_URL  = "https://famille.thonis.fr"
    const val DEFAULT_LEAD = 7

    val DEFAULT_CATEGORIES = listOf(
        "Maison", "Féculents", "Condiments", "Petit Dej/Gouter",
        "Viandes/Poissons", "Laitage", "Fruits/Légumes",
        "Hygiène/Beauté", "Surgelés"
    )

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun serverUrl(ctx: Context): String =
        get(ctx).getString(SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun token(ctx: Context): String? =
        get(ctx).getString(TOKEN, null)

    fun isFirstLaunch(ctx: Context): Boolean =
        get(ctx).getBoolean(FIRST_LAUNCH, true)

    fun notifLeadDays(ctx: Context): Int =
        get(ctx).getInt(NOTIF_LEAD, DEFAULT_LEAD)

    fun saveServerUrl(ctx: Context, url: String) =
        get(ctx).edit().putString(SERVER_URL, url).apply()

    fun saveToken(ctx: Context, token: String) =
        get(ctx).edit().putString(TOKEN, token).apply()

    fun clearToken(ctx: Context) =
        get(ctx).edit().remove(TOKEN).apply()

    fun markFirstLaunchDone(ctx: Context) =
        get(ctx).edit().putBoolean(FIRST_LAUNCH, false).apply()

    fun saveNotifLead(ctx: Context, days: Int) =
        get(ctx).edit().putInt(NOTIF_LEAD, days).apply()

    fun calendarEnabled(ctx: Context): Boolean =
        get(ctx).getBoolean(CALENDAR_ENABLED, false)

    fun saveCalendarEnabled(ctx: Context, enabled: Boolean) =
        get(ctx).edit().putBoolean(CALENDAR_ENABLED, enabled).apply()

    fun calendarId(ctx: Context): Long =
        get(ctx).getLong(CALENDAR_ID, -1L)

    fun saveCalendarId(ctx: Context, id: Long) =
        get(ctx).edit().putLong(CALENDAR_ID, id).apply()

    // ── Calendar row map (autholauId → device calendar row ID) ───────────────

    fun calendarRowMap(ctx: Context): MutableMap<String, Long> {
        val raw = get(ctx).getString(CALENDAR_ROW_MAP, null) ?: return mutableMapOf()
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            obj.keys().forEach { key -> map[key] = obj.getLong(key) }
            map
        } catch (_: Exception) { mutableMapOf() }
    }

    fun saveCalendarRowMap(ctx: Context, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        get(ctx).edit().putString(CALENDAR_ROW_MAP, obj.toString()).apply()
    }

    fun courseMode(ctx: Context): Boolean =
        get(ctx).getBoolean(COURSE_MODE, false)

    fun saveCourseMode(ctx: Context, enabled: Boolean) =
        get(ctx).edit().putBoolean(COURSE_MODE, enabled).apply()

    // ── Events cache ──────────────────────────────────────────────────────────

    fun saveEvents(ctx: Context, events: List<Event>) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("id",        e.id)
                put("title",     e.title)
                put("date",      e.date)
                if (e.time != null) put("time", e.time)
                put("notify",    e.notify)
                put("updatedAt", e.updatedAt)
            })
        }
        get(ctx).edit().putString(EVENTS_CACHE, arr.toString()).apply()
    }

    fun loadEvents(ctx: Context): List<Event> {
        val raw = get(ctx).getString(EVENTS_CACHE, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Event(
                    id        = o.getString("id"),
                    title     = o.getString("title"),
                    date      = o.getString("date"),
                    time      = o.optString("time", null).takeIf { !it.isNullOrEmpty() },
                    notify    = o.optBoolean("notify", true),
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Shopping cache ────────────────────────────────────────────────────────

    fun saveShopping(ctx: Context, items: List<ShoppingItem>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(JSONObject().apply {
                put("id",        s.id)
                put("name",      s.name)
                put("checked",   s.checked)
                put("planned",   s.planned)
                if (s.category != null) put("category", s.category)
                put("store",     s.store)
                put("updatedAt", s.updatedAt)
            })
        }
        get(ctx).edit().putString(SHOPPING_CACHE, arr.toString()).apply()
    }

    fun loadShopping(ctx: Context): List<ShoppingItem> {
        val raw = get(ctx).getString(SHOPPING_CACHE, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ShoppingItem(
                    id        = o.getString("id"),
                    name      = o.getString("name"),
                    checked   = o.optBoolean("checked", false),
                    planned   = o.optBoolean("planned", false),
                    category  = o.optString("category", null).takeIf { !it.isNullOrEmpty() },
                    store     = o.optString("store", "Leclerc").ifEmpty { "Leclerc" },
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Categories cache ──────────────────────────────────────────────────────

    fun saveCategories(ctx: Context, cats: List<String>) {
        val arr = JSONArray()
        cats.forEach { arr.put(it) }
        get(ctx).edit().putString(CATEGORIES_CACHE, arr.toString()).apply()
    }

    fun loadCategories(ctx: Context): List<String> {
        val raw = get(ctx).getString(CATEGORIES_CACHE, null) ?: return DEFAULT_CATEGORIES
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { DEFAULT_CATEGORIES }
    }

    // ── Recurring items ───────────────────────────────────────────────────────

    fun saveRecurring(ctx: Context, items: List<RecurringItem>) {
        val arr = JSONArray()
        items.forEach { r ->
            arr.put(JSONObject().apply {
                put("id",          r.id)
                put("name",        r.name)
                if (r.category != null) put("category", r.category)
                val storesArr = JSONArray()
                r.stores.forEach { storesArr.put(it) }
                put("stores",      storesArr)
                put("periodWeeks", r.periodWeeks)
                put("lastBought",  r.lastBought)
            })
        }
        get(ctx).edit().putString(RECURRING_ITEMS, arr.toString()).apply()
    }

    fun loadRecurring(ctx: Context): List<RecurringItem> {
        val raw = get(ctx).getString(RECURRING_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o      = arr.getJSONObject(i)
                val sArr   = o.getJSONArray("stores")
                val stores = (0 until sArr.length()).map { sArr.getString(it) }
                RecurringItem(
                    id          = o.optString("id", java.util.UUID.randomUUID().toString()),
                    name        = o.getString("name"),
                    category    = o.optString("category", null).takeIf { !it.isNullOrEmpty() },
                    stores      = stores,
                    periodWeeks = o.getInt("periodWeeks"),
                    lastBought  = o.optLong("lastBought", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Updates lastBought for the matching entry, saves, and returns the updated item (or null if not found). */
    fun updateRecurringLastBought(ctx: Context, name: String, category: String?, ts: Long): RecurringItem? {
        val list = loadRecurring(ctx).toMutableList()
        val idx  = list.indexOfFirst {
            it.name.equals(name, ignoreCase = true) && it.category == category
        }
        if (idx < 0) return null
        val updated = list[idx].copy(lastBought = ts)
        list[idx] = updated
        saveRecurring(ctx, list)
        return updated
    }
}
