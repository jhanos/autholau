package com.autholau.model

data class RecurringItem(
    val name:        String,
    val category:    String?,
    val stores:      List<String>,  // e.g. ["Leclerc"] or ["Leclerc", "Grand Frais"]
    val periodWeeks: Int,
    val lastBought:  Long           // epoch ms; 0 = never, triggers immediately on next open
)
