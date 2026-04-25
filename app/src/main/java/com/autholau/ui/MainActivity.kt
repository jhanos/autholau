package com.autholau.ui

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
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

    private enum class Section { EVENTS, SHOPPING }

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

    // Data
    private var events:   List<Event>        = emptyList()
    private var shopping: List<ShoppingItem> = emptyList()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_main)

        // Init Api credentials
        Api.baseUrl = Prefs.serverUrl(this)
        Api.token   = Prefs.token(this)

        // Bind views
        drawerPanel      = findViewById(R.id.drawerPanel)
        scrim            = findViewById(R.id.scrim)
        tvTitle          = findViewById(R.id.tvTitle)
        listView         = findViewById(R.id.listView)
        tvEmpty          = findViewById(R.id.tvEmpty)
        rowShoppingInput = findViewById(R.id.rowShoppingInput)
        etNewItem        = findViewById(R.id.etNewItem)
        btnAdd           = findViewById(R.id.btnAdd)

        // Toolbar buttons
        findViewById<ImageButton>(R.id.btnDrawer).setOnClickListener { toggleDrawer() }
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { refresh() }
        btnAdd.setOnClickListener { onAdd() }

        // Shopping inline-add
        findViewById<ImageButton>(R.id.btnAddItem).setOnClickListener { addShoppingItem() }
        etNewItem.setOnEditorActionListener { _, _, _ -> addShoppingItem(); true }

        // Drawer items
        scrim.setOnClickListener { closeDrawer() }
        findViewById<TextView>(R.id.navEvents).setOnClickListener  { switchSection(Section.EVENTS);   closeDrawer() }
        findViewById<TextView>(R.id.navShopping).setOnClickListener { switchSection(Section.SHOPPING); closeDrawer() }
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

        // Load from cache first, then sync
        events   = Prefs.loadEvents(this)
        shopping = Prefs.loadShopping(this)
        renderSection()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Re-render in case EventFormActivity made changes
        events   = Prefs.loadEvents(this)
        shopping = Prefs.loadShopping(this)
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
        ObjectAnimator.ofFloat(drawerPanel, "translationX", -drawerPanel.width.toFloat().coerceAtLeast(800f)).setDuration(200).also {
            it.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    scrim.visibility = View.GONE
                }
            })
        }.start()
    }

    // ── Section switching ─────────────────────────────────────────────────────

    private fun switchSection(s: Section) {
        section = s
        renderSection()
    }

    private fun renderSection() {
        when (section) {
            Section.EVENTS -> {
                tvTitle.text          = getString(R.string.nav_events)
                rowShoppingInput.visibility = View.GONE
                btnAdd.visibility     = View.VISIBLE
                renderEvents()
            }
            Section.SHOPPING -> {
                tvTitle.text          = getString(R.string.nav_shopping)
                rowShoppingInput.visibility = View.VISIBLE
                btnAdd.visibility     = View.GONE
                renderShopping()
            }
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    private fun renderEvents() {
        val fmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val cutoff = today.minusDays(7)

        val visible = events
            .filter { e ->
                val d = try { LocalDate.parse(e.date, fmt) } catch (_: Exception) { return@filter false }
                !d.isBefore(cutoff)           // hide older than 7 days
            }
            .sortedWith(compareBy { e -> try { LocalDate.parse(e.date, fmt) } catch (_: Exception) { LocalDate.MAX } })

        if (visible.isEmpty()) {
            tvEmpty.text       = getString(R.string.empty_events)
            tvEmpty.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        listView.adapter = object : ArrayAdapter<Event>(this, 0, visible) {
            override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
                val row = convert ?: layoutInflater.inflate(R.layout.row_event, parent, false)
                val e   = getItem(pos)!!
                val fmt2 = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val d    = try { LocalDate.parse(e.date, fmt2) } catch (_: Exception) { null }
                val muted = d != null && d.isBefore(today)

                val tvT = row.findViewById<TextView>(R.id.tvEventTitle)
                val tvD = row.findViewById<TextView>(R.id.tvEventDate)
                tvT.text = e.title
                tvD.text = e.date

                val col = if (muted) getColor(R.color.muted) else getColor(R.color.text_primary)
                tvT.setTextColor(col)
                tvD.setTextColor(if (muted) getColor(R.color.muted) else getColor(R.color.text_secondary))

                row.setOnClickListener {
                    val i = Intent(this@MainActivity, EventFormActivity::class.java)
                    i.putExtra(EventFormActivity.EXTRA_EVENT_ID, e.id)
                    startActivity(i)
                }
                return row
            }
        }
    }

    // ── Shopping ──────────────────────────────────────────────────────────────

    private fun renderShopping() {
        val sorted = shopping.sortedWith(compareBy({ it.checked }, { it.name }))

        if (sorted.isEmpty()) {
            tvEmpty.text       = getString(R.string.empty_shopping)
            tvEmpty.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        listView.adapter = object : ArrayAdapter<ShoppingItem>(this, 0, sorted) {
            override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
                val row = convert ?: layoutInflater.inflate(R.layout.row_shopping, parent, false)
                val s   = getItem(pos)!!
                val cb  = row.findViewById<CheckBox>(R.id.cbItem)
                val tv  = row.findViewById<TextView>(R.id.tvItemName)
                val del = row.findViewById<ImageButton>(R.id.btnDeleteItem)

                // Detach listener before setting checked state
                cb.setOnCheckedChangeListener(null)
                cb.isChecked = s.checked
                tv.text      = s.name
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
                        Api.updateShoppingItem(updated)
                    }.start()
                }

                del.setOnClickListener {
                    shopping = shopping.filter { it.id != s.id }
                    Prefs.saveShopping(this@MainActivity, shopping)
                    renderShopping()
                    Thread { Api.deleteShoppingItem(s.id) }.start()
                }

                return row
            }
        }
    }

    private fun addShoppingItem() {
        val name = etNewItem.text.toString().trim()
        if (name.isEmpty()) return
        etNewItem.setText("")
        val item = ShoppingItem(
            id        = java.util.UUID.randomUUID().toString(),
            name      = name,
            checked   = false,
            updatedAt = System.currentTimeMillis()
        )
        shopping = shopping + item
        Prefs.saveShopping(this, shopping)
        renderShopping()
        Thread {
            val created = Api.createShoppingItem(item)
            if (created != null) {
                // Replace local UUID with server-assigned id if different
                shopping = shopping.map { if (it.id == item.id) created else it }
                Prefs.saveShopping(this, shopping)
            }
        }.start()
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    private fun onAdd() {
        startActivity(Intent(this, EventFormActivity::class.java))
    }

    private fun refresh() {
        Thread {
            val newEvents   = Api.getEvents()
            val newShopping = Api.getShopping()
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
                renderSection()
            }
        }.start()
    }
}
