package com.insightstream.controller

import com.insightstream.model.AlertEvent
import com.insightstream.model.IncidentReport
import com.insightstream.model.SummaryEvent
import com.insightstream.service.IncidentMemoryService
import com.insightstream.service.IncidentReportService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/incidents")
class IncidentReportController(
    private val reports: IncidentReportService,
    private val memory: IncidentMemoryService,
) {
    @GetMapping("/latest")
    fun latest(): IncidentReport? = reports.latest()

    @GetMapping
    fun list(@RequestParam(defaultValue = "10") limit: Int): List<IncidentReport> = reports.latestMany(limit)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): IncidentReport? = memory.get(id)

    @PostMapping("/generate")
    fun generate(@RequestBody req: IncidentGenerateRequest): Mono<IncidentReport> =
        reports.generate(req.summary, req.alerts)
}

data class IncidentGenerateRequest(
    val summary: SummaryEvent,
    val alerts: List<AlertEvent> = emptyList()
)
