package com.insightstream.model

data class SummaryEvent(
    val ts: String,
    val windowSec: Int,
    val totalLogs: Int,
    val errorCount: Int,
    val warnCount: Int,
    val servicesTop: List<String>,
    val isAnomaly: Boolean,
    val severity: String,              // low|medium|high|critical
    val category: String,              // error_spike|latency|security|resource|other|normal
    val summary: String,
    val topSignals: List<String>,
    val recommendedActions: List<String>
)