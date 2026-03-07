package com.insightstream.alert

data class AlertEvent(
    val ts: String,
    val service: String,
    val category: String,
    val score: Double,
    val level: String,
    val message: String,
    val explanation: String,
    val status: String = "NEW"
)
