package com.insightstream.log

import com.insightstream.infra.json.Jsons
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class LogPublisher(
    @Qualifier("logKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${insight.topics.logs}") private val logsTopic: String,
) {
    fun send(json: String) {
        kafkaTemplate.send(logsTopic, null, json)
    }

    fun sendBatch(payloads: List<String>) {
        if (payloads.isEmpty()) return
        payloads.forEach(::send)
    }

    fun toJson(event: LogEvent): String = Jsons.mapper.writeValueAsString(event)

    fun nowIso(): String = Instant.now().toString()
}
