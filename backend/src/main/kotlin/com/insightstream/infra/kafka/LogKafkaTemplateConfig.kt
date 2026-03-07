package com.insightstream.infra.kafka

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
class LogKafkaTemplateConfig {
    @Bean(name = ["kafkaTemplate", "logKafkaTemplate"])
    fun logKafkaTemplate(
        producerFactory: ProducerFactory<String, String>,
    ): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory).apply {
            setAllowNonTransactional(true)
        }
}
