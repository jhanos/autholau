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

class LoginActivity : Activity() {

    private lateinit var etPwd:   EditText
    private lateinit var btnLogin: Button
    private lateinit var tvError:  TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_login)

        etPwd    = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvError  = findViewById(R.id.tvError)

        // Configure Api from prefs
        Api.baseUrl = Prefs.serverUrl(this)

        btnLogin.setOnClickListener {
            val pwd = etPwd.text.toString()
            if (pwd.isEmpty()) return@setOnClickListener
            tvError.visibility = View.GONE
            btnLogin.isEnabled = false
            Thread {
                val tok = Api.login(pwd)
                runOnUiThread {
                    btnLogin.isEnabled = true
                    if (tok != null) {
                        Prefs.saveToken(this, tok)
                        Api.token = tok
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        tvError.text       = getString(R.string.err_login)
                        tvError.visibility = View.VISIBLE
                    }
                }
            }.start()
        }
    }
}
