package com.insightstream.service

import com.insightstream.model.WindowMetricEvent
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedDeque

@Component
class WindowMetricStore {
    private val history = ConcurrentLinkedDeque<WindowMetricEvent>()

    fun add(metric: WindowMetricEvent) {
        history.addFirst(metric)
        while (history.size > 500) history.pollLast()
    }

    fun latest(limit: Int = 100): List<WindowMetricEvent> = history.toList().take(limit)
}
