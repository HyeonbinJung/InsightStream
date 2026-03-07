package com.insightstream.metric

import com.insightstream.infra.json.Jsons
import com.insightstream.infra.net.IpNetAddress
import com.insightstream.log.LogEvent
import com.insightstream.window.WindowBuffer
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

private data class MetricAccumulator(
    val service: String,
    val totalCount: Long = 0,
    val errorCount: Long = 0,
    val warnCount: Long = 0,
    val latencySum: Long = 0,
    val latencyCount: Long = 0,
    val maxLatencyMs: Int = 0,
    val lastTs: String = Instant.now().toString(),
    val endpointHint: String? = null,
    val failedIpsTop: List<IpNetAddress> = emptyList(),
) {
    fun add(log: LogEvent): MetricAccumulator {
        val ip = Regex("""\bip=([^\s]+)""").find(log.message)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { raw -> runCatching { IpNetAddress.of(raw) }.getOrNull() }

        val endpoint = Regex("""\b(GET|POST|PUT|DELETE|PATCH)\s+([^\s]+)""")
            .find(log.message)
            ?.groupValues
            ?.getOrNull(2)

        return copy(
            totalCount = totalCount + 1,
            errorCount = errorCount + if (log.level.equals("ERROR", true)) 1 else 0,
            warnCount = warnCount + if (log.level.equals("WARN", true)) 1 else 0,
            latencySum = latencySum + (log.latencyMs ?: 0),
            latencyCount = latencyCount + if (log.latencyMs != null) 1 else 0,
            maxLatencyMs = maxOf(maxLatencyMs, log.latencyMs ?: 0),
            lastTs = log.ts,
            endpointHint = endpoint ?: endpointHint,
            failedIpsTop = (failedIpsTop + listOfNotNull(ip)).takeLast(5),
        )
    }

    fun toEvent(windowSec: Int): WindowMetricEvent = WindowMetricEvent(
        ts = lastTs,
        windowSec = windowSec,
        service = service,
        totalCount = totalCount,
        errorCount = errorCount,
        warnCount = warnCount,
        avgLatencyMs = if (latencyCount == 0L) 0.0 else latencySum.toDouble() / latencyCount,
        maxLatencyMs = maxLatencyMs,
        endpointHint = endpointHint,
        failedIpsTop = failedIpsTop.distinct(),
    )
}

@Component
class MetricPublisher(
    private val window: WindowBuffer,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${insight.topics.metrics:logs.metrics.window}") private val metricsTopic: String,
    @Value("\${insight.windowing.shortWindowSec:5}") private val shortWindowSec: Int,
    @Value("\${insight.windowing.longWindowSec:30}") private val longWindowSec: Int,
) {
    @Scheduled(fixedRateString = "\${insight.windowing.metricsPublishRateMs:5000}")
    fun publish() {
        val maxWindowSec = maxOf(shortWindowSec, longWindowSec)
        val snapshot = window.snapshot(maxWindowSec)
        if (snapshot.logs.isEmpty()) return

        val payloads = buildMetrics(snapshot.logs, shortWindowSec) + buildMetrics(snapshot.logs, longWindowSec)
        if (payloads.isEmpty()) return

        kafkaTemplate.executeInTransaction { ops ->
            payloads.forEach { metric ->
                ops.send(metricsTopic, metric.service, Jsons.mapper.writeValueAsString(metric)).get()
            }
            true
        }
    }

    private fun buildMetrics(logs: List<LogEvent>, windowSec: Int): List<WindowMetricEvent> {
        val cutoff = Instant.now().minusSeconds(windowSec.toLong())

        return logs.asSequence()
            .filter { log ->
                val ts = runCatching { Instant.parse(log.ts) }.getOrNull() ?: return@filter false
                !ts.isBefore(cutoff)
            }
            .groupBy { it.service }
            .map { (service, serviceLogs) ->
                serviceLogs.fold(MetricAccumulator(service = service)) { acc, log -> acc.add(log) }
                    .toEvent(windowSec)
            }
            .sortedWith(compareByDescending<WindowMetricEvent> { it.windowSec }.thenBy { it.service })
    }
}
