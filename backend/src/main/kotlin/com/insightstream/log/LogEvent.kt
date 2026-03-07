package com.insightstream.log

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class LogEvent(
    @JsonAlias("timestamp")
    val ts: String,
    @JsonAlias("app", "serviceName", "service_name")
    val service: String,
    @JsonAlias("severity")
    val level: String,
    @JsonAlias("msg")
    val message: String,
    @JsonAlias("latency", "latency_ms", "responseTimeMs")
    val latencyMs: Int? = null,
    @JsonAlias("stackTrace", "stack_trace", "trace", "exception", "exceptionStackTrace", "exception_stack_trace", "traceback")
    val stacktrace: String? = null,
)
