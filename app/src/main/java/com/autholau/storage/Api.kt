package com.autholau.storage

import com.autholau.model.Event
import com.autholau.model.ShoppingItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object Api {

    var baseUrl: String = Prefs.DEFAULT_URL
    var token: String?  = null

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun conn(path: String, method: String): HttpURLConnection {
        val url  = URL("${baseUrl.trimEnd('/')}/$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod       = method
        conn.connectTimeout      = 10_000
        conn.readTimeout         = 10_000
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept",       "application/json")
        token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        return conn
    }

    private fun get(path: String): Pair<Int, String> {
        val c = conn(path, "GET")
        return try {
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            code to body
        } finally { c.disconnect() }
    }

    private fun post(path: String, body: JSONObject): Pair<Int, String> {
        val c = conn(path, "POST")
        c.doOutput = true
        return try {
            OutputStreamWriter(c.outputStream).use { it.write(body.toString()) }
            val code = c.responseCode
            val resp = (if (code < 400) c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            code to resp
        } finally { c.disconnect() }
    }

    private fun put(path: String, body: JSONObject): Pair<Int, String> {
        val c = conn(path, "PUT")
        c.doOutput = true
        return try {
            OutputStreamWriter(c.outputStream).use { it.write(body.toString()) }
            val code = c.responseCode
            val resp = (if (code < 400) c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            code to resp
        } finally { c.disconnect() }
    }

    private fun delete(path: String): Int {
        val c = conn(path, "DELETE")
        return try { c.responseCode } finally { c.disconnect() }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun healthCheck(): Boolean = try {
        val (code, _) = get("health")
        code == 200
    } catch (_: Exception) { false }

    /** Returns JWT token string on success, null on failure. */
    fun login(password: String): String? = try {
        val (code, body) = post("auth/login", JSONObject().put("password", password))
        if (code == 200) JSONObject(body).optString("token", null) else null
    } catch (_: Exception) { null }

    // ── Events ────────────────────────────────────────────────────────────────

    fun getEvents(): List<Event>? = try {
        val (code, body) = get("events")
        if (code == 200) parseEvents(body) else null
    } catch (_: Exception) { null }

    fun createEvent(e: Event): Event? = try {
        val (code, body) = post("events", eventToJson(e))
        if (code == 201 || code == 200) parseEvent(JSONObject(body)) else null
    } catch (_: Exception) { null }

    fun updateEvent(e: Event): Event? = try {
        val (code, body) = put("events/${e.id}", eventToJson(e))
        if (code == 200) parseEvent(JSONObject(body)) else null
    } catch (_: Exception) { null }

    fun deleteEvent(id: String): Boolean = try {
        delete("events/$id") in 200..299
    } catch (_: Exception) { false }

    // ── Shopping ──────────────────────────────────────────────────────────────

    fun getShopping(): List<ShoppingItem>? = try {
        val (code, body) = get("shopping")
        if (code == 200) parseShopping(body) else null
    } catch (_: Exception) { null }

    fun createShoppingItem(s: ShoppingItem): ShoppingItem? = try {
        val (code, body) = post("shopping", shoppingToJson(s))
        if (code == 201 || code == 200) parseShoppingItem(JSONObject(body)) else null
    } catch (_: Exception) { null }

    fun updateShoppingItem(s: ShoppingItem): ShoppingItem? = try {
        val (code, body) = put("shopping/${s.id}", shoppingToJson(s))
        if (code == 200) parseShoppingItem(JSONObject(body)) else null
    } catch (_: Exception) { null }

    fun deleteShoppingItem(id: String): Boolean = try {
        delete("shopping/$id") in 200..299
    } catch (_: Exception) { false }

    // ── Categories ────────────────────────────────────────────────────────────

    fun getCategories(): List<String>? = try {
        val (code, body) = get("categories")
        if (code == 200) {
            val arr = org.json.JSONArray(body)
            (0 until arr.length()).map { arr.getString(it) }
        } else null
    } catch (_: Exception) { null }

    fun createCategory(name: String): Boolean = try {
        val (code, _) = post("categories", JSONObject().put("name", name))
        code == 201
    } catch (_: Exception) { false }

    fun deleteCategory(name: String): Boolean = try {
        val encoded = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        delete("categories/$encoded") in 200..299
    } catch (_: Exception) { false }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun eventToJson(e: Event) = JSONObject().apply {
        put("id",        e.id)
        put("title",     e.title)
        put("date",      e.date)
        if (e.time != null) put("time", e.time)
        put("notify",    e.notify)
        put("updatedAt", e.updatedAt)
    }

    private fun parseEvent(o: JSONObject) = Event(
        id        = o.getString("id"),
        title     = o.getString("title"),
        date      = o.getString("date"),
        time      = o.optString("time", null).takeIf { !it.isNullOrEmpty() },
        notify    = o.optBoolean("notify", true),
        updatedAt = o.optLong("updatedAt", 0L)
    )

    private fun parseEvents(body: String): List<Event> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { parseEvent(arr.getJSONObject(it)) }
    }

    private fun shoppingToJson(s: ShoppingItem) = JSONObject().apply {
        put("id",        s.id)
        put("name",      s.name)
        put("checked",   s.checked)
        if (s.category != null) put("category", s.category)
        put("store",     s.store)
        put("updatedAt", s.updatedAt)
    }

    private fun parseShoppingItem(o: JSONObject) = ShoppingItem(
        id        = o.getString("id"),
        name      = o.getString("name"),
        checked   = o.optBoolean("checked", false),
        category  = o.optString("category", null).takeIf { !it.isNullOrEmpty() },
        store     = o.optString("store", "Leclerc").ifEmpty { "Leclerc" },
        updatedAt = o.optLong("updatedAt", 0L)
    )

    private fun parseShopping(body: String): List<ShoppingItem> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { parseShoppingItem(arr.getJSONObject(it)) }
    }
}
