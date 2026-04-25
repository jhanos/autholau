package com.autholau.model

data class ShoppingItem(
    val id: String,
    val name: String,
    val checked: Boolean = false,
    val category: String? = null,
    val store: String = "Leclerc",   // "Leclerc" | "Grand Frais" | "Autre"
    val updatedAt: Long = System.currentTimeMillis()
)
