package com.insightstream.ai

import com.insightstream.infra.json.Jsons
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class LogReview(
    val isAnomaly: Boolean,
    val score: Double,
    val category: String,
    val explanation: String,
)

data class WindowReview(
    val isAnomaly: Boolean,
    val severity: String,
    val category: String,
    val summary: String,
    val topSignals: List<String>,
    val recommendedActions: List<String>,
)

data class AgentReply(
    val answer: String,
    val steps: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val cautions: List<String> = emptyList(),
)

data class IncidentDraft(
    val title: String,
    val impact: String,
    val timeline: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val recommendedActions: List<String> = emptyList(),
)

@Component
class GradientApi(
    @Value("\${insight.gradient.baseUrl}") private val baseUrl: String,
    @Value("\${insight.gradient.model}") private val model: String,
    @Value("\${insight.gradient.accessKey}") private val accessKey: String,
) {
    private val client: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(5_000)
                setReadTimeout(15_000)
            },
        )
        .build()

    fun reviewLog(logJson: String): LogReview {
        if (accessKey.isBlank()) {
            return heuristicFromJson(
                logJson,
                "DO_MODEL_ACCESS_KEY not set; heuristic detection used for demo.",
            )
        }

        val prompt = """
You are an SRE log analyst.
Given ONE log line as JSON, decide if it is anomalous.

Return ONLY strict JSON with keys:
{"is_anomaly":boolean,"score":number,"category":string,"explanation":string}

- score: 0.0 (normal) to 1.0 (highly anomalous)
- category: one of ["error_spike","latency","security","resource","other","normal"]

Log JSON:
$logJson
""".trimIndent()

        return runCatching {
            val content = callModel(prompt, maxTokens = 180, temperature = 0.0)
            parseLogReview(content).getOrElse {
                println("Gradient(log) model content parse failed preview=" + content.take(300))
                heuristicFromJson(logJson, "Fallback heuristic (parse failed).")
            }
        }.getOrElse { e ->
            e.printStackTrace()
            heuristicFromJson(logJson, "Fallback heuristic (Gradient call exception).")
        }
    }

    fun reviewWindow(windowJson: String): WindowReview {
        if (accessKey.isBlank()) {
            return WindowReview(
                isAnomaly = false,
                severity = "low",
                category = "normal",
                summary = "AI key not set. Window analysis fallback in effect.",
                topSignals = emptyList(),
                recommendedActions = listOf("Set DO_MODEL_ACCESS_KEY to enable Gradient Serverless analysis."),
            )
        }

        val prompt = """
You are an on-call SRE assistant.

Given a JSON window summary (counts + sample logs), decide if it indicates an incident/anomaly.

Return ONLY strict JSON:
{
 "is_anomaly": boolean,
 "severity": "low"|"medium"|"high"|"critical",
 "category": "error_spike"|"latency"|"security"|"resource"|"other"|"normal",
 "summary": string,
 "top_signals": string[],
 "recommended_actions": string[]
}

Constraints:
- summary: 1 sentence
- top_signals: max 4
- recommended_actions: max 4, concrete steps

WINDOW JSON:
$windowJson
""".trimIndent()

        return runCatching {
            val content = callModel(prompt, maxTokens = 220, temperature = 0.0)
            parseWindowReview(content).getOrElse {
                println("Gradient(window) content parse failed preview=" + content.take(400))
                WindowReview(
                    isAnomaly = false,
                    severity = "low",
                    category = "other",
                    summary = "Model response didn't include valid JSON; using fallback.",
                    topSignals = listOf(content.take(200)),
                    recommendedActions = emptyList(),
                )
            }
        }.getOrElse { e ->
            e.printStackTrace()
            WindowReview(
                isAnomaly = false,
                severity = "low",
                category = "other",
                summary = "Gradient window analysis exception; using fallback.",
                topSignals = emptyList(),
                recommendedActions = emptyList(),
            )
        }
    }

    fun ask(prompt: String): String {
        if (accessKey.isBlank()) {
            return "AI key is not configured. Please set DO_MODEL_ACCESS_KEY."
        }

        return runCatching {
            callModel(prompt, maxTokens = 320, temperature = 0.2).ifBlank { "No answer." }
        }.getOrElse {
            "AI request failed."
        }
    }

    fun askAgent(prompt: String): AgentReply {
        if (accessKey.isBlank()) {
            return AgentReply(
                answer = "AI key is not configured. Please set DO_MODEL_ACCESS_KEY.",
                cautions = listOf("Using fallback because no model access key is set."),
            )
        }

        val structuredPrompt = """
You are an SRE assistant.

Return ONLY strict JSON:
{
  "answer": string,
  "steps": string[],
  "commands": string[],
  "cautions": string[]
}

Keep the answer practical and concise.

QUESTION:
$prompt
""".trimIndent()

        return runCatching {
            val content = callModel(structuredPrompt, maxTokens = 320, temperature = 0.1)
            parseAgentReply(content).getOrElse {
                AgentReply(
                    answer = content.take(900).ifBlank {
                        "Model response didn't include valid JSON; using fallback."
                    },
                    cautions = listOf("Model output was not strict JSON."),
                )
            }
        }.getOrElse {
            AgentReply(
                answer = "AI request failed.",
                cautions = listOf("Exception during model call."),
            )
        }
    }

    fun draftIncident(contextJson: String): IncidentDraft {
        if (accessKey.isBlank()) {
            return IncidentDraft(
                title = "Incident report fallback",
                impact = "AI key is not configured.",
                timeline = listOf("Window anomaly detected"),
                evidence = listOf("No DO_MODEL_ACCESS_KEY configured"),
                recommendedActions = listOf("Set DO_MODEL_ACCESS_KEY"),
            )
        }

        val prompt = """
You are an incident commander assistant.

Given this incident context JSON, produce a concise incident report.

Return ONLY strict JSON:
{
  "title": string,
  "impact": string,
  "timeline": string[],
  "evidence": string[],
  "recommended_actions": string[]
}

Constraints:
- title: short
- impact: 1 sentence
- timeline: max 5
- evidence: max 5
- recommended_actions: max 5

INCIDENT CONTEXT JSON:
$contextJson
""".trimIndent()

        return runCatching {
            val content = callModel(prompt, maxTokens = 320, temperature = 0.1)
            parseIncidentDraft(content).getOrElse {
                IncidentDraft(
                    title = "Incident report fallback",
                    impact = "Model output was not valid JSON.",
                    evidence = listOf(content.take(200)),
                    recommendedActions = listOf("Retry with smaller context"),
                )
            }
        }.getOrElse {
            IncidentDraft(
                title = "Incident report fallback",
                impact = "AI request failed.",
                evidence = listOf("Exception during model call"),
                recommendedActions = listOf("Retry request"),
            )
        }
    }

    private fun callModel(prompt: String, maxTokens: Int, temperature: Double): String {
        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to maxTokens,
            "temperature" to temperature,
        )

        val raw = client.post()
            .uri("/v1/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $accessKey")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: ""

        val envelope = runCatching {
            @Suppress("UNCHECKED_CAST")
            Jsons.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
        }.getOrElse { e ->
            e.printStackTrace()
            error("Could not parse model response envelope.")
        }

        return extractContent(envelope)
    }

    private fun extractContent(response: Map<String, Any?>): String {
        val choices = response["choices"] as? List<*>
        return (((choices?.firstOrNull() as? Map<*, *>)?.get("message") as? Map<*, *>)?.get("content") as? String)
            ?: ""
    }

    private fun parseLogReview(text: String): Result<LogReview> = runCatching {
        val node = Jsons.mapper.readTree(extractJsonObject(text))
        LogReview(
            isAnomaly = node["is_anomaly"]?.asBoolean(false) ?: false,
            score = node["score"]?.asDouble(0.0) ?: 0.0,
            category = node["category"]?.asText("other") ?: "other",
            explanation = node["explanation"]?.asText("No explanation") ?: "No explanation",
        )
    }

    private fun parseWindowReview(text: String): Result<WindowReview> = runCatching {
        val node = Jsons.mapper.readTree(extractJsonObject(text))
        WindowReview(
            isAnomaly = node["is_anomaly"]?.asBoolean(false) ?: false,
            severity = node["severity"]?.asText("low") ?: "low",
            category = node["category"]?.asText("other") ?: "other",
            summary = node["summary"]?.asText("") ?: "",
            topSignals = node["top_signals"]?.map { it.asText() }?.toList() ?: emptyList(),
            recommendedActions = node["recommended_actions"]?.map { it.asText() }?.toList() ?: emptyList(),
        )
    }

    private fun parseAgentReply(text: String): Result<AgentReply> = runCatching {
        val node = Jsons.mapper.readTree(extractJsonObject(text))
        AgentReply(
            answer = node["answer"]?.asText("") ?: "",
            steps = node["steps"]?.map { it.asText() }?.toList() ?: emptyList(),
            commands = node["commands"]?.map { it.asText() }?.toList() ?: emptyList(),
            cautions = node["cautions"]?.map { it.asText() }?.toList() ?: emptyList(),
        )
    }

    private fun parseIncidentDraft(text: String): Result<IncidentDraft> = runCatching {
        val node = Jsons.mapper.readTree(extractJsonObject(text))
        IncidentDraft(
            title = node["title"]?.asText("Incident report") ?: "Incident report",
            impact = node["impact"]?.asText("") ?: "",
            timeline = node["timeline"]?.map { it.asText() }?.toList() ?: emptyList(),
            evidence = node["evidence"]?.map { it.asText() }?.toList() ?: emptyList(),
            recommendedActions = node["recommended_actions"]?.map { it.asText() }?.toList() ?: emptyList(),
        )
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON object found" }
        return text.substring(start, end + 1)
    }

    private fun heuristicFromJson(logJson: String, note: String): LogReview {
        return runCatching {
            val node = Jsons.mapper.readTree(logJson)
            val level = node.path("level").asText("").uppercase()
            val service = node.path("service").asText("").lowercase()
            val message = node.path("message").asText("").lowercase()
            val latencyMs = node.path("latencyMs").asInt(0)

            val isErr = level == "ERROR" || message.contains("exception") || message.contains(" 500 ")
            val isAuth = service == "auth" || message.contains("failed login") || message.contains("bruteforce")
            val isLatency = latencyMs >= 1500 || message.contains("slow") || message.contains("latenc")
            val isResource = message.contains("heap") || message.contains("gc") || message.contains("oom") || message.contains("memory")

            val category = when {
                isAuth -> "security"
                isLatency -> "latency"
                isResource -> "resource"
                isErr -> "error_spike"
                else -> "normal"
            }

            val score = when (category) {
                "normal" -> 0.08
                "latency" -> 0.72
                "resource" -> 0.76
                "security" -> 0.82
                "error_spike" -> 0.85
                else -> 0.5
            }

            LogReview(
                isAnomaly = category != "normal",
                score = score,
                category = category,
                explanation = note,
            )
        }.getOrElse {
            val text = logJson.lowercase()
            val isAnomaly =
                text.contains("error") ||
                text.contains("exception") ||
                text.contains("failed login") ||
                text.contains("bruteforce")

            LogReview(
                isAnomaly = isAnomaly,
                score = if (isAnomaly) 0.7 else 0.1,
                category = if (isAnomaly) "other" else "normal",
                explanation = "$note (json-parse-fallback)",
            )
        }
    }
}
