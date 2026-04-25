package com.autholau.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.autholau.storage.Api
import com.autholau.storage.Prefs

/**
 * Transparent trampoline: decides where to send the user on cold start.
 *  - First launch   → SetupActivity
 *  - No token       → LoginActivity
 *  - Authenticated  → MainActivity
 */
class RouterActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val next = when {
            Prefs.isFirstLaunch(this)   -> SetupActivity::class.java
            Prefs.token(this) == null   -> LoginActivity::class.java
            else                        -> MainActivity::class.java
        }
        // Pre-load Api state before handing off
        Api.baseUrl = Prefs.serverUrl(this)
        Api.token   = Prefs.token(this)

        startActivity(Intent(this, next))
        finish()
    }
}
