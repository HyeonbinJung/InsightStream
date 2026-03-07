package com.insightstream.metric

import com.insightstream.infra.json.Jsons
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class MetricConsumer(
    private val metrics: MetricStore,
) {
    @KafkaListener(topics = ["\${insight.topics.metrics:logs.metrics.window}"], groupId = "insightstream-metrics-consumer")
    fun onMetric(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        val metric = runCatching { Jsons.mapper.readValue(record.value(), WindowMetricEvent::class.java) }
            .getOrNull()
        if (metric != null) metrics.add(metric)
        ack.acknowledge()
    }
}
