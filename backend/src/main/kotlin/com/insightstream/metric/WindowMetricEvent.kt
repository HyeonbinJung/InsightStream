package com.insightstream.metric

import com.insightstream.infra.net.IpNetAddress

data class WindowMetricEvent(
    val ts: String,
    val windowSec: Int,
    val service: String,
    val totalCount: Long,
    val errorCount: Long,
    val warnCount: Long,
    val avgLatencyMs: Double,
    val maxLatencyMs: Int,
    val endpointHint: String? = null,
    val failedIpsTop: List<IpNetAddress> = emptyList(),
)
