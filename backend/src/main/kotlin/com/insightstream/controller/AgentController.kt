package com.insightstream.controller

import com.insightstream.model.AlertEvent
import com.insightstream.model.LogEvent
import com.insightstream.model.SummaryEvent
import com.insightstream.service.GradientClient
import org.springframework.web.bind.annotation.*

data class AgentAskRequest(
    val question: String,
    val mode: String = "log", // log | overview
    val log: LogEvent? = null,
    val alerts: List<AlertEvent> = emptyList(),
    val summary: SummaryEvent? = null,
    val recentLogs: List<LogEvent> = emptyList()
)

data class AgentAskResponse(
    val answer: String
)

@RestController
@RequestMapping("/api/agent")
class AgentController(
    private val gradientClient: GradientClient
) {
    @PostMapping("/ask")
    fun ask(@RequestBody req: AgentAskRequest): AgentAskResponse {
        val prompt = when (req.mode) {
            "overview" -> buildString {
                appendLine("You are an SRE assistant.")
                appendLine("Answer briefly and practically.")
                appendLine("Question: ${req.question}")
                appendLine("Latest summary: ${req.summary}")
                appendLine("Recent alerts: ${req.alerts.take(5)}")
                appendLine("Recent logs: ${req.recentLogs.takeLast(10)}")
            }
            else -> buildString {
                appendLine("You are an SRE assistant.")
                appendLine("Answer briefly and practically.")
                appendLine("Question: ${req.question}")
                appendLine("Target log: ${req.log}")
                appendLine("Related summary: ${req.summary}")
                appendLine("Related alerts: ${req.alerts.take(3)}")
            }
        }

        val answer = gradientClient.askFreeform(prompt)
        return AgentAskResponse(answer)
    }
}