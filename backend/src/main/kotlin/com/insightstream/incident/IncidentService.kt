package com.insightstream.incident

import com.insightstream.ai.GradientApi
import com.insightstream.alert.AlertEvent
import com.insightstream.infra.json.Jsons
import com.insightstream.window.SummaryEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class IncidentService(
    private val gradient: GradientApi,
    private val store: IncidentStore,
) {
    @Volatile
    private var latestReport: IncidentReport? = null
    private val dedupe = ConcurrentHashMap<String, String>()

    fun latest(): IncidentReport? = latestReport

    fun list(limit: Int = 10): List<IncidentReport> = store.list(limit)

    @Transactional(rollbackFor = [Exception::class])
    fun generate(summary: SummaryEvent, alerts: List<AlertEvent> = emptyList()): IncidentReport {
        val dedupeKey = listOf(summary.ts, summary.category, summary.severity, summary.summary).joinToString("|")
        dedupe[dedupeKey]?.let { existingId ->
            store.get(existingId)?.let { return it }
        }

        val context = mapOf(
            "summary" to summary,
            "alerts" to alerts.take(8),
        )

        val draft = gradient.draftIncident(Jsons.mapper.writeValueAsString(context))
        val report = IncidentReport(
            id = store.nextId(),
            ts = Instant.now().toString(),
            title = draft.title.ifBlank { "${summary.category.replace('_', ' ')} incident" },
            severity = summary.severity,
            category = summary.category,
            summary = summary.summary,
            impact = draft.impact.ifBlank { "Incident affects ${summary.servicesTop.joinToString(", ")}." },
            timeline = if (draft.timeline.isNotEmpty()) draft.timeline else listOf(
                "Window anomaly detected",
                "Category=${summary.category}, severity=${summary.severity}",
            ),
            evidence = (summary.topSignals + draft.evidence + alerts.take(3).map { "${it.service}: ${it.message}" })
                .distinct()
                .take(8),
            recommendedActions = (summary.recommendedActions + draft.recommendedActions)
                .distinct()
                .take(8),
            similarIncidentIds = store.searchSimilar(
                listOf(summary.summary).plus(summary.topSignals).joinToString(" "),
            ).map { it.id }.take(3),
        )

        store.save(report)
        afterCommit {
            latestReport = report
            dedupe[dedupeKey] = report.id
        }
        return report
    }

    private fun afterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    action()
                }
            },
        )
    }
}
