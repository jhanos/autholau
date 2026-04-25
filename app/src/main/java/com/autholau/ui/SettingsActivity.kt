package com.autholau.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import com.autholau.R
import com.autholau.notifications.NotificationScheduler
import com.autholau.storage.Api
import com.autholau.storage.Prefs

class SettingsActivity : Activity() {

    private val leadOptions = intArrayOf(1, 3, 7, 14, 30)
    private val leadLabels  = arrayOf("1 day", "3 days", "1 week", "2 weeks", "1 month")

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)

        val etUrl      = findViewById<EditText>(R.id.etUrl)
        val spinner    = findViewById<Spinner>(R.id.spinnerLead)
        val btnSave    = findViewById<Button>(R.id.btnSave)
        val tvStatus   = findViewById<TextView>(R.id.tvStatus)

        etUrl.setText(Prefs.serverUrl(this))

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, leadLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentLead = Prefs.notifLeadDays(this)
        val idx = leadOptions.indexOfFirst { it == currentLead }.takeIf { it >= 0 } ?: 2
        spinner.setSelection(idx)

        btnSave.setOnClickListener {
            val url  = etUrl.text.toString().trim()
            val lead = leadOptions[spinner.selectedItemPosition]

            Prefs.saveServerUrl(this, url)
            Prefs.saveNotifLead(this, lead)
            Api.baseUrl = url

            // Reschedule all notifications with new lead time
            val events = Prefs.loadEvents(this)
            NotificationScheduler.scheduleAll(this, events, lead)

            tvStatus.text       = "Saved"
            tvStatus.visibility = View.VISIBLE
        }
    }
}
