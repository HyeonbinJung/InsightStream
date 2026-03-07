package com.insightstream.rootcause

import com.insightstream.incident.IncidentService
import com.insightstream.metric.MetricStore
import com.insightstream.stream.LiveFeed
import com.insightstream.trace.TraceRecorder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/root-cause")
class RootCauseController(
    private val feed: LiveFeed,
    private val traces: TraceRecorder,
    private val metrics: MetricStore,
    private val incidents: IncidentService,
    private val graph: RootCauseGraphService,
) {
    @GetMapping
    fun graph(): RootCauseGraph = graph.build(
        summary = feed.summary(),
        alerts = feed.alerts(20),
        traces = traces.list(20),
        metrics = metrics.list(20),
        reports = incidents.list(5),
    )
}
