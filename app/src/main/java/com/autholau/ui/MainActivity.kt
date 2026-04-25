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
import com.autholau.model.ShoppingItem
import com.autholau.notifications.NotificationScheduler
import com.autholau.storage.Api
import com.autholau.storage.Prefs
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    private enum class Section { EVENTS, LISTE, LECLERC, GRAND_FRAIS, AUTRE }

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

    // Data
    private var events:     List<Event>        = emptyList()
    private var shopping:   List<ShoppingItem> = emptyList()
    private var categories: List<String>       = emptyList()
    private var searchQuery: String            = ""

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
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { refresh() }
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
        findViewById<TextView>(R.id.navLeclerc).setOnClickListener    { switchSection(Section.LECLERC);     closeDrawer() }
        findViewById<TextView>(R.id.navGrandFrais).setOnClickListener { switchSection(Section.GRAND_FRAIS); closeDrawer() }
        findViewById<TextView>(R.id.navAutre).setOnClickListener      { switchSection(Section.AUTRE);       closeDrawer() }
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
            days == 0 -> "Today$timeSuffix"
            days == 1 -> "Tomorrow$timeSuffix"
            days > 1  -> "$rawDate · $dayName · +${days}d$timeSuffix"
            else      -> rawDate
        }
    }

    // ── Shopping — grouped adapter ─────────────────────────────────────────────

    private sealed class ShoppingRow {
        data class Header(val label: String) : ShoppingRow()
        data class Item(val item: ShoppingItem) : ShoppingRow()
    }

    private fun buildShoppingRows(items: List<ShoppingItem>): List<ShoppingRow> {
        val query = searchQuery.lowercase()

        if (section == Section.LISTE) {
            // Liste: all Leclerc + Grand Frais items, filtered by search
            // Grouped by category. Unchecked first, checked (greyed) mixed in
            // by category — same grouping, checked appear at bottom within group.
            val filtered = items
                .filter { it.store == "Leclerc" || it.store == "Grand Frais" }
                .let { if (query.isEmpty()) it else it.filter { i -> i.name.lowercase().contains(query) } }

            val rows = mutableListOf<ShoppingRow>()
            val catOrder = categories + listOf(null as String?)
            val grouped  = filtered.groupBy { it.category }

            for (cat in catOrder) {
                val group = grouped[cat] ?: continue
                val label = cat ?: getString(R.string.category_none)
                // unchecked first, then checked within same category header
                val unchecked = group.filter { !it.checked }.sortedBy { it.name }
                val checked   = group.filter {  it.checked }.sortedBy { it.name }
                if (unchecked.isEmpty() && checked.isEmpty()) continue
                rows.add(ShoppingRow.Header(label))
                unchecked.forEach { rows.add(ShoppingRow.Item(it)) }
                checked.forEach   { rows.add(ShoppingRow.Item(it)) }
            }
            // unknown categories
            for ((cat, group) in grouped) {
                if (cat != null && cat !in categories) {
                    rows.add(ShoppingRow.Header(cat))
                    group.filter { !it.checked }.sortedBy { it.name }.forEach { rows.add(ShoppingRow.Item(it)) }
                    group.filter {  it.checked }.sortedBy { it.name }.forEach { rows.add(ShoppingRow.Item(it)) }
                }
            }
            return rows
        }

        val store = currentStore()
        val filtered = items
            .filter { it.store == store }
            .let { if (query.isEmpty()) it else it.filter { i -> i.name.lowercase().contains(query) } }

        if (store == "Autre") {
            val rows = mutableListOf<ShoppingRow>()
            filtered.filter { !it.checked }.sortedBy { it.name }.forEach { rows.add(ShoppingRow.Item(it)) }
            val checked = filtered.filter { it.checked }.sortedBy { it.name }
            if (checked.isNotEmpty()) {
                rows.add(ShoppingRow.Header("✓ Done"))
                checked.forEach { rows.add(ShoppingRow.Item(it)) }
            }
            return rows
        }

        // Leclerc / Grand Frais: grouped by category
        val unchecked = filtered.filter { !it.checked }
        val checked   = filtered.filter {  it.checked }
        val rows = mutableListOf<ShoppingRow>()
        val catOrder = categories + listOf(null as String?)
        val grouped  = unchecked.groupBy { it.category }
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
        val TYPE_HEADER = 0
        val TYPE_ITEM   = 1
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
                        val s   = row.item
                        val v   = convert ?: layoutInflater.inflate(R.layout.row_shopping, parent, false)
                        val cb  = v.findViewById<CheckBox>(R.id.cbItem)
                        val tv  = v.findViewById<TextView>(R.id.tvItemName)
                        val del = v.findViewById<ImageButton>(R.id.btnDeleteItem)

                        cb.setOnCheckedChangeListener(null)
                        cb.isChecked = s.checked

                        // In Liste: show store badge alongside item name
                        tv.text = if (isListe) {
                            val badge = if (s.store == "Grand Frais") " [GF]" else " [L]"
                            s.name + badge
                        } else {
                            s.name
                        }

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
                            Thread { Api.updateShoppingItem(updated) }.start()
                        }

                        // Delete button only for Autre items
                        del.visibility = if (s.store == "Autre") View.VISIBLE else View.GONE
                        del.setOnClickListener {
                            shopping = shopping.filter { it.id != s.id }
                            Prefs.saveShopping(this@MainActivity, shopping)
                            renderShopping()
                            Thread { Api.deleteShoppingItem(s.id) }.start()
                        }

                        v.setOnLongClickListener {
                            when {
                                s.store == "Autre"  -> showRenameDialog(s)
                                isListe             -> showChangeStoreDialog(s)
                                else                -> showChangeStoreDialog(s)
                            }
                            true
                        }
                        v
                    }
                }
            }
        }
    }

    // ── Shopping actions ──────────────────────────────────────────────────────

    private fun clearChecked() {
        val store = currentStore()
        val checked = shopping.filter { it.store == store && it.checked }
        if (checked.isEmpty()) return

        if (store == "Autre") {
            shopping = shopping.filter { !(it.store == store && it.checked) }
            Prefs.saveShopping(this, shopping)
            renderShopping()
            Thread { checked.forEach { Api.deleteShoppingItem(it.id) } }.start()
        } else {
            // Leclerc / Grand Frais: uncheck (item goes back to unchecked in Liste)
            val unchecked = checked.map { it.copy(checked = false, updatedAt = System.currentTimeMillis()) }
            shopping = shopping.map { item -> unchecked.firstOrNull { it.id == item.id } ?: item }
            Prefs.saveShopping(this, shopping)
            renderShopping()
            Thread { unchecked.forEach { Api.updateShoppingItem(it) } }.start()
        }
    }

    private fun showAddItemDialog() {
        val isListe  = section == Section.LISTE
        val isAutre  = section == Section.AUTRE
        val prefill  = etNewItem.text.toString().trim()

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

        var spinnerCat:   Spinner? = null
        var spinnerStore: Spinner? = null

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

            // Store spinner — shown in Liste (ambiguous store) or always for clarity
            if (isListe) {
                val tvStoreLabel = TextView(this).apply {
                    text = "Magasin"
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 12f
                    setPadding(0, 16, 0, 4)
                }
                spinnerStore = Spinner(this)
                val storeOptions = listOf("Leclerc", "Grand Frais")
                spinnerStore.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, storeOptions).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layout.addView(tvStoreLabel)
                layout.addView(spinnerStore)
            }
        }

        val sc = spinnerCat
        val ss = spinnerStore
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_add))
            .setView(layout)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val itemName = etName.text.toString().trim()
                if (itemName.isEmpty()) return@setPositiveButton
                val cat = if (isAutre || sc == null || sc.selectedItemPosition == 0) null
                          else categories[sc.selectedItemPosition - 1]
                val store = when {
                    isAutre             -> "Autre"
                    isListe && ss != null -> listOf("Leclerc", "Grand Frais")[ss.selectedItemPosition]
                    else                -> currentStore()
                }
                createShoppingItem(itemName, cat, store)
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
            .setTitle("Edit item")
            .setView(layout)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                val updated = item.copy(name = newName, updatedAt = System.currentTimeMillis())
                shopping = shopping.map { if (it.id == item.id) updated else it }
                Prefs.saveShopping(this, shopping)
                renderShopping()
                Thread { Api.updateShoppingItem(updated) }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showChangeStoreDialog(item: ShoppingItem) {
        val stores = arrayOf("Leclerc", "Grand Frais", "Autre")
        AlertDialog.Builder(this)
            .setTitle("Move to store")
            .setItems(stores) { _, idx ->
                val newStore = stores[idx]
                if (newStore == item.store) return@setItems
                val updated = item.copy(store = newStore, updatedAt = System.currentTimeMillis())
                shopping = shopping.map { if (it.id == item.id) updated else it }
                Prefs.saveShopping(this, shopping)
                renderShopping()
                Thread { Api.updateShoppingItem(updated) }.start()
            }
            .show()
    }

    private fun createShoppingItem(name: String, category: String?, store: String) {
        val item = ShoppingItem(
            id        = java.util.UUID.randomUUID().toString(),
            name      = name,
            checked   = false,
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
            }
        }.start()
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    private fun onAdd() { startActivity(Intent(this, EventFormActivity::class.java)) }

    private fun refresh() {
        Thread {
            val newEvents     = Api.getEvents()
            val newShopping   = Api.getShopping()
            val newCategories = Api.getCategories()
            runOnUiThread {
                if (newEvents != null) {
                    events = newEvents
                    Prefs.saveEvents(this, events)
                    NotificationScheduler.scheduleAll(this, events, Prefs.notifLeadDays(this))
                }
                if (newShopping != null) {
                    shopping = newShopping
                    Prefs.saveShopping(this, shopping)
                }
                if (newCategories != null) {
                    categories = newCategories
                    Prefs.saveCategories(this, categories)
                }
                renderSection()
            }
        }.start()
    }
}
