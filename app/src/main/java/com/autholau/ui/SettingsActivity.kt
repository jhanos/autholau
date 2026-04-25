package com.autholau.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.autholau.R
import com.autholau.storage.Api
import com.autholau.storage.Prefs

class SettingsActivity : Activity() {

    private val leadOptions = intArrayOf(1, 3, 7, 14, 30)
    private val leadLabels  = arrayOf("1 day", "3 days", "1 week", "2 weeks", "1 month")

    private lateinit var listCategories: ListView
    private lateinit var etNewCategory:  EditText
    private lateinit var tvCatError:     TextView
    private var categories: MutableList<String> = mutableListOf()

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)

        val etUrl      = findViewById<EditText>(R.id.etUrl)
        val spinner    = findViewById<Spinner>(R.id.spinnerLead)
        val btnSave    = findViewById<Button>(R.id.btnSave)
        val tvStatus   = findViewById<TextView>(R.id.tvStatus)
        listCategories = findViewById(R.id.listCategories)
        etNewCategory  = findViewById(R.id.etNewCategory)
        tvCatError     = findViewById(R.id.tvCatError)

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
            tvStatus.text       = "Saved"
            tvStatus.visibility = View.VISIBLE
        }

        // Load categories from cache first, then refresh from server
        categories = Prefs.loadCategories(this).toMutableList()
        renderCategories()
        fetchCategories()

        findViewById<Button>(R.id.btnAddCategory).setOnClickListener { addCategory() }
        etNewCategory.setOnEditorActionListener { _, _, _ -> addCategory(); true }
    }

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
        listCategories.adapter = object : ArrayAdapter<String>(this, 0, categories) {
            override fun getView(pos: Int, convert: View?, parent: ViewGroup): View {
                val row = convert ?: layoutInflater.inflate(R.layout.row_category, parent, false)
                val name = getItem(pos)!!
                row.findViewById<TextView>(R.id.tvCategoryName).text = name
                row.findViewById<ImageButton>(R.id.btnDeleteCategory).setOnClickListener {
                    deleteCategory(name)
                }
                return row
            }
        }
        // Fix ListView height inside ScrollView
        setListViewHeightBasedOnItems(listCategories)
    }

    private fun addCategory() {
        val name = etNewCategory.text.toString().trim()
        if (name.isEmpty()) return
        if (categories.any { it.equals(name, ignoreCase = true) }) {
            tvCatError.text       = "Category already exists"
            tvCatError.visibility = View.VISIBLE
            return
        }
        tvCatError.visibility = View.GONE
        etNewCategory.setText("")
        categories.add(name)
        Prefs.saveCategories(this, categories)
        renderCategories()
        Thread {
            Api.createCategory(name)
        }.start()
    }

    private fun deleteCategory(name: String) {
        categories.remove(name)
        Prefs.saveCategories(this, categories)
        renderCategories()
        Thread {
            Api.deleteCategory(name)
        }.start()
    }

    // Makes a ListView inside a ScrollView show all its items without scrolling
    private fun setListViewHeightBasedOnItems(lv: ListView) {
        val adapter = lv.adapter ?: return
        var totalHeight = 0
        for (i in 0 until adapter.count) {
            val item = adapter.getView(i, null, lv)
            item.measure(
                View.MeasureSpec.makeMeasureSpec(lv.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            totalHeight += item.measuredHeight
        }
        val params = lv.layoutParams
        params.height = totalHeight + (lv.dividerHeight * (adapter.count - 1))
        lv.layoutParams = params
        lv.requestLayout()
    }
}
