package com.autholau.ui

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.autholau.R
import com.autholau.model.Event
import com.autholau.model.RecurringItem
import com.autholau.model.ShoppingItem
import com.autholau.calendar.CalendarSync
import com.autholau.notifications.NotificationScheduler
import com.autholau.storage.Api
import com.autholau.storage.Prefs
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    private enum class Section { EVENTS, LISTE, COURSE, LECLERC, GRAND_FRAIS, AUTRE }

    private var section = Section.EVENTS

    // Views
    private lateinit var drawerPanel:      View
    private lateinit var scrim:            View
    private lateinit var tvTitle:          TextView
    private lateinit var listView:         ListView
    private lateinit var tvEmpty:          TextView
    private lateinit var rowShoppingInput: View
    private lateinit var etNewItem:        EditText
    private lateinit var btnAdd:           ImageButton
    private lateinit var btnClearChecked:  ImageButton
    private lateinit var btnRefresh:       ImageButton

    // Data
    private var events:     List<Event>         = emptyList()
    private var shopping:   List<ShoppingItem>  = emptyList()
    private var categories: List<String>        = emptyList()
    private var recurring:  List<RecurringItem> = emptyList()
    private var searchQuery: String             = ""

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)

        Api.baseUrl = Prefs.serverUrl(this)
        Api.token   = Prefs.token(this)

        drawerPanel      = findViewById(R.id.drawerPanel)
        scrim            = findViewById(R.id.scrim)
        tvTitle          = findViewById(R.id.tvTitle)
        listView         = findViewById(R.id.listView)
        tvEmpty          = findViewById(R.id.tvEmpty)
        rowShoppingInput = findViewById(R.id.rowShoppingInput)
        etNewItem        = findViewById(R.id.etNewItem)
        btnAdd           = findViewById(R.id.btnAdd)
        btnClearChecked  = findViewById(R.id.btnClearChecked)

        findViewById<ImageButton>(R.id.btnDrawer).setOnClickListener { toggleDrawer() }
        btnRefresh = findViewById(R.id.btnRefresh)
        btnRefresh.setOnClickListener { refresh() }
        btnClearChecked.setOnClickListener { clearChecked() }
        btnAdd.setOnClickListener { onAdd() }

        etNewItem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                if (section != Section.EVENTS) renderShopping()
            }
        })
        findViewById<ImageButton>(R.id.btnAddItem).setOnClickListener { showAddItemDialog() }
        etNewItem.setOnEditorActionListener { _, _, _ -> showAddItemDialog(); true }

        scrim.setOnClickListener { closeDrawer() }
        findViewById<TextView>(R.id.navEvents).setOnClickListener     { switchSection(Section.EVENTS);      closeDrawer() }
        findViewById<TextView>(R.id.navListe).setOnClickListener      { switchSection(Section.LISTE);       closeDrawer() }
        findViewById<TextView>(R.id.navCourse).setOnClickListener     { switchSection(Section.COURSE);      closeDrawer() }
        findViewById<TextView>(R.id.navLeclerc).setOnClickListener    { switchSection(Section.LECLERC);     closeDrawer() }
        findViewById<TextView>(R.id.navGrandFrais).setOnClickListener { switchSection(Section.GRAND_FRAIS); closeDrawer() }
        findViewById<TextView>(R.id.navAutre).setOnClickListener      { switchSection(Section.AUTRE);       closeDrawer() }

        // Show/hide Course vs Leclerc+Grand Frais based on courseMode pref
        val courseMode = Prefs.courseMode(this)
        findViewById<TextView>(R.id.navCourse).visibility     = if (courseMode) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.navLeclerc).visibility    = if (courseMode) View.GONE    else View.VISIBLE
        findViewById<TextView>(R.id.navGrandFrais).visibility = if (courseMode) View.GONE    else View.VISIBLE
        findViewById<TextView>(R.id.navSettings).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.navLogout).setOnClickListener {
            Prefs.clearToken(this)
            Api.token = null
            closeDrawer()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        NotificationScheduler.ensureChannel(this)

        events     = Prefs.loadEvents(this)
        shopping   = Prefs.loadShopping(this)
        categories = Prefs.loadCategories(this)
        renderSection()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        events     = Prefs.loadEvents(this)
        shopping   = Prefs.loadShopping(this)
        categories = Prefs.loadCategories(this)
        recurring  = Prefs.loadRecurring(this)
        checkAndAddRecurring()
        renderSection()
    }

    override fun onBackPressed() {
        if (isDrawerOpen()) closeDrawer() else super.onBackPressed()
    }

    // ── Drawer ────────────────────────────────────────────────────────────────

    private fun isDrawerOpen() = drawerPanel.translationX >= 0f
    private fun toggleDrawer() { if (isDrawerOpen()) closeDrawer() else openDrawer() }

    private fun openDrawer() {
        scrim.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(drawerPanel, "translationX", 0f).setDuration(200).start()
    }

    private fun closeDrawer() {
        ObjectAnimator.ofFloat(drawerPanel, "translationX", -drawerPanel.width.toFloat().coerceAtLeast(800f))
            .setDuration(200).also {
                it.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) { scrim.visibility = View.GONE }
                })
            }.start()
    }

    // ── Section switching ─────────────────────────────────────────────────────

    private fun switchSection(s: Section) {
        section = s
        searchQuery = ""
        etNewItem.setText("")
        renderSection()
    }

    private fun currentStore(): String = when (section) {
        Section.LECLERC     -> "Leclerc"
        Section.GRAND_FRAIS -> "Grand Frais"
        Section.AUTRE       -> "Autre"
        else                -> ""
    }

    private fun renderSection() {
        when (section) {
            Section.EVENTS -> {
                tvTitle.text                = getString(R.string.nav_events)
                rowShoppingInput.visibility = View.GONE
                btnAdd.visibility           = View.VISIBLE
                btnClearChecked.visibility  = View.GONE
                renderEvents()
            }
            Section.LISTE -> {
                tvTitle.text                = getString(R.string.nav_liste)
                rowShoppingInput.visibility = View.VISIBLE
                btnAdd.visibility           = View.GONE
                btnClearChecked.visibility  = View.GONE
                renderShopping()
            }
            Section.COURSE -> {
                tvTitle.text                = getString(R.string.nav_course)
                rowShoppingInput.visibility = View.VISIBLE
                btnAdd.visibility           = View.GONE
                btnClearChecked.visibility  = View.VISIBLE
                renderShopping()
            }
            Section.LECLERC, Section.GRAND_FRAIS, Section.AUTRE -> {
                tvTitle.text = when (section) {
                    Section.LECLERC     -> getString(R.string.nav_leclerc)
                    Section.GRAND_FRAIS -> getString(R.string.nav_grand_frais)
                    else                -> getString(R.string.nav_autre)
                }
                rowShoppingInput.visibility = View.VISIBLE
                btnAdd.visibility           = View.GONE
                btnClearChecked.visibility  = View.VISIBLE
                renderShopping()
            }
        }
    }

    // ── Sibling helpers ───────────────────────────────────────────────────────

    /** Returns the "other-store" sibling of an item (Leclerc↔Grand Frais), if any. */
    private fun findSibling(item: ShoppingItem): ShoppingItem? =
        shopping.firstOrNull {
            it.id != item.id &&
            it.name.equals(item.name, ignoreCase = true) &&
            it.category == item.category &&
            (it.store == "Leclerc" || it.store == "Grand Frais")
        }

    /**
     * Apply [transform] to [item] and, if it has a sibling and [syncSibling] is true,
     * apply [siblingTransform] (defaults to [transform]) to the sibling too.
     * Saves + syncs all touched items.
     */
    private fun updateWithSibling(
        item: ShoppingItem,
        syncSibling: Boolean = true,
        transform: (ShoppingItem) -> ShoppingItem,
        siblingTransform: ((ShoppingItem) -> ShoppingItem)? = null
    ) {
        val updated = transform(item)
        val sibling = if (syncSibling) findSibling(item) else null
        val updatedSibling = sibling?.let { (siblingTransform ?: transform)(it) }

        shopping = shopping.map { s ->
            when (s.id) {
                updated.id        -> updated
                updatedSibling?.id -> updatedSibling
                else              -> s
            }
        }
        Prefs.saveShopping(this, shopping)
        renderShopping()
        Thread {
            val ok1 = Api.updateShoppingItem(updated)
            val ok2 = updatedSibling?.let { Api.updateShoppingItem(it) }
            if (ok1 == null || (updatedSibling != null && ok2 == null)) showSyncError()
        }.start()
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private fun renderEvents() {
        val fmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today  = LocalDate.now()
        val cutoff = today.minusDays(7)

        val visible = events
            .filter { e ->
                val d = try { LocalDate.parse(e.date, fmt) } catch (_: Exception) { return@filter false }
                !d.isBefore(cutoff)
            }
            .sortedWith(compareBy { e -> try { LocalDate.parse(e.date, fmt) } catch (_: Exception) { LocalDate.MAX } })

        if (visible.isEmpty()) {
            tvEmpty.text        = getString(R.string.empty_events)
            tvEmpty.visibility  = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            tvEmpty.visibility  = View.GONE
            listView.visibility = View.VISIBLE
        }

        listView.adapter = object : ArrayAdapter<Event>(this, 0, visible) {
            override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
                val row  = convert ?: layoutInflater.inflate(R.layout.row_event, parent, false)
                val e    = getItem(pos)!!
                val d    = try { LocalDate.parse(e.date, fmt) } catch (_: Exception) { null }
                val muted = d != null && d.isBefore(today)
                row.findViewById<TextView>(R.id.tvEventTitle).apply {
                    text = e.title
                    setTextColor(getColor(if (muted) R.color.muted else R.color.text_primary))
                }
                row.findViewById<TextView>(R.id.tvEventDate).apply {
                    text = formatEventDate(d, e.date, e.time, today)
                    setTextColor(getColor(if (muted) R.color.muted else R.color.text_secondary))
                }
                row.setOnClickListener {
                    startActivity(Intent(this@MainActivity, EventFormActivity::class.java).apply {
                        putExtra(EventFormActivity.EXTRA_EVENT_ID, e.id)
                    })
                }
                return row
            }
        }
    }

    private fun formatEventDate(d: LocalDate?, rawDate: String, time: String?, today: LocalDate): String {
        if (d == null) return rawDate
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, d).toInt()
        val dayName = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            .replaceFirstChar { it.uppercase() }
        val timeSuffix = if (time != null) " · $time" else ""
        return when {
            days == 0 -> "${getString(R.string.label_today)}$timeSuffix"
            days == 1 -> "${getString(R.string.label_tomorrow)}$timeSuffix"
            days > 1  -> "$rawDate · $dayName · +${days}d$timeSuffix"
            else      -> rawDate
        }
    }

    // ── Shopping rows ─────────────────────────────────────────────────────────

    private sealed class ShoppingRow {
        data class Header(val label: String) : ShoppingRow()
        /** [primary] is the item for this store (or Leclerc for merged rows).
         *  [sibling] is non-null when both stores have this item (merged Liste row). */
        data class Item(val primary: ShoppingItem, val sibling: ShoppingItem? = null) : ShoppingRow()
    }

    private fun buildShoppingRows(items: List<ShoppingItem>): List<ShoppingRow> {
        val query = searchQuery.lowercase()

        when (section) {

            // ── Liste ─────────────────────────────────────────────────────────
            Section.LISTE -> {
                val leclercItems    = items.filter { it.store == "Leclerc" }
                val grandFraisItems = items.filter { it.store == "Grand Frais" }

                // Build merged items: key = (name lowercase, category)
                data class Key(val name: String, val cat: String?)
                val lMap = leclercItems.associateBy    { Key(it.name.lowercase(), it.category) }
                val gMap = grandFraisItems.associateBy { Key(it.name.lowercase(), it.category) }
                val allKeys = (lMap.keys + gMap.keys).toSet()

                // Each key becomes one ShoppingRow.Item (primary=Leclerc if exists, else GrandFrais; sibling=other)
                data class MergedItem(
                    val primary: ShoppingItem,
                    val sibling: ShoppingItem?,
                    val planned: Boolean  // true if any sibling is planned
                )
                val merged = allKeys.map { k ->
                    val l = lMap[k]
                    val g = gMap[k]
                    val primary  = l ?: g!!
                    val sibling  = if (l != null && g != null) g else null
                    val planned  = (l?.planned ?: false) || (g?.planned ?: false)
                    MergedItem(primary, sibling, planned)
                }

                // Filter by search
                val filtered = if (query.isEmpty()) merged
                               else merged.filter { it.primary.name.lowercase().contains(query) }

                val rows     = mutableListOf<ShoppingRow>()
                val catOrder = categories + listOf(null as String?)
                val grouped  = filtered.groupBy { it.primary.category }

                for (cat in catOrder) {
                    val group = grouped[cat] ?: continue
                    val unplanned = group.filter { !it.planned }.sortedBy { it.primary.name }
                    val planned   = group.filter {  it.planned }.sortedBy { it.primary.name }
                    if (unplanned.isEmpty() && planned.isEmpty()) continue
                    rows.add(ShoppingRow.Header(cat ?: getString(R.string.category_none)))
                    unplanned.forEach { rows.add(ShoppingRow.Item(it.primary, it.sibling)) }
                    planned.forEach   { rows.add(ShoppingRow.Item(it.primary, it.sibling)) }
                }
                // Unknown categories
                for ((cat, group) in grouped) {
                    if (cat != null && cat !in categories) {
                        rows.add(ShoppingRow.Header(cat))
                        group.filter { !it.planned }.sortedBy { it.primary.name }.forEach { rows.add(ShoppingRow.Item(it.primary, it.sibling)) }
                        group.filter {  it.planned }.sortedBy { it.primary.name }.forEach { rows.add(ShoppingRow.Item(it.primary, it.sibling)) }
                    }
                }
                return rows
            }

            // ── Course (merged Leclerc + Grand Frais, planned only, tick = checked) ──
            Section.COURSE -> {
                val leclercItems    = items.filter { it.store == "Leclerc"     && it.planned }
                val grandFraisItems = items.filter { it.store == "Grand Frais" && it.planned }

                data class CKey(val name: String, val cat: String?)
                val lMap    = leclercItems.associateBy    { CKey(it.name.lowercase(), it.category) }
                val gMap    = grandFraisItems.associateBy { CKey(it.name.lowercase(), it.category) }
                val allKeys = (lMap.keys + gMap.keys).toSet()

                data class CMergedItem(
                    val primary: ShoppingItem,
                    val sibling: ShoppingItem?,
                    val checked: Boolean  // true if all present siblings are checked
                )
                val merged = allKeys.map { k ->
                    val l        = lMap[k]
                    val g        = gMap[k]
                    val primary  = l ?: g!!
                    val sibling  = if (l != null && g != null) g else null
                    val checked  = (l == null || l.checked) && (g == null || g.checked)
                    CMergedItem(primary, sibling, checked)
                }

                val filtered = if (query.isEmpty()) merged
                               else merged.filter { it.primary.name.lowercase().contains(query) }

                val unchecked = filtered.filter { !it.checked }
                val checked   = filtered.filter {  it.checked }

                val rows     = mutableListOf<ShoppingRow>()
                val catOrder = categories + listOf(null as String?)
                val grouped  = unchecked.groupBy { it.primary.category }

                for (cat in catOrder) {
                    val group = grouped[cat] ?: continue
                    rows.add(ShoppingRow.Header(cat ?: getString(R.string.category_none)))
                    group.sortedBy { it.primary.name }.forEach { rows.add(ShoppingRow.Item(it.primary, it.sibling)) }
                }
                for ((cat, group) in grouped) {
                    if (cat != null && cat !in categories) {
                        rows.add(ShoppingRow.Header(cat))
                        group.sortedBy { it.primary.name }.forEach { rows.add(ShoppingRow.Item(it.primary, it.sibling)) }
                    }
                }
                if (checked.isNotEmpty()) {
                    rows.add(ShoppingRow.Header("✓ Fait"))
                    checked.sortedBy { it.primary.name }.forEach { rows.add(ShoppingRow.Item(it.primary, it.sibling)) }
                }
                return rows
            }

            // ── Leclerc / Grand Frais ─────────────────────────────────────────
            Section.LECLERC, Section.GRAND_FRAIS -> {
                val store    = currentStore()
                val filtered = items
                    .filter { it.store == store && it.planned }
                    .let { if (query.isEmpty()) it else it.filter { i -> i.name.lowercase().contains(query) } }

                val unchecked = filtered.filter { !it.checked }
                val checked   = filtered.filter {  it.checked }
                val rows      = mutableListOf<ShoppingRow>()
                val catOrder  = categories + listOf(null as String?)
                val grouped   = unchecked.groupBy { it.category }

                for (cat in catOrder) {
                    val group = grouped[cat] ?: continue
                    rows.add(ShoppingRow.Header(cat ?: getString(R.string.category_none)))
                    group.sortedBy { it.name }.forEach { rows.add(ShoppingRow.Item(it)) }
                }
                for ((cat, group) in grouped) {
                    if (cat != null && cat !in categories) {
                        rows.add(ShoppingRow.Header(cat))
                        group.sortedBy { it.name }.forEach { rows.add(ShoppingRow.Item(it)) }
                    }
                }
                if (checked.isNotEmpty()) {
                    rows.add(ShoppingRow.Header("✓ Done"))
                    checked.sortedBy { it.name }.forEach { rows.add(ShoppingRow.Item(it)) }
                }
                return rows
            }

            // ── Autre ─────────────────────────────────────────────────────────
            Section.AUTRE -> {
                val filtered = items
                    .filter { it.store == "Autre" }
                    .let { if (query.isEmpty()) it else it.filter { i -> i.name.lowercase().contains(query) } }
                val rows = mutableListOf<ShoppingRow>()
                filtered.filter { !it.checked }.sortedBy { it.name }.forEach { rows.add(ShoppingRow.Item(it)) }
                val checked = filtered.filter { it.checked }.sortedBy { it.name }
                if (checked.isNotEmpty()) {
                    rows.add(ShoppingRow.Header("✓ Done"))
                    checked.forEach { rows.add(ShoppingRow.Item(it)) }
                }
                return rows
            }

            else -> return emptyList()
        }
    }

    private fun renderShopping() {
        val rows = buildShoppingRows(shopping)

        // Remove header-only groups
        val cleaned = mutableListOf<ShoppingRow>()
        for (i in rows.indices) {
            val row = rows[i]
            if (row is ShoppingRow.Header) {
                val hasItems = rows.drop(i + 1).takeWhile { it is ShoppingRow.Item }.isNotEmpty()
                if (hasItems) cleaned.add(row)
            } else {
                cleaned.add(row)
            }
        }

        val itemCount = cleaned.filterIsInstance<ShoppingRow.Item>().size
        if (itemCount == 0) {
            tvEmpty.text        = getString(R.string.empty_shopping)
            tvEmpty.visibility  = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            tvEmpty.visibility  = View.GONE
            listView.visibility = View.VISIBLE
        }

        val isListe     = section == Section.LISTE
        val isCourse    = section == Section.COURSE
        val isStoreList = section == Section.LECLERC || section == Section.GRAND_FRAIS
        val TYPE_HEADER = 0
        val TYPE_ITEM   = 1

        // Preserve scroll position across adapter swap
        val firstVisible = listView.firstVisiblePosition
        val topOffset    = listView.getChildAt(0)?.top ?: 0

        listView.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = cleaned.size
            override fun getItem(pos: Int) = cleaned[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(pos: Int) = if (cleaned[pos] is ShoppingRow.Header) TYPE_HEADER else TYPE_ITEM
            override fun isEnabled(pos: Int) = cleaned[pos] is ShoppingRow.Item

            override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
                return when (val row = cleaned[pos]) {
                    is ShoppingRow.Header -> {
                        val v = convert ?: layoutInflater.inflate(R.layout.row_shopping_header, parent, false)
                        v.findViewById<TextView>(R.id.tvCategoryHeader).text = row.label
                        v
                    }
                    is ShoppingRow.Item -> {
                        val s       = row.primary
                        val sibling = row.sibling   // non-null only in merged Liste rows
                        val v   = convert ?: layoutInflater.inflate(R.layout.row_shopping, parent, false)
                        val cb  = v.findViewById<CheckBox>(R.id.cbItem)
                        val tv  = v.findViewById<TextView>(R.id.tvItemName)
                        val del = v.findViewById<ImageButton>(R.id.btnDeleteItem)
                        val ivRec = v.findViewById<android.widget.ImageView>(R.id.ivRecurring)

                        // Show recurring icon in Liste and Course sections
                        val hasRecurrence = (isListe || isCourse) && recurring.any {
                            it.name.equals(s.name, ignoreCase = true) && it.category == s.category
                        }
                        ivRec.visibility = if (hasRecurrence) View.VISIBLE else View.GONE

                        cb.setOnCheckedChangeListener(null)

                        when {
                            isListe -> {
                                // planned state — if sibling exists, planned = either is planned
                                val isPlanned = s.planned || (sibling?.planned ?: false)
                                cb.isChecked = isPlanned
                                val badge = when {
                                    sibling != null -> " [L+GF]"
                                    s.store == "Grand Frais" -> " [GF]"
                                    else -> " [L]"
                                }
                                tv.text = s.name + badge
                                if (isPlanned) {
                                    tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                                    tv.setTextColor(getColor(R.color.muted))
                                } else {
                                    tv.paintFlags = tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                                    tv.setTextColor(getColor(R.color.text_primary))
                                }
                                cb.setOnCheckedChangeListener { _, planned ->
                                    val ts = System.currentTimeMillis()
                                    val updatedPrimary = s.copy(
                                        planned   = planned,
                                        checked   = if (!planned) false else s.checked,
                                        updatedAt = ts
                                    )
                                    val updatedSibling = sibling?.copy(
                                        planned   = planned,
                                        checked   = if (!planned) false else sibling.checked,
                                        updatedAt = ts
                                    )
                                    shopping = shopping.map { item ->
                                        when (item.id) {
                                            updatedPrimary.id  -> updatedPrimary
                                            updatedSibling?.id -> updatedSibling
                                            else               -> item
                                        }
                                    }
                                    Prefs.saveShopping(this@MainActivity, shopping)
                                    renderShopping()
                                    Thread {
                                        val ok1 = Api.updateShoppingItem(updatedPrimary)
                                        val ok2 = updatedSibling?.let { Api.updateShoppingItem(it) }
                                        if (ok1 == null || (updatedSibling != null && ok2 == null)) showSyncError()
                                    }.start()
                                }
                            }

                            isCourse -> {
                                // checked = all present siblings are checked
                                val isChecked = s.checked && (sibling == null || sibling.checked)
                                cb.isChecked = isChecked
                                val badge = when {
                                    sibling != null          -> " [L+GF]"
                                    s.store == "Grand Frais" -> " [GF]"
                                    else                     -> " [L]"
                                }
                                tv.text = s.name + badge
                                if (isChecked) {
                                    tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                                    tv.setTextColor(getColor(R.color.muted))
                                } else {
                                    tv.paintFlags = tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                                    tv.setTextColor(getColor(R.color.text_primary))
                                }
                                cb.setOnCheckedChangeListener { _, checked ->
                                    val ts = System.currentTimeMillis()
                                    val updatedPrimary = s.copy(checked = checked, updatedAt = ts)
                                    val updatedSibling = sibling?.copy(checked = checked, updatedAt = ts)
                                    shopping = shopping.map { item ->
                                        when (item.id) {
                                            updatedPrimary.id  -> updatedPrimary
                                            updatedSibling?.id -> updatedSibling
                                            else               -> item
                                        }
                                    }
                                    Prefs.saveShopping(this@MainActivity, shopping)
                                    renderShopping()
                                    Thread {
                                        val ok1 = Api.updateShoppingItem(updatedPrimary)
                                        val ok2 = updatedSibling?.let { Api.updateShoppingItem(it) }
                                        if (ok1 == null || (updatedSibling != null && ok2 == null)) showSyncError()
                                    }.start()
                                }
                            }

                            isStoreList -> {
                                cb.isChecked = s.checked
                                tv.text = s.name
                                if (s.checked) {
                                    tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                                    tv.setTextColor(getColor(R.color.muted))
                                } else {
                                    tv.paintFlags = tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                                    tv.setTextColor(getColor(R.color.text_primary))
                                }
                                cb.setOnCheckedChangeListener { _, checked ->
                                    // Sync sibling if it is also planned
                                    updateWithSibling(s, syncSibling = true, transform = { item ->
                                        item.copy(checked = checked, updatedAt = System.currentTimeMillis())
                                    })
                                }
                            }

                            else -> { // Autre
                                cb.isChecked = s.checked
                                tv.text = s.name
                                if (s.checked) {
                                    tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                                    tv.setTextColor(getColor(R.color.muted))
                                } else {
                                    tv.paintFlags = tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                                    tv.setTextColor(getColor(R.color.text_primary))
                                }
                                cb.setOnCheckedChangeListener { _, checked ->
                                    val updated = s.copy(checked = checked, updatedAt = System.currentTimeMillis())
                                    shopping = shopping.map { if (it.id == s.id) updated else it }
                                    Prefs.saveShopping(this@MainActivity, shopping)
                                    renderShopping()
                                    Thread {
                                        val ok = Api.updateShoppingItem(updated)
                                        if (ok == null) showSyncError()
                                    }.start()
                                }
                            }
                        }

                        del.visibility = if (s.store == "Autre") View.VISIBLE else View.GONE
                        del.setOnClickListener {
                            shopping = shopping.filter { it.id != s.id }
                            Prefs.saveShopping(this@MainActivity, shopping)
                            renderShopping()
                            Thread {
                                val ok = Api.deleteShoppingItem(s.id)
                                if (!ok) showSyncError()
                            }.start()
                        }

                        v.setOnLongClickListener {
                            if (s.store == "Autre") showRenameDialog(s)
                            else showChangeStoreDialog(s, sibling)
                            true
                        }
                        v
                    }
                }
            }
        }
        listView.setSelectionFromTop(firstVisible, topOffset)
    }

    // ── Shopping actions ──────────────────────────────────────────────────────

    private fun clearChecked() {
        if (section == Section.COURSE) {
            // Reset all fully-checked Course items (planned=false, checked=false) on both stores
            val checkedItems = shopping
                .filter { (it.store == "Leclerc" || it.store == "Grand Frais") && it.planned && it.checked }
            if (checkedItems.isEmpty()) return
            val ts = System.currentTimeMillis()
            val toReset = checkedItems.map { it.copy(planned = false, checked = false, updatedAt = ts) }
            shopping = shopping.map { item -> toReset.firstOrNull { it.id == item.id } ?: item }
            Prefs.saveShopping(this, shopping)
            val updatedRecurring = checkedItems.mapNotNull { Prefs.updateRecurringLastBought(this, it.name, it.category, ts) }
            recurring = Prefs.loadRecurring(this)
            renderShopping()
            Thread {
                toReset.forEach { item ->
                    val ok = Api.updateShoppingItem(item)
                    if (ok == null) showSyncError()
                }
                updatedRecurring.forEach { r ->
                    val ok = Api.updateRecurring(r)
                    if (ok == null) showSyncError()
                }
            }.start()
            return
        }

        val store   = currentStore()
        val checked = shopping.filter { it.store == store && it.checked }
        if (checked.isEmpty()) return

        if (store == "Autre") {
            shopping = shopping.filter { !(it.store == store && it.checked) }
            Prefs.saveShopping(this, shopping)
            renderShopping()
            Thread { checked.forEach { item ->
                val ok = Api.deleteShoppingItem(item.id)
                if (!ok) showSyncError()
            } }.start()
        } else {
            // Reset checked items and their siblings (planned=false, checked=false)
            val ts = System.currentTimeMillis()
            val toReset = mutableListOf<ShoppingItem>()
            for (item in checked) {
                toReset.add(item.copy(planned = false, checked = false, updatedAt = ts))
                findSibling(item)?.let { sib ->
                    if (toReset.none { it.id == sib.id })
                        toReset.add(sib.copy(planned = false, checked = false, updatedAt = ts))
                }
            }
            shopping = shopping.map { item -> toReset.firstOrNull { it.id == item.id } ?: item }
            Prefs.saveShopping(this, shopping)
            val updatedRecurring = checked.mapNotNull { Prefs.updateRecurringLastBought(this, it.name, it.category, ts) }
            recurring = Prefs.loadRecurring(this)
            renderShopping()
            Thread {
                toReset.forEach { item ->
                    val ok = Api.updateShoppingItem(item)
                    if (ok == null) showSyncError()
                }
                updatedRecurring.forEach { r ->
                    val ok = Api.updateRecurring(r)
                    if (ok == null) showSyncError()
                }
            }.start()
        }
    }

    // ── Add item dialog ───────────────────────────────────────────────────────

    private fun showAddItemDialog() {
        val isListe  = section == Section.LISTE
        val isCourse = section == Section.COURSE
        val isAutre  = section == Section.AUTRE
        val prefill = etNewItem.text.toString().trim()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(this).apply {
            setText(prefill)
            hint = getString(R.string.hint_item_name)
            setTextColor(getColor(R.color.text_primary))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        layout.addView(etName)

        var spinnerCat: Spinner?  = null
        var cbLeclerc:  CheckBox? = null
        var cbGF:       CheckBox? = null
        var cbAlso:     CheckBox? = null   // "also add to other store" for Leclerc/GF sections

        if (!isAutre) {
            // Category spinner
            val tvCatLabel = TextView(this).apply {
                text = getString(R.string.label_categories)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(0, 16, 0, 4)
            }
            spinnerCat = Spinner(this)
            val catOptions = listOf(getString(R.string.category_none)) + categories
            spinnerCat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, catOptions).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            layout.addView(tvCatLabel)
            layout.addView(spinnerCat)

            // Store selection
            val tvStoreLabel = TextView(this).apply {
                text = "Magasin"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
                setPadding(0, 16, 0, 4)
            }
            layout.addView(tvStoreLabel)

            if (isListe || isCourse) {
                // Two independent checkboxes — at least one must be ticked
                cbLeclerc = CheckBox(this).apply {
                    text = "Leclerc"
                    isChecked = true
                    setTextColor(getColor(R.color.text_primary))
                }
                cbGF = CheckBox(this).apply {
                    text = "Grand Frais"
                    isChecked = isCourse   // both pre-ticked in Course mode
                    setTextColor(getColor(R.color.text_primary))
                }
                layout.addView(cbLeclerc)
                layout.addView(cbGF)
            } else {
                // In a store section: "Also add to [other store]" checkbox
                val otherStore = if (section == Section.LECLERC) "Grand Frais" else "Leclerc"
                cbAlso = CheckBox(this).apply {
                    text = "Aussi dans $otherStore"
                    isChecked = false
                    setTextColor(getColor(R.color.text_primary))
                }
                layout.addView(cbAlso)
            }
        }

        val sc    = spinnerCat
        val cbL   = cbLeclerc
        val cbG   = cbGF
        val cbA   = cbAlso

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_add))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val itemName = etName.text.toString().trim()
                if (itemName.isEmpty()) return@setPositiveButton

                val cat = if (isAutre || sc == null || sc.selectedItemPosition == 0) null
                          else categories[sc.selectedItemPosition - 1]

                when {
                    isAutre -> createShoppingItem(itemName, cat, "Autre")

                    isListe || isCourse -> {
                        val wantLeclerc    = cbL?.isChecked ?: false
                        val wantGrandFrais = cbG?.isChecked ?: false
                        if (!wantLeclerc && !wantGrandFrais) return@setPositiveButton
                        if (wantLeclerc)    createShoppingItem(itemName, cat, "Leclerc")
                        if (wantGrandFrais) createShoppingItem(itemName, cat, "Grand Frais")
                    }

                    else -> {
                        // In a Leclerc or Grand Frais section
                        createShoppingItem(itemName, cat, currentStore())
                        if (cbA?.isChecked == true) {
                            val other = if (section == Section.LECLERC) "Grand Frais" else "Leclerc"
                            createShoppingItem(itemName, cat, other)
                        }
                    }
                }
                etNewItem.setText("")
                searchQuery = ""
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(item: ShoppingItem) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(this).apply {
            setText(item.name)
            selectAll()
            setTextColor(getColor(R.color.text_primary))
        }
        layout.addView(etName)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_edit_item))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                val updated = item.copy(name = newName, updatedAt = System.currentTimeMillis())
                shopping = shopping.map { if (it.id == item.id) updated else it }
                Prefs.saveShopping(this, shopping)
                renderShopping()
                Thread {
                    val ok = Api.updateShoppingItem(updated)
                    if (ok == null) showSyncError()
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameListeItemDialog(item: ShoppingItem, sibling: ShoppingItem?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(this).apply {
            setText(item.name)
            selectAll()
            setTextColor(getColor(R.color.text_primary))
        }
        layout.addView(etName)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_edit_item))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                val ts = System.currentTimeMillis()
                val updatedPrimary = item.copy(name = newName, updatedAt = ts)
                val updatedSibling = sibling?.copy(name = newName, updatedAt = ts)
                shopping = shopping.map { s ->
                    when (s.id) {
                        updatedPrimary.id  -> updatedPrimary
                        updatedSibling?.id -> updatedSibling
                        else               -> s
                    }
                }
                Prefs.saveShopping(this, shopping)
                renderShopping()
                Thread {
                    val ok1 = Api.updateShoppingItem(updatedPrimary)
                    val ok2 = updatedSibling?.let { Api.updateShoppingItem(it) }
                    if (ok1 == null || (updatedSibling != null && ok2 == null)) showSyncError()
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChangeStoreDialog(item: ShoppingItem, sibling: ShoppingItem?) {
        if (section == Section.LISTE) {
            val options = arrayOf(getString(R.string.action_rename), getString(R.string.action_change_store), getString(R.string.action_recurrence), getString(R.string.action_delete))
            AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(options) { _, idx ->
                    when (idx) {
                        0 -> showRenameListeItemDialog(item, sibling)
                        1 -> showStorePicker(item)
                        2 -> showRecurrenceDialog(item, sibling)
                        3 -> {
                            val idsToDelete = listOfNotNull(item.id, sibling?.id).toSet()
                            shopping = shopping.filter { it.id !in idsToDelete }
                            Prefs.saveShopping(this, shopping)
                            renderShopping()
                            Thread {
                                idsToDelete.forEach { id ->
                                    val ok = Api.deleteShoppingItem(id)
                                    if (!ok) showSyncError()
                                }
                            }.start()
                        }
                    }
                }
                .show()
            return
        }
        showStorePicker(item)
    }

    private fun showStorePicker(item: ShoppingItem) {
        val stores = arrayOf("Leclerc", "Grand Frais", "Autre")
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_move_store))
            .setItems(stores) { _, idx ->
                val newStore = stores[idx]
                if (newStore == item.store) return@setItems
                val ts = System.currentTimeMillis()
                val updated = item.copy(store = newStore, updatedAt = ts)
                shopping = shopping.map { if (it.id == item.id) updated else it }
                Prefs.saveShopping(this, shopping)
                renderShopping()
                Thread {
                    val ok = Api.updateShoppingItem(updated)
                    if (ok == null) showSyncError()
                }.start()
            }
            .show()
    }

    // ── Recurring items ───────────────────────────────────────────────────────

    private fun checkAndAddRecurring() {
        if (recurring.isEmpty()) return
        val now = System.currentTimeMillis()
        var changed = false
        for (r in recurring) {
            val dueMs = r.periodWeeks * 7L * 24 * 60 * 60 * 1000
            if (now - r.lastBought < dueMs) continue
            for (store in r.stores) {
                val alreadyExists = shopping.any {
                    it.store == store &&
                    it.name.equals(r.name, ignoreCase = true) &&
                    it.category == r.category
                }
                if (!alreadyExists) {
                    val item = ShoppingItem(
                        id        = java.util.UUID.randomUUID().toString(),
                        name      = r.name,
                        checked   = false,
                        planned   = false,
                        category  = r.category,
                        store     = store,
                        updatedAt = now
                    )
                    shopping = shopping + item
                    changed  = true
                    Thread {
                        val created = Api.createShoppingItem(item)
                        if (created != null) {
                            shopping = shopping.map { if (it.id == item.id) created else it }
                            Prefs.saveShopping(this, shopping)
                        } else showSyncError()
                    }.start()
                }
            }
        }
        if (changed) Prefs.saveShopping(this, shopping)
    }

    private fun showRecurrenceDialog(item: ShoppingItem, sibling: ShoppingItem?) {
        val existing = recurring.firstOrNull {
            it.name.equals(item.name, ignoreCase = true) && it.category == item.category
        }

        val weekOptions = arrayOf("1 semaine", "2 semaines", "3 semaines", "4 semaines", "6 semaines", "8 semaines")
        val weekValues  = intArrayOf(1, 2, 3, 4, 6, 8)

        if (existing != null) {
            val currentWeeks = existing.periodWeeks
            val currentLabel = weekValues.indexOfFirst { it == currentWeeks }
                .takeIf { it >= 0 }?.let { weekOptions[it] } ?: "$currentWeeks semaine(s)"
            val options = arrayOf(
                getString(R.string.action_edit_recurrence),
                getString(R.string.action_disable_recurrence)
            )
            AlertDialog.Builder(this)
                .setTitle("${getString(R.string.title_recurrence)} — $currentLabel")
                .setItems(options) { _, idx ->
                    when (idx) {
                        0 -> showRecurrencePicker(item, sibling, weekOptions, weekValues, existing.periodWeeks)
                        1 -> {
                            val idToDelete = existing.id
                            recurring = recurring.filter { it.id != idToDelete }
                            Prefs.saveRecurring(this, recurring)
                            Thread {
                                val ok = Api.deleteRecurring(idToDelete)
                                if (!ok) showSyncError()
                            }.start()
                        }
                    }
                }
                .show()
        } else {
            showRecurrencePicker(item, sibling, weekOptions, weekValues, defaultWeeks = 4)
        }
    }

    private fun showRecurrencePicker(
        item: ShoppingItem, sibling: ShoppingItem?,
        weekOptions: Array<String>, weekValues: IntArray,
        defaultWeeks: Int
    ) {
        val allOptions  = weekOptions + arrayOf("Personnalisé...")
        val defaultIdx  = weekValues.indexOfFirst { it == defaultWeeks }.coerceAtLeast(0)
        var selectedIdx = defaultIdx

        // Check if we're modifying an existing entry
        val existing = recurring.firstOrNull {
            it.name.equals(item.name, ignoreCase = true) && it.category == item.category
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_recurrence))
            .setSingleChoiceItems(allOptions, defaultIdx) { _, idx -> selectedIdx = idx }
            .setPositiveButton(getString(R.string.action_enable)) { _, _ ->
                if (selectedIdx == allOptions.lastIndex) {
                    // Custom weeks input
                    val et = EditText(this).apply {
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setText("5")
                        selectAll()
                        setTextColor(getColor(R.color.text_primary))
                        setPadding(48, 24, 48, 8)
                    }
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.title_recurrence))
                        .setView(et)
                        .setPositiveButton(getString(R.string.action_enable)) { _, _ ->
                            val weeks = et.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: return@setPositiveButton
                            saveRecurringItem(item, sibling, existing, weeks)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    saveRecurringItem(item, sibling, existing, weekValues[selectedIdx])
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveRecurringItem(item: ShoppingItem, sibling: ShoppingItem?, existing: RecurringItem?, weeks: Int) {
        val stores  = listOfNotNull(item.store, sibling?.store).distinct()
        val newItem = RecurringItem(
            id          = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name        = item.name,
            category    = item.category,
            stores      = stores,
            periodWeeks = weeks,
            lastBought  = System.currentTimeMillis()
        )
        recurring = recurring.filter {
            !(it.name.equals(item.name, ignoreCase = true) && it.category == item.category)
        } + newItem
        Prefs.saveRecurring(this, recurring)
        Thread {
            val ok = if (existing != null) Api.updateRecurring(newItem)
                     else Api.createRecurring(newItem)
            if (ok == null) showSyncError()
        }.start()
    }

    private fun createShoppingItem(name: String, category: String?, store: String) {
        val item = ShoppingItem(
            id        = java.util.UUID.randomUUID().toString(),
            name      = name,
            checked   = false,
            planned   = false,
            category  = category,
            store     = store,
            updatedAt = System.currentTimeMillis()
        )
        shopping = shopping + item
        Prefs.saveShopping(this, shopping)
        renderShopping()
        Thread {
            val created = Api.createShoppingItem(item)
            if (created != null) {
                shopping = shopping.map { if (it.id == item.id) created else it }
                Prefs.saveShopping(this, shopping)
            } else {
                showSyncError()
            }
        }.start()
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    private fun onAdd() { startActivity(Intent(this, EventFormActivity::class.java)) }

    private var lastSyncErrorShownAt: Long = 0

    private fun showSyncError() = runOnUiThread {
        val now = System.currentTimeMillis()
        if (now - lastSyncErrorShownAt >= 30_000) {
            lastSyncErrorShownAt = now
            Toast.makeText(this, getString(R.string.err_sync), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refresh() {
        btnRefresh.isEnabled = false
        Thread {
            val newEvents     = Api.getEvents()
            val newShopping   = Api.getShopping()
            val newCategories = Api.getCategories()
            val newRecurring  = Api.getRecurring()
            runOnUiThread {
                btnRefresh.isEnabled = true
                if (newEvents == null && newShopping == null && newCategories == null && newRecurring == null) {
                    Toast.makeText(this, getString(R.string.err_network), Toast.LENGTH_SHORT).show()
                }
                if (newEvents != null) {
                    events = newEvents
                    Prefs.saveEvents(this, events)
                    NotificationScheduler.scheduleAll(this, events, Prefs.notifLeadDays(this))
                    Thread { CalendarSync.syncAll(this, events) }.start()
                }
                if (newShopping != null) {
                    shopping = newShopping
                    Prefs.saveShopping(this, shopping)
                }
                if (newCategories != null) {
                    categories = newCategories
                    Prefs.saveCategories(this, categories)
                }
                if (newRecurring != null) {
                    recurring = newRecurring
                    Prefs.saveRecurring(this, recurring)
                }
                renderSection()
            }
        }.start()
    }
}
