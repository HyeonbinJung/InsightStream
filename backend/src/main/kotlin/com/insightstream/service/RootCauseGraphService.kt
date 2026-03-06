package com.insightstream.service

import com.insightstream.model.*
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class RootCauseGraphService {
    fun build(
        summary: SummaryEvent?,
        alerts: List<AlertEvent>,
        traces: List<TraceRecord>,
        metrics: List<WindowMetricEvent> = emptyList(),
        reports: List<IncidentReport> = emptyList(),
    ): RootCauseGraph {
        if (summary == null) {
            return RootCauseGraph(
                ts = Instant.now().toString(),
                title = "No active incident",
                confidence = 0.2,
                nodes = listOf(RootCauseNode("n0", "No anomaly summary available", "state", 0.2)),
                edges = emptyList(),
                hypothesis = "Generate anomaly summaries first."
            )
        }

        val nodes = mutableListOf<RootCauseNode>()
        val edges = mutableListOf<RootCauseEdge>()
        val incidentId = "incident"
        nodes += RootCauseNode(incidentId, "${summary.category} (${summary.severity})", "incident", 1.0)

        summary.servicesTop.forEachIndexed { idx, svc ->
            val id = "svc-$idx"
            nodes += RootCauseNode(id, svc, "service", 0.95 - idx * 0.08)
            edges += RootCauseEdge(id, incidentId, "affected")
        }

        summary.topSignals.take(5).forEachIndexed { idx, signal ->
            val id = "sig-$idx"
            nodes += RootCauseNode(id, signal, "signal", 0.72)
            edges += RootCauseEdge(id, incidentId, "supports")
        }

        alerts.take(6).forEachIndexed { idx, a ->
            val id = "alert-$idx"
            nodes += RootCauseNode(id, "${a.service}:${a.category}", "alert", a.score.coerceIn(0.0, 1.0))
            edges += RootCauseEdge(id, incidentId, "evidence")
        }

        traces.sortedByDescending { it.durationMs }.take(6).forEachIndexed { idx, t ->
            val id = "trace-$idx"
            val weight = (t.durationMs / 2500.0).coerceIn(0.15, 1.0)
            nodes += RootCauseNode(id, "${t.service}:${t.name}", "trace", weight)
            edges += RootCauseEdge(id, incidentId, "observed")
        }

        metrics.take(6).forEachIndexed { idx, m ->
            val id = "metric-$idx"
            val rate = if (m.totalCount == 0L) 0.0 else m.errorCount.toDouble() / m.totalCount.toDouble()
            nodes += RootCauseNode(id, "${m.service} err=${m.errorCount}/${m.totalCount}", "metric", rate.coerceIn(0.15, 1.0))
            edges += RootCauseEdge(id, incidentId, "correlates")
        }

        reports.firstOrNull()?.let { report ->
            val id = "report-0"
            nodes += RootCauseNode(id, report.title, "report", 0.6)
            edges += RootCauseEdge(id, incidentId, "matches")
        }

        val hypothesis = when (summary.category) {
            "latency" -> "Latency likely originated in a slow downstream dependency, overloaded database, or expensive query path."
            "security" -> "Repeated authentication failures point to burst login abuse or credential stuffing from a small set of sources."
            "resource" -> "Resource saturation signals suggest heap pressure, GC contention, or worker exhaustion."
            "error_spike" -> "A deployment regression, dependency outage, or application bug likely triggered the error spike."
            else -> "Correlated stream, trace, and alert signals indicate an operational anomaly that needs triage."
        }

        val confidence = listOf(
            if (summary.isAnomaly) 0.35 else 0.1,
            minOf(alerts.size, 5) * 0.05,
            minOf(traces.size, 5) * 0.04,
            minOf(metrics.size, 5) * 0.03,
        ).sum().coerceAtMost(0.95)

        return RootCauseGraph(
            ts = Instant.now().toString(),
            title = "Root cause hypothesis",
            confidence = confidence,
            nodes = nodes,
            edges = edges,
            hypothesis = hypothesis
        )
    }
}
