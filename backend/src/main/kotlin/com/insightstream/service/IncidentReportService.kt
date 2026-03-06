package com.insightstream.service

import com.insightstream.model.AlertEvent
import com.insightstream.model.IncidentReport
import com.insightstream.model.SummaryEvent
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class IncidentReportService(
    private val gradient: GradientClient,
    private val memory: IncidentMemoryService,
) {
    @Volatile
    private var latest: IncidentReport? = null
    private val dedupe = ConcurrentHashMap<String, String>()

    fun latest(): IncidentReport? = latest

    fun latestMany(limit: Int = 10): List<IncidentReport> = memory.latest(limit)

    fun generate(summary: SummaryEvent, alerts: List<AlertEvent> = emptyList()): Mono<IncidentReport> {
        val dedupeKey = listOf(summary.ts, summary.category, summary.severity, summary.summary).joinToString("|")
        dedupe[dedupeKey]?.let { existingId ->
            memory.get(existingId)?.let { return Mono.just(it) }
        }

        val ctx = mapOf(
            "summary" to summary,
            "alerts" to alerts.take(8)
        )
        val ctxJson = JsonUtil.mapper.writeValueAsString(ctx)
        return gradient.generateIncidentReport(ctxJson)
            .map { ai ->
                val report = IncidentReport(
                    id = memory.allocateId(),
                    ts = Instant.now().toString(),
                    title = ai.title.ifBlank { "${summary.category.replace('_', ' ')} incident" },
                    severity = summary.severity,
                    category = summary.category,
                    summary = summary.summary,
                    impact = ai.impact.ifBlank { "Incident affects ${summary.servicesTop.joinToString(", ")}." },
                    timeline = if (ai.timeline.isNotEmpty()) ai.timeline else listOf(
                        "Window anomaly detected",
                        "Category=${summary.category}, severity=${summary.severity}"
                    ),
                    evidence = (summary.topSignals + ai.evidence + alerts.take(3).map { "${it.service}: ${it.message}" }).distinct().take(8),
                    recommendedActions = (summary.recommendedActions + ai.recommendedActions).distinct().take(8),
                    similarIncidentIds = memory.findSimilar(listOf(summary.summary).plus(summary.topSignals).joinToString(" ")).map { it.id }.take(3)
                )
                latest = report
                memory.remember(report)
                dedupe[dedupeKey] = report.id
                report
            }
    }
}
