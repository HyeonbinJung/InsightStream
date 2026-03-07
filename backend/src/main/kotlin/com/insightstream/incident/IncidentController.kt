package com.insightstream.incident

import com.insightstream.alert.AlertEvent
import com.insightstream.window.SummaryEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/incidents")
class IncidentController(
    private val incidents: IncidentService,
    private val store: IncidentStore,
) {
    @GetMapping("/latest")
    fun latest(): IncidentReport? = incidents.latest()

    @GetMapping
    fun list(@RequestParam(defaultValue = "10") limit: Int): List<IncidentReport> = incidents.list(limit)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): IncidentReport? = store.get(id)

    @PostMapping("/generate")
    fun generate(@RequestBody payload: GeneratePayload): IncidentReport =
        incidents.generate(payload.summary, payload.alerts)
}

data class GeneratePayload(
    val summary: SummaryEvent,
    val alerts: List<AlertEvent> = emptyList(),
)
