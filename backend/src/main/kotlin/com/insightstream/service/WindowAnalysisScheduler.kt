package com.insightstream.service

import com.insightstream.model.SummaryEvent
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Component
class WindowAnalysisScheduler(
    private val agg: WindowAggregator,
    private val gradient: GradientClient,
    private val hub: StreamHub,
    private val reports: IncidentReportService,
    private val traces: TraceRecorder,
) {
    private val windowSec = 30
    private val inFlight = AtomicBoolean(false)

    @Scheduled(fixedRate = 10000)
    fun analyzeWindow() {
        if (!inFlight.compareAndSet(false, true)) return

        try {
            val snap = agg.snapshot(windowSec)
            val logs = snap.logs
            if (logs.isEmpty()) {
                inFlight.set(false)
                return
            }

            val errorCount = logs.count { it.level.equals("ERROR", true) }
            val warnCount = logs.count { it.level.equals("WARN", true) }

            val servicesTop = logs.groupBy { it.service }
                .entries
                .sortedByDescending { it.value.size }
                .take(3)
                .map { "${it.key}(${it.value.size})" }

            val sample = logs.takeLast(10)

            val windowJson = JsonUtil.mapper.writeValueAsString(
                mapOf(
                    "ts" to snap.ts,
                    "window_sec" to windowSec,
                    "counts" to mapOf(
                        "total" to logs.size,
                        "error" to errorCount,
                        "warn" to warnCount
                    ),
                    "services_top" to servicesTop,
                    "sample_logs" to sample
                )
            )

            traces.inSpan(
                name = "window-analysis",
                service = "backend",
                attrs = mapOf("windowSec" to windowSec.toString(), "logCount" to logs.size.toString())
            ) {
                gradient.analyzeWindow(windowJson).subscribe(
                    { r ->
                        val summary = SummaryEvent(
                            ts = Instant.now().toString(),
                            windowSec = windowSec,
                            totalLogs = logs.size,
                            errorCount = errorCount,
                            warnCount = warnCount,
                            servicesTop = servicesTop,
                            isAnomaly = r.isAnomaly,
                            severity = r.severity,
                            category = r.category,
                            summary = r.summary,
                            topSignals = r.topSignals.take(5),
                            recommendedActions = r.recommendedActions.take(6)
                        )
                        hub.addSummary(summary)
                        if (summary.isAnomaly) {
                            reports.generate(summary, hub.latestAlerts(10)).subscribe()
                        }
                        inFlight.set(false)
                    },
                    { e ->
                        e.printStackTrace()
                        hub.addSummary(
                            SummaryEvent(
                                ts = Instant.now().toString(),
                                windowSec = windowSec,
                                totalLogs = logs.size,
                                errorCount = errorCount,
                                warnCount = warnCount,
                                servicesTop = servicesTop,
                                isAnomaly = false,
                                severity = "low",
                                category = "other",
                                summary = "AI window analysis failed (timeout/transient). Showing fallback summary.",
                                topSignals = listOf("Gradient request timeout or transient error"),
                                recommendedActions = listOf(
                                    "Try again",
                                    "Reduce sample size / increase timeout",
                                    "Check Gradient rate limits"
                                )
                            )
                        )
                        inFlight.set(false)
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            inFlight.set(false)
        }
    }
}
