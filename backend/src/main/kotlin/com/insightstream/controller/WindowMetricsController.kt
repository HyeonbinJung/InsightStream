package com.insightstream.controller

import com.insightstream.model.WindowMetricEvent
import com.insightstream.service.WindowMetricStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/window-metrics")
class WindowMetricsController(
    private val store: WindowMetricStore,
) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "100") limit: Int): List<WindowMetricEvent> = store.latest(limit)
}
