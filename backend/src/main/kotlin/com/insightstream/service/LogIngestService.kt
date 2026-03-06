package com.insightstream.service

import com.insightstream.model.LogEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class LogIngestService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${insight.topics.logs}") private val logsTopic: String,
) {
    fun sendRaw(json: String) {
        kafkaTemplate.send(logsTopic, null, json)
    }

    fun toJson(e: LogEvent): String = JsonUtil.mapper.writeValueAsString(e)

    fun nowIso(): String = Instant.now().toString()
}
