package com.insightstream.window

import com.insightstream.ai.GradientApi
import com.insightstream.incident.IncidentService
import com.insightstream.infra.json.Jsons
import com.insightstream.stream.LiveFeed
import com.insightstream.trace.TraceRecorder
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Component
class WindowAnalysisJob(
    private val window: WindowBuffer,
    private val gradient: GradientApi,
    private val feed: LiveFeed,
    private val incidents: IncidentService,
    private val traces: TraceRecorder,
) {
    private val windowSec = 30
    private val inFlight = AtomicBoolean(false)

    @Scheduled(fixedRate = 10_000)
    fun run() {
        if (!inFlight.compareAndSet(false, true)) return

        try {
            val snapshot = window.snapshot(windowSec)
            val logs = snapshot.logs
            if (logs.isEmpty()) return

            val errorCount = logs.count { it.level.equals("ERROR", true) }
            val warnCount = logs.count { it.level.equals("WARN", true) }
            val servicesTop = logs.groupBy { it.service }
                .entries
                .sortedByDescending { it.value.size }
                .take(3)
                .map { "${it.key}(${it.value.size})" }

            val windowJson = Jsons.mapper.writeValueAsString(
                mapOf(
                    "ts" to snapshot.ts,
                    "window_sec" to windowSec,
                    "counts" to mapOf(
                        "total" to logs.size,
                        "error" to errorCount,
                        "warn" to warnCount,
                    ),
                    "services_top" to servicesTop,
                    "sample_logs" to logs.takeLast(10),
                ),
            )

            val review = traces.inSpan(
                name = "window-analysis",
                service = "backend",
                attrs = mapOf("windowSec" to windowSec.toString(), "logCount" to logs.size.toString()),
            ) {
                gradient.reviewWindow(windowJson)
            }

            val summary = SummaryEvent(
                ts = Instant.now().toString(),
                windowSec = windowSec,
                totalLogs = logs.size,
                errorCount = errorCount,
                warnCount = warnCount,
                servicesTop = servicesTop,
                isAnomaly = review.isAnomaly,
                severity = review.severity,
                category = review.category,
                summary = review.summary,
                topSignals = review.topSignals.take(5),
                recommendedActions = review.recommendedActions.take(6),
            )

            feed.addSummary(summary)
            if (summary.isAnomaly) {
                runCatching {
                    incidents.generate(summary, feed.alerts(10))
                }.onFailure { it.printStackTrace() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            feed.addSummary(
                SummaryEvent(
                    ts = Instant.now().toString(),
                    windowSec = windowSec,
                    totalLogs = 0,
                    errorCount = 0,
                    warnCount = 0,
                    servicesTop = emptyList(),
                    isAnomaly = false,
                    severity = "low",
                    category = "other",
                    summary = "AI window analysis failed (timeout/transient). Showing fallback summary.",
                    topSignals = listOf("Gradient request timeout or transient error"),
                    recommendedActions = listOf(
                        "Try again",
                        "Reduce sample size / increase timeout",
                        "Check Gradient rate limits",
                    ),
                ),
            )
        } finally {
            inFlight.set(false)
        }
    }
}
