package com.insightstream

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class InsightStreamApplication

fun main(args: Array<String>) {
    runApplication<InsightStreamApplication>(*args)
}