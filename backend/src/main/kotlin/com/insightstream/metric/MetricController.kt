package com.insightstream.metric

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/window-metrics")
class MetricController(
    private val metrics: MetricStore,
) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "100") limit: Int): List<WindowMetricEvent> = metrics.list(limit)
}
