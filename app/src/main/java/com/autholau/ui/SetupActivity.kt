package com.autholau.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.autholau.R
import com.autholau.storage.Api
import com.autholau.storage.Prefs

class SetupActivity : Activity() {

    private lateinit var etUrl:    EditText
    private lateinit var btnConn:  Button
    private lateinit var tvError:  TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_setup)

        etUrl   = findViewById(R.id.etUrl)
        btnConn = findViewById(R.id.btnConnect)
        tvError = findViewById(R.id.tvError)

        etUrl.setText(Prefs.serverUrl(this))

        btnConn.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) { showError(getString(R.string.err_connect)); return@setOnClickListener }
            tvError.visibility = View.GONE
            btnConn.isEnabled  = false
            Thread {
                Api.baseUrl = url
                val ok = Api.healthCheck()
                runOnUiThread {
                    btnConn.isEnabled = true
                    if (ok) {
                        Prefs.saveServerUrl(this, url)
                        Prefs.markFirstLaunchDone(this)
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        showError(getString(R.string.err_connect))
                    }
                }
            }.start()
        }
    }

    private fun showError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }
}
