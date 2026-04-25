package com.autholau.model

data class Event(
    val id: String,
    val title: String,
    val date: String,       // ISO-8601: "2026-04-24"
    val time: String? = null, // "HH:mm", null means no specific time (notif fires at 09:00)
    val notify: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
