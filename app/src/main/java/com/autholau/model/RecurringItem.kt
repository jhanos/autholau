package com.autholau.model

data class RecurringItem(
    val id:          String,
    val name:        String,
    val category:    String?,
    val stores:      List<String>,  // e.g. ["Leclerc"] or ["Leclerc", "Grand Frais"]
    val periodWeeks: Int,
    val lastBought:  Long,          // epoch ms; 0 = never, triggers immediately on next open
    val updatedAt:   Long = 0L      // epoch ms; used for server-side conflict resolution
)
