package com.insightstream.metric

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedDeque

@Component
class MetricStore {
    private val history = ConcurrentLinkedDeque<WindowMetricEvent>()

    fun add(metric: WindowMetricEvent) {
        history.addFirst(metric)
        while (history.size > 500) history.pollLast()
    }

    fun list(limit: Int = 100): List<WindowMetricEvent> = history.toList().take(limit)
}
