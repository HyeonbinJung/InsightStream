package com.insightstream.kafka

import com.insightstream.model.AlertEvent
import com.insightstream.model.LogEvent
import com.insightstream.service.GradientClient
import com.insightstream.service.JsonUtil
import com.insightstream.service.StreamHub
import com.insightstream.service.TraceRecorder
import com.insightstream.service.WindowAggregator
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class LogConsumer(
    private val hub: StreamHub,
    private val gradient: GradientClient,
    private val agg: WindowAggregator,
    private val traces: TraceRecorder,
) {
    @KafkaListener(topics = ["\${insight.topics.logs}"])
    fun onMessage(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        traces.inSpan("consume-log", "backend", mapOf("topic" to record.topic())) {
            val raw = record.value()
            val log = runCatching { JsonUtil.mapper.readValue(raw, LogEvent::class.java) }
                .getOrElse {
                    LogEvent(
                        ts = Instant.now().toString(),
                        service = "unknown",
                        level = "INFO",
                        message = raw,
                        latencyMs = null,
                        stacktrace = null
                    )
                }

            hub.addLog(log)
            agg.add(log)

            gradient.analyze(raw).subscribe { r ->
                if (r.isAnomaly && r.score >= 0.6) {
                    val alert = AlertEvent(
                        ts = log.ts,
                        service = log.service,
                        category = r.category,
                        score = r.score,
                        level = log.level,
                        message = log.message,
                        explanation = r.explanation,
                        status = "NEW"
                    )
                    hub.addAlert(alert)
                }
            }

            ack.acknowledge()
        }
    }
}
