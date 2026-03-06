package com.insightstream.kafka

import com.insightstream.model.WindowMetricEvent
import com.insightstream.service.JsonUtil
import com.insightstream.service.WindowMetricStore
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class WindowMetricConsumer(
    private val store: WindowMetricStore,
) {
    @KafkaListener(topics = ["\${insight.topics.metrics:logs.metrics.window}"], groupId = "insightstream-metrics-consumer")
    fun onMetric(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
        val metric = runCatching { JsonUtil.mapper.readValue(record.value(), WindowMetricEvent::class.java) }
            .getOrNull()
        if (metric != null) store.add(metric)
        ack.acknowledge()
    }
}
