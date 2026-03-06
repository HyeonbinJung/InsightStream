package com.insightstream.config

import com.insightstream.model.LogEvent
import com.insightstream.model.WindowMetricEvent
import com.insightstream.service.JsonUtil
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
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
    val failedIpsTop: List<String> = emptyList()
) {
    fun add(log: LogEvent): MetricAccumulator {
        val msg = log.message.lowercase()
        val ip = Regex("ip=([0-9.]+)").find(msg)?.groupValues?.getOrNull(1)
        val endpoint = Regex("(GET|POST|PUT|DELETE)\\s+([^\\s]+)").find(log.message)?.groupValues?.getOrNull(2)
        return copy(
            totalCount = totalCount + 1,
            errorCount = errorCount + if (log.level.equals("ERROR", true)) 1 else 0,
            warnCount = warnCount + if (log.level.equals("WARN", true)) 1 else 0,
            latencySum = latencySum + (log.latencyMs ?: 0),
            latencyCount = latencyCount + if (log.latencyMs != null) 1 else 0,
            maxLatencyMs = maxOf(maxLatencyMs, log.latencyMs ?: 0),
            lastTs = log.ts,
            endpointHint = endpoint ?: endpointHint,
            failedIpsTop = (failedIpsTop + listOfNotNull(ip)).takeLast(5)
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
        failedIpsTop = failedIpsTop.distinct()
    )
}

@Configuration
class WindowMetricTopology(
    @Value("\${insight.topics.logs}") private val logsTopic: String,
    @Value("\${insight.topics.metrics:logs.metrics.window}") private val metricsTopic: String,
    @Value("\${insight.windowing.shortWindowSec:5}") private val shortWindowSec: Long,
    @Value("\${insight.windowing.longWindowSec:30}") private val longWindowSec: Long,
) {
    @Bean
    fun windowMetricsStream(builder: StreamsBuilder): KStream<String, String> {
        val base = builder.stream(logsTopic, Consumed.with(Serdes.String(), Serdes.String()))
            .mapValues { raw ->
                runCatching { JsonUtil.mapper.readValue(raw, LogEvent::class.java) }
                    .getOrElse { LogEvent(Instant.now().toString(), "unknown", "INFO", raw, null, null) }
            }
            .selectKey { _, log -> log.service }

        val shortMetrics = aggregateWindow(base, shortWindowSec)
        val longMetrics = aggregateWindow(base, longWindowSec)
        val merged = shortMetrics.merge(longMetrics)
        merged.to(metricsTopic, Produced.with(Serdes.String(), Serdes.String()))
        return merged
    }

    private fun aggregateWindow(stream: KStream<String, LogEvent>, windowSec: Long): KStream<String, String> =
        stream
            .groupByKey(Grouped.with(Serdes.String(), logEventSerde()))
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(windowSec)))
            .aggregate(
                { MetricAccumulator(service = "unknown") },
                { key, value, agg ->
                    val base = if (agg.service == "unknown") agg.copy(service = key) else agg
                    base.add(value)
                },
                Materialized.with(Serdes.String(), metricAccumulatorSerde())
            )
            .toStream()
            .map { windowedKey, acc ->
                val evt = acc.toEvent(windowSec.toInt())
                KeyValue.pair(windowedKey.key(), JsonUtil.mapper.writeValueAsString(evt))
            }

    private fun logEventSerde() = jsonSerde(LogEvent::class.java)
    private fun metricAccumulatorSerde() = jsonSerde(MetricAccumulator::class.java)

    private fun <T> jsonSerde(clazz: Class<T>): org.apache.kafka.common.serialization.Serde<T> {
        val serializer = org.apache.kafka.common.serialization.Serializer<T> { _, data ->
            if (data == null) null else JsonUtil.mapper.writeValueAsBytes(data)
        }
        val deserializer = org.apache.kafka.common.serialization.Deserializer<T> { _, bytes ->
            if (bytes == null) null else JsonUtil.mapper.readValue(bytes, clazz)
        }
        return Serdes.serdeFrom(serializer, deserializer)
    }
}
