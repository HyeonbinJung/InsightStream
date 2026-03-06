package com.insightstream.config

import org.apache.kafka.streams.StreamsConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.config.KafkaStreamsConfiguration

@Configuration
@EnableKafkaStreams
class KafkaStreamsConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
) {
    @Bean(name = ["defaultKafkaStreamsConfig"])
    fun kStreamsConfig(): KafkaStreamsConfiguration {
        return KafkaStreamsConfiguration(
            mapOf(
                StreamsConfig.APPLICATION_ID_CONFIG to "insightstream-window-agg",
                StreamsConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                StreamsConfig.PROCESSING_GUARANTEE_CONFIG to StreamsConfig.EXACTLY_ONCE_V2,
                StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG to org.apache.kafka.common.serialization.Serdes.StringSerde::class.java,
                StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG to org.apache.kafka.common.serialization.Serdes.StringSerde::class.java,
            )
        )
    }
}