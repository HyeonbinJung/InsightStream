package com.insightstream.model

data class SimilarIncident(
    val id: String,
    val ts: String,
    val title: String,
    val summary: String,
    val severity: String,
    val category: String,
    val score: Double
)