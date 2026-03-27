package com.example.openflight4and.model

import kotlinx.serialization.Serializable

@Serializable
data class FlightTicketHistoryEntry(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val amount: Int,
    val balanceAfter: Int,
    val title: String,
    val detail: String
)
