package com.insightstream.controller

import com.insightstream.model.RootCauseGraph
import com.insightstream.service.IncidentReportService
import com.insightstream.service.RootCauseGraphService
import com.insightstream.service.StreamHub
import com.insightstream.service.TraceRecorder
import com.insightstream.service.WindowMetricStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/root-cause")
class RootCauseController(
    private val hub: StreamHub,
    private val traces: TraceRecorder,
    private val metrics: WindowMetricStore,
    private val reports: IncidentReportService,
    private val graphService: RootCauseGraphService,
) {
    @GetMapping
    fun graph(): RootCauseGraph = graphService.build(
        summary = hub.latestSummary(),
        alerts = hub.latestAlerts(20),
        traces = traces.latest(20),
        metrics = metrics.latest(20),
        reports = reports.latestMany(5)
    )
}
