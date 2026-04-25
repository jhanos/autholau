package com.autholau.model

data class ShoppingItem(
    val id: String,
    val name: String,
    val checked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
