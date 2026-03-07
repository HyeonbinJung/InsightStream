package com.insightstream.log

import com.fasterxml.jackson.databind.JsonNode
import com.insightstream.ai.GradientApi
import com.insightstream.alert.AlertEvent
import com.insightstream.infra.json.Jsons
import com.insightstream.stream.LiveFeed
import com.insightstream.trace.TraceRecorder
import com.insightstream.window.WindowBuffer
import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.Executors

@Component
class LogConsumer(
    private val feed: LiveFeed,
    private val gradient: GradientApi,
    private val window: WindowBuffer,
    private val traces: TraceRecorder,
) {
    private val reviewPool = Executors.newFixedThreadPool(4)

    @KafkaListener(topics = ["\${insight.topics.logs}"])
    fun onMessage(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        traces.inSpan("consume-log", "backend", mapOf("topic" to record.topic())) {
            val raw = record.value()
            val log = parseLog(raw)

            feed.addLog(log)
            window.add(log)
            reviewLater(log, raw)

            ack.acknowledge()
        }
    }

    private fun reviewLater(log: LogEvent, raw: String) {
        reviewPool.execute {
            runCatching { gradient.reviewLog(raw) }
                .onSuccess { review ->
                    if (review.isAnomaly && review.score >= 0.6) {
                        feed.addAlert(
                            AlertEvent(
                                ts = log.ts,
                                service = log.service,
                                category = review.category,
                                score = review.score,
                                level = log.level,
                                message = log.message,
                                explanation = review.explanation,
                                status = "NEW",
                            ),
                        )
                    }
                }
                .onFailure { it.printStackTrace() }
        }
    }

    private fun parseLog(raw: String): LogEvent {
        return runCatching { Jsons.mapper.readValue(raw, LogEvent::class.java) }
            .recoverCatching {
                val node = Jsons.mapper.readTree(raw)
                LogEvent(
                    ts = text(node, "ts") ?: text(node, "timestamp") ?: Instant.now().toString(),
                    service = text(node, "service") ?: text(node, "app") ?: text(node, "serviceName") ?: text(node, "service_name") ?: "unknown",
                    level = (text(node, "level") ?: text(node, "severity") ?: "INFO").uppercase(),
                    message = text(node, "message") ?: text(node, "msg") ?: raw,
                    latencyMs = intValue(node, "latencyMs") ?: intValue(node, "latency") ?: intValue(node, "latency_ms") ?: intValue(node, "responseTimeMs"),
                    stacktrace =
                        text(node, "stacktrace")
                            ?: text(node, "stackTrace")
                            ?: text(node, "stack_trace")
                            ?: text(node, "trace")
                            ?: text(node, "exception")
                            ?: text(node, "exceptionStackTrace")
                            ?: text(node, "exception_stack_trace")
                            ?: text(node, "traceback"),
                )
            }
            .getOrElse {
                LogEvent(
                    ts = Instant.now().toString(),
                    service = "unknown",
                    level = "INFO",
                    message = raw,
                    latencyMs = null,
                    stacktrace = null,
                )
            }
    }

    private fun text(node: JsonNode, field: String): String? {
        val value = node.path(field)
        return if (value.isMissingNode || value.isNull) null else value.asText()
    }

    private fun intValue(node: JsonNode, field: String): Int? {
        val value = node.path(field)
        return if (value.isMissingNode || value.isNull) null else value.asInt()
    }

    @PreDestroy
    fun shutdown() {
        reviewPool.shutdown()
    }
}
