package com.insightstream.model

data class LogEvent(
    val ts: String,
    val service: String,
    val level: String,
    val message: String,
    val latencyMs: Int? = null,
    // Optional: multiline stack trace or exception details for error logs.
    val stacktrace: String? = null
)
