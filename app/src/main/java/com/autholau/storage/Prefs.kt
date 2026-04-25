package com.autholau.storage

import android.content.Context
import android.content.SharedPreferences
import com.autholau.model.Event
import com.autholau.model.ShoppingItem
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    const val NAME           = "autholau"
    const val SERVER_URL     = "server_url"
    const val TOKEN          = "token"
    const val FIRST_LAUNCH   = "first_launch"
    const val NOTIF_LEAD     = "notif_lead_days"
    const val EVENTS_CACHE   = "events_cache"
    const val SHOPPING_CACHE = "shopping_cache"

    const val DEFAULT_URL    = "https://famille.thonis.fr"
    const val DEFAULT_LEAD   = 7

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
                    updatedAt = o.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
