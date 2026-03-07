package com.insightstream.agent

import com.insightstream.ai.GradientApi
import com.insightstream.alert.AlertEvent
import com.insightstream.log.LogEvent
import com.insightstream.window.SummaryEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AskPayload(
    val question: String,
    val mode: String = "log",
    val log: LogEvent? = null,
    val alerts: List<AlertEvent> = emptyList(),
    val summary: SummaryEvent? = null,
    val recentLogs: List<LogEvent> = emptyList(),
)

data class AskReply(
    val answer: String,
)

@RestController
@RequestMapping("/api/agent")
class AgentController(
    private val gradient: GradientApi,
) {
    @PostMapping("/ask")
    fun ask(@RequestBody payload: AskPayload): AskReply {
        val prompt = when (payload.mode) {
            "overview" -> buildString {
                appendLine("You are an SRE assistant.")
                appendLine("Answer briefly and practically.")
                appendLine("Question: ${payload.question}")
                appendLine("Latest summary: ${payload.summary}")
                appendLine("Recent alerts: ${payload.alerts.take(5)}")
                appendLine("Recent logs: ${payload.recentLogs.takeLast(10)}")
            }
            else -> buildString {
                appendLine("You are an SRE assistant.")
                appendLine("Answer briefly and practically.")
                appendLine("Question: ${payload.question}")
                appendLine("Target log: ${payload.log}")
                appendLine("Related summary: ${payload.summary}")
                appendLine("Related alerts: ${payload.alerts.take(3)}")
            }
        }

        return AskReply(gradient.ask(prompt))
    }
}
