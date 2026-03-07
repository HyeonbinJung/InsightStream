package com.insightstream.incident

data class IncidentReport(
    val id: String,
    val ts: String,
    val title: String,
    val severity: String,
    val category: String,
    val summary: String,
    val impact: String,
    val timeline: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val recommendedActions: List<String> = emptyList(),
    val similarIncidentIds: List<String> = emptyList()
)