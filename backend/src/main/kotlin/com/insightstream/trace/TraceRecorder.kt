package com.insightstream.trace

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque

@Component
class TraceRecorder {
    private val history = ConcurrentLinkedDeque<TraceRecord>()

    fun <T> inSpan(
        name: String,
        service: String,
        attrs: Map<String, String> = emptyMap(),
        block: () -> T,
    ): T {
        val traceId = UUID.randomUUID().toString().replace("-", "")
        val spanId = UUID.randomUUID().toString().replace("-", "").take(16)
        val start = System.nanoTime()

        return try {
            val result = block()
            remember(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = null,
                name = name,
                service = service,
                durationMs = (System.nanoTime() - start) / 1_000_000.0,
                attrs = attrs,
            )
            result
        } catch (e: Exception) {
            remember(
                traceId = traceId,
                spanId = spanId,
                parentSpanId = null,
                name = name,
                service = service,
                durationMs = (System.nanoTime() - start) / 1_000_000.0,
                attrs = attrs + mapOf("error" to (e.message ?: e.javaClass.simpleName)),
            )
            throw e
        }
    }

    fun remember(
        traceId: String,
        spanId: String,
        parentSpanId: String? = null,
        name: String,
        service: String,
        durationMs: Double,
        attrs: Map<String, String> = emptyMap(),
    ) {
        history.addFirst(
            TraceRecord(
                ts = Instant.now().toString(),
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parentSpanId,
                name = name,
                service = service,
                durationMs = durationMs,
                attributes = attrs,
            ),
        )
        while (history.size > 500) history.pollLast()
    }

    fun list(limit: Int = 100): List<TraceRecord> = history.toList().take(limit)
}
