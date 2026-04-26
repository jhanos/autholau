package com.autholau.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import com.autholau.R
import com.autholau.calendar.CalendarSync
import com.autholau.storage.Api
import com.autholau.storage.Prefs

class SettingsActivity : Activity() {

    private val leadOptions = intArrayOf(1, 3, 7, 14, 30)
    private val leadLabels  = arrayOf("1 jour", "3 jours", "1 semaine", "2 semaines", "1 mois")

    private val PERM_CALENDAR = 42

    private lateinit var listCategories:  LinearLayout
    private lateinit var etNewCategory:   EditText
    private lateinit var tvCatError:      TextView
    private lateinit var switchCalendar:  Switch
    private lateinit var tvCalendarLabel: TextView
    private lateinit var spinnerCalendar: Spinner
    private lateinit var tvCalendarError: TextView
    private var categories: MutableList<String> = mutableListOf()

    // calendars as (id, name) — populated when permission is granted
    private var calendarList: List<Pair<Long, String>> = emptyList()

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)

        val etUrl      = findViewById<EditText>(R.id.etUrl)
        val spinner    = findViewById<Spinner>(R.id.spinnerLead)
        val btnSave    = findViewById<Button>(R.id.btnSave)
        val tvStatus   = findViewById<TextView>(R.id.tvStatus)
        listCategories  = findViewById(R.id.listCategories)
        etNewCategory   = findViewById(R.id.etNewCategory)
        tvCatError      = findViewById(R.id.tvCatError)
        switchCalendar  = findViewById(R.id.switchCalendar)
        tvCalendarLabel = findViewById(R.id.tvCalendarLabel)
        spinnerCalendar = findViewById(R.id.spinnerCalendar)
        tvCalendarError = findViewById(R.id.tvCalendarError)

        etUrl.setText(Prefs.serverUrl(this))

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, leadLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val idx = leadOptions.indexOfFirst { it == Prefs.notifLeadDays(this) }.takeIf { it >= 0 } ?: 2
        spinner.setSelection(idx)

        btnSave.setOnClickListener {
            val url  = etUrl.text.toString().trim()
            val lead = leadOptions[spinner.selectedItemPosition]
            Prefs.saveServerUrl(this, url)
            Prefs.saveNotifLead(this, lead)
            Api.baseUrl = url
            val events = Prefs.loadEvents(this)
            com.autholau.notifications.NotificationScheduler.scheduleAll(this, events, lead)
            tvStatus.text       = getString(R.string.msg_saved)
            tvStatus.visibility = View.VISIBLE
        }

        // ── Categories ───────────────────────────────────────────────────────
        categories = Prefs.loadCategories(this).toMutableList()
        renderCategories()
        fetchCategories()
        findViewById<Button>(R.id.btnAddCategory).setOnClickListener { addCategory() }
        etNewCategory.setOnEditorActionListener { _, _, _ -> addCategory(); true }

        // ── Calendar sync ────────────────────────────────────────────────────
        switchCalendar.isChecked = Prefs.calendarEnabled(this)
        updateCalendarSectionVisibility()

        if (switchCalendar.isChecked && hasCalendarPermission()) {
            loadCalendars()
        }

        switchCalendar.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasCalendarPermission()) {
                    Prefs.saveCalendarEnabled(this, true)
                    loadCalendars()
                    tvCalendarError.visibility = View.GONE
                } else {
                    requestPermissions(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                        PERM_CALENDAR
                    )
                }
            } else {
                Prefs.saveCalendarEnabled(this, false)
                tvCalendarError.visibility = View.GONE
            }
            updateCalendarSectionVisibility()
        }

        spinnerCalendar.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                if (calendarList.isEmpty()) return
                val selected = calendarList[pos]
                // pos 0 = "Automatique" sentinel (id = -1), otherwise real id
                Prefs.saveCalendarId(this@SettingsActivity, selected.first)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ── Debug logs ───────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnShowCalendarLogs).setOnClickListener { showCalendarLogs() }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        if (code == PERM_CALENDAR) {
            if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
                Prefs.saveCalendarEnabled(this, true)
                loadCalendars()
                tvCalendarError.visibility = View.GONE
            } else {
                // Permission denied — revert switch silently
                switchCalendar.setOnCheckedChangeListener(null)
                switchCalendar.isChecked = false
                Prefs.saveCalendarEnabled(this, false)
                tvCalendarError.text       = getString(R.string.err_calendar_perm)
                tvCalendarError.visibility = View.VISIBLE
                // Re-attach listener
                switchCalendar.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                            PERM_CALENDAR
                        )
                    } else {
                        Prefs.saveCalendarEnabled(this, false)
                        tvCalendarError.visibility = View.GONE
                    }
                    updateCalendarSectionVisibility()
                }
            }
            updateCalendarSectionVisibility()
        }
    }

    // ── Calendar helpers ──────────────────────────────────────────────────────

    private fun hasCalendarPermission(): Boolean =
        checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun loadCalendars() {
        Thread {
            val raw = CalendarSync.listCalendars(this)
            // Prepend "Automatique" option (id = -1)
            val list = listOf(Pair(-1L, getString(R.string.calendar_auto))) + raw
            runOnUiThread {
                calendarList = list
                val names = list.map { it.second }.toTypedArray()
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCalendar.adapter = adapter
                // Select saved calendar
                val savedId = Prefs.calendarId(this)
                val savedPos = list.indexOfFirst { it.first == savedId }.takeIf { it >= 0 } ?: 0
                spinnerCalendar.setSelection(savedPos)
            }
        }.start()
    }

    private fun updateCalendarSectionVisibility() {
        val visible = switchCalendar.isChecked
        tvCalendarLabel.visibility  = if (visible) View.VISIBLE else View.GONE
        spinnerCalendar.visibility  = if (visible) View.VISIBLE else View.GONE
    }

    // ── Category helpers ──────────────────────────────────────────────────────

    private fun fetchCategories() {
        Thread {
            val result = Api.getCategories()
            runOnUiThread {
                if (result != null) {
                    categories = result.toMutableList()
                    Prefs.saveCategories(this, categories)
                    renderCategories()
                }
            }
        }.start()
    }

    private fun renderCategories() {
        listCategories.removeAllViews()
        categories.forEachIndexed { index, name ->
            val row = layoutInflater.inflate(R.layout.row_category, listCategories, false)
            row.findViewById<TextView>(R.id.tvCategoryName).text = name
            row.findViewById<ImageButton>(R.id.btnDeleteCategory).setOnClickListener {
                deleteCategory(name)
            }
            if (index > 0) {
                val divider = View(this)
                divider.setBackgroundColor(getColor(R.color.divider))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                listCategories.addView(divider, lp)
            }
            listCategories.addView(row)
        }
    }

    private fun addCategory() {
        val name = etNewCategory.text.toString().trim()
        if (name.isEmpty()) return
        if (categories.any { it.equals(name, ignoreCase = true) }) {
            tvCatError.text       = getString(R.string.err_cat_exists)
            tvCatError.visibility = View.VISIBLE
            return
        }
        tvCatError.visibility = View.GONE
        etNewCategory.setText("")
        categories.add(name)
        Prefs.saveCategories(this, categories)
        renderCategories()
        Thread { Api.createCategory(name) }.start()
    }

    private fun deleteCategory(name: String) {
        categories.remove(name)
        Prefs.saveCategories(this, categories)
        renderCategories()
        Thread { Api.deleteCategory(name) }.start()
    }

    // ── Calendar debug logs ───────────────────────────────────────────────────

    private fun showCalendarLogs() {
        val logs = CalendarSync.getLogs()
        val text = if (logs.isEmpty()) getString(R.string.logs_empty)
                   else logs.joinToString("\n")

        val scrollView = ScrollView(this)
        val tv = TextView(this).apply {
            this.text = text
            textSize  = 11f
            setTextColor(getColor(R.color.text_primary))
            setPadding(32, 24, 32, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        scrollView.addView(tv)

        // Scroll to bottom so latest logs are visible
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_cal_debug))
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(getString(R.string.btn_clear_logs)) { _, _ ->
                CalendarSync.clearLogs()
            }
            .show()
    }
}
