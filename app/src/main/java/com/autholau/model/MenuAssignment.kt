package com.autholau.model

data class MenuAssignment(
    val id: String,
    val date: String,            // "YYYY-MM-DD"
    val platId: String?,
    val fruitId: String?,
    val oleagineuxId: String?,
    val updatedAt: Long
)
