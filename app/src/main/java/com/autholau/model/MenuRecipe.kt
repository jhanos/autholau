package com.autholau.model

data class MenuRecipe(
    val id: String,
    val name: String,
    val category: String,            // "plat", "fruit", "oleagineux"
    val ingredients: List<Ingredient>, // only used for plat
    val updatedAt: Long
)
