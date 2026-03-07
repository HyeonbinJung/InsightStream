package com.insightstream.trace

data class TraceRecord(
    val ts: String,
    val traceId: String,
    val spanId: String,
    val parentSpanId: String? = null,
    val name: String,
    val service: String,
    val durationMs: Double,
    val attributes: Map<String, String> = emptyMap()
)
