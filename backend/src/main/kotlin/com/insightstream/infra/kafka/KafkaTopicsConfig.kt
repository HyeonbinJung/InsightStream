package com.insightstream.infra.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicsConfig(
    @Value("\${insight.topics.logs}") private val logsTopic: String,
    @Value("\${insight.topics.alerts}") private val alertsTopic: String,
    @Value("\${insight.topics.metrics:logs.metrics.window}") private val metricsTopic: String,
) {
    @Bean
    fun logsTopicBean(): NewTopic =
        TopicBuilder.name(logsTopic).partitions(1).replicas(1).build()

    @Bean
    fun alertsTopicBean(): NewTopic =
        TopicBuilder.name(alertsTopic).partitions(1).replicas(1).build()

    @Bean
    fun metricsTopicBean(): NewTopic =
        TopicBuilder.name(metricsTopic).partitions(1).replicas(1).build()
}
