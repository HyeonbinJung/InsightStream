package com.insightstream.incident

data class SimilarIncident(
    val id: String,
    val ts: String,
    val title: String,
    val summary: String,
    val severity: String,
    val category: String,
    val score: Double
)