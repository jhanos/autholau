package com.autholau.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.autholau.R
import com.autholau.calendar.CalendarSync
import com.autholau.model.Event
import com.autholau.notifications.NotificationScheduler
import com.autholau.storage.Api
import com.autholau.storage.Prefs
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID

class EventFormActivity : Activity() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }

    private lateinit var etTitle:  EditText
    private lateinit var etDate:   EditText
    private lateinit var etTime:   EditText
    private lateinit var cbNotify: CheckBox
    private lateinit var btnSave:  Button
    private lateinit var btnDel:   Button
    private lateinit var tvError:  TextView

    private var existingEvent: Event? = null

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_event_form)

        etTitle  = findViewById(R.id.etTitle)
        etDate   = findViewById(R.id.etDate)
        etTime   = findViewById(R.id.etTime)
        cbNotify = findViewById(R.id.cbNotify)
        btnSave  = findViewById(R.id.btnSave)
        btnDel   = findViewById(R.id.btnDelete)
        tvError  = findViewById(R.id.tvError)

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (eventId != null) {
            existingEvent = Prefs.loadEvents(this).find { it.id == eventId }
            existingEvent?.let {
                etTitle.setText(it.title)
                etDate.setText(it.date)
                etTime.setText(it.time ?: "")
                cbNotify.isChecked = it.notify
            }
            btnDel.visibility = View.VISIBLE
            title = getString(R.string.title_edit_event)
        } else {
            title = getString(R.string.title_new_event)
        }

        btnSave.setOnClickListener { save() }
        btnDel.setOnClickListener  { delete() }
        etDate.setOnClickListener  { showDatePicker() }
        etTime.setOnClickListener  { showTimePicker() }
    }

    private fun showTimePicker() {
        val cal     = Calendar.getInstance()
        val current = etTime.text.toString()
        if (current.isNotEmpty()) {
            try {
                val parts = current.split(":")
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                cal.set(Calendar.MINUTE,      parts[1].toInt())
            } catch (_: Exception) {}
        }
        TimePickerDialog(
            this,
            { _, hour, minute ->
                etTime.setText(String.format("%02d:%02d", hour, minute))
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true   // 24-hour format
        ).show()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val current = etDate.text.toString()
        if (current.isNotEmpty()) {
            try {
                val d = LocalDate.parse(current, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                cal.set(d.year, d.monthValue - 1, d.dayOfMonth)
            } catch (_: Exception) {}
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                etDate.setText(
                    LocalDate.of(year, month + 1, day)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                )
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun save() {
        val titleStr = etTitle.text.toString().trim()
        val dateStr  = etDate.text.toString().trim()

        if (titleStr.isEmpty()) { showError(getString(R.string.err_empty_title)); return }
        if (dateStr.isEmpty())  { showError(getString(R.string.err_empty_date));  return }

        tvError.visibility = View.GONE
        btnSave.isEnabled  = false

        val event = Event(
            id        = existingEvent?.id ?: UUID.randomUUID().toString(),
            title     = titleStr,
            date      = dateStr,
            time      = etTime.text.toString().trim().takeIf { it.isNotEmpty() },
            notify    = cbNotify.isChecked,
            updatedAt = System.currentTimeMillis()
        )

        Thread {
            val result = if (existingEvent == null) Api.createEvent(event) else Api.updateEvent(event)
            runOnUiThread {
                btnSave.isEnabled = true
                if (result != null) {
                    // Update local cache
                    val cached = Prefs.loadEvents(this).toMutableList()
                    val idx = cached.indexOfFirst { it.id == result.id }
                    if (idx >= 0) cached[idx] = result else cached.add(result)
                    Prefs.saveEvents(this, cached)

                    // Reschedule notification
                    if (result.notify) {
                        NotificationScheduler.schedule(this, result, Prefs.notifLeadDays(this))
                    } else {
                        NotificationScheduler.cancel(this, result)
                    }

                    // Sync to device calendar
                    CalendarSync.upsertEvent(this, result)

                    finish()
                } else {
                    showError(getString(R.string.err_network))
                }
            }
        }.start()
    }

    private fun delete() {
        val e = existingEvent ?: return
        btnDel.isEnabled = false
        Thread {
            val ok = Api.deleteEvent(e.id)
            runOnUiThread {
                btnDel.isEnabled = true
                if (ok) {
                    val cached = Prefs.loadEvents(this).filter { it.id != e.id }
                    Prefs.saveEvents(this, cached)
                    NotificationScheduler.cancel(this, e)
                    CalendarSync.deleteEvent(this, e)
                    finish()
                } else {
                    showError(getString(R.string.err_network))
                }
            }
        }.start()
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }
}
