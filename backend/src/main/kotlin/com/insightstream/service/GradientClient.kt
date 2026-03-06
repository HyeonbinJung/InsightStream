package com.insightstream.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

data class GradientResult(
    val isAnomaly: Boolean,
    val score: Double,
    val category: String,
    val explanation: String
)

data class WindowAiResult(
    val isAnomaly: Boolean,
    val severity: String,
    val category: String,
    val summary: String,
    val topSignals: List<String>,
    val recommendedActions: List<String>
)

data class AgentAnswer(
    val answer: String,
    val steps: List<String> = emptyList(),
    val commands: List<String> = emptyList(),
    val cautions: List<String> = emptyList()
)

data class IncidentReportAiResult(
    val title: String,
    val impact: String,
    val timeline: List<String> = emptyList(),
    val evidence: List<String> = emptyList(),
    val recommendedActions: List<String> = emptyList()
)

@Component
class GradientClient(
    @Value("\${insight.gradient.baseUrl}") private val baseUrl: String,
    @Value("\${insight.gradient.model}") private val model: String,
    @Value("\${insight.gradient.accessKey}") private val accessKey: String,
) {
    private val client = WebClient.builder().baseUrl(baseUrl).build()

    /**
     * Per-log analysis.
     */
    fun analyze(logJson: String): Mono<GradientResult> {
        if (accessKey.isBlank()) {
            return Mono.just(
                heuristicFromJson(
                    logJson,
                    "DO_MODEL_ACCESS_KEY not set; heuristic detection used for demo."
                )
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

        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 180,
            "temperature" to 0.0
        )

        return client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $accessKey")
            .bodyValue(body)
            .exchangeToMono { res ->
                res.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { raw ->
                    val code = res.statusCode().value()

                    if (!res.statusCode().is2xxSuccessful) {
                        println("Gradient(log) HTTP $code bodyPreview=" + raw.take(600))
                        return@flatMap Mono.just(
                            heuristicFromJson(logJson, "Fallback heuristic (Gradient HTTP $code).")
                        )
                    }

                    val respMap = runCatching {
                        @Suppress("UNCHECKED_CAST")
                        JsonUtil.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
                    }.getOrElse { e ->
                        e.printStackTrace()
                        println("Gradient(log) response parse failed rawPreview=" + raw.take(600))
                        return@flatMap Mono.just(
                            heuristicFromJson(
                                logJson,
                                "Fallback heuristic (response JSON parse failed)."
                            )
                        )
                    }

                    val content = extractContent(respMap)

                    val result = parseGradientResult(content).getOrElse {
                        println("Gradient(log) model content parse failed preview=" + content.take(300))
                        heuristicFromJson(logJson, "Fallback heuristic (parse failed).")
                    }

                    Mono.just(result)
                }
            }
            .timeout(Duration.ofSeconds(15))
            .onErrorResume { e ->
                e.printStackTrace()
                Mono.just(
                    heuristicFromJson(logJson, "Fallback heuristic (Gradient call exception).")
                )
            }
    }

    /**
     * Window analysis: main AI feature.
     */
    fun analyzeWindow(windowJson: String): Mono<WindowAiResult> {
        if (accessKey.isBlank()) {
            return Mono.just(
                WindowAiResult(
                    isAnomaly = false,
                    severity = "low",
                    category = "normal",
                    summary = "AI key not set. Window analysis fallback in effect.",
                    topSignals = emptyList(),
                    recommendedActions = listOf(
                        "Set DO_MODEL_ACCESS_KEY to enable Gradient Serverless analysis."
                    )
                )
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

        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 220,
            "temperature" to 0.0
        )

        return client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $accessKey")
            .bodyValue(body)
            .exchangeToMono { res ->
                res.bodyToMono(String::class.java).defaultIfEmpty("").flatMap { raw ->
                    val code = res.statusCode().value()

                    if (!res.statusCode().is2xxSuccessful) {
                        println("Gradient(window) HTTP $code bodyPreview=" + raw.take(900))
                        return@flatMap Mono.just(
                            WindowAiResult(
                                isAnomaly = false,
                                severity = "low",
                                category = "other",
                                summary = "Gradient window analysis HTTP $code; using fallback.",
                                topSignals = emptyList(),
                                recommendedActions = emptyList()
                            )
                        )
                    }

                    val respMap = runCatching {
                        @Suppress("UNCHECKED_CAST")
                        JsonUtil.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
                    }.getOrElse { e ->
                        e.printStackTrace()
                        println("Gradient(window) response parse failed rawPreview=" + raw.take(900))
                        return@flatMap Mono.just(
                            WindowAiResult(
                                isAnomaly = false,
                                severity = "low",
                                category = "other",
                                summary = "Gradient window response JSON parse failed; using fallback.",
                                topSignals = emptyList(),
                                recommendedActions = emptyList()
                            )
                        )
                    }

                    val content = extractContent(respMap)

                    val result = parseWindowResult(content).getOrElse {
                        println("Gradient(window) content parse failed preview=" + content.take(400))
                        WindowAiResult(
                            isAnomaly = false,
                            severity = "low",
                            category = "other",
                            summary = "Model response didn't include valid JSON; using fallback.",
                            topSignals = listOf(content.take(200)),
                            recommendedActions = emptyList()
                        )
                    }

                    Mono.just(result)
                }
            }
            .timeout(Duration.ofSeconds(15))
            .onErrorResume { e ->
                e.printStackTrace()
                Mono.just(
                    WindowAiResult(
                        isAnomaly = false,
                        severity = "low",
                        category = "other",
                        summary = "Gradient window analysis exception; using fallback.",
                        topSignals = emptyList(),
                        recommendedActions = emptyList()
                    )
                )
            }
    }

    /**
     * Freeform SRE Q&A for agent panel.
     */
    fun askFreeform(prompt: String): String {
        if (accessKey.isBlank()) {
            return "AI key is not configured. Please set DO_MODEL_ACCESS_KEY."
        }

        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 320,
            "temperature" to 0.2
        )

        return client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $accessKey")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
            .map { raw ->
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    JsonUtil.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
                }.map { respMap ->
                    extractContent(respMap).ifBlank { "No answer." }
                }.getOrElse {
                    "AI request failed."
                }
            }
            .onErrorReturn("AI request failed.")
            .block(Duration.ofSeconds(20)) ?: "AI request failed."
    }

    /**
     * Structured agent answer.
     */
    fun askAgent(prompt: String): Mono<AgentAnswer> {
        if (accessKey.isBlank()) {
            return Mono.just(
                AgentAnswer(
                    answer = "AI key is not configured. Please set DO_MODEL_ACCESS_KEY.",
                    cautions = listOf("Using fallback because no model access key is set.")
                )
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

        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to structuredPrompt)),
            "max_tokens" to 320,
            "temperature" to 0.1
        )

        return client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $accessKey")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
            .map { raw ->
                val respMap = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    JsonUtil.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
                }.getOrElse {
                    return@map AgentAnswer(
                        answer = "AI request failed.",
                        cautions = listOf("Could not parse model response envelope.")
                    )
                }

                val content = extractContent(respMap)

                parseAgentAnswer(content).getOrElse {
                    AgentAnswer(
                        answer = content.take(900).ifBlank {
                            "Model response didn't include valid JSON; using fallback."
                        },
                        cautions = listOf("Model output was not strict JSON.")
                    )
                }
            }
            .onErrorResume {
                Mono.just(
                    AgentAnswer(
                        answer = "AI request failed.",
                        cautions = listOf("Exception during model call.")
                    )
                )
            }
    }

    /**
     * Structured incident report generation.
     */
    fun generateIncidentReport(contextJson: String): Mono<IncidentReportAiResult> {
        if (accessKey.isBlank()) {
            return Mono.just(
                IncidentReportAiResult(
                    title = "Incident report fallback",
                    impact = "AI key is not configured.",
                    timeline = listOf("Window anomaly detected"),
                    evidence = listOf("No DO_MODEL_ACCESS_KEY configured"),
                    recommendedActions = listOf("Set DO_MODEL_ACCESS_KEY")
                )
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

        val body = mapOf(
            "model" to model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 320,
            "temperature" to 0.1
        )

        return client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $accessKey")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
            .map { raw ->
                val respMap = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    JsonUtil.mapper.readValue(raw, Map::class.java) as Map<String, Any?>
                }.getOrElse {
                    return@map IncidentReportAiResult(
                        title = "Incident report fallback",
                        impact = "Could not parse model response envelope.",
                        evidence = listOf("Response envelope parse failure"),
                        recommendedActions = listOf("Retry request")
                    )
                }

                val content = extractContent(respMap)

                parseIncidentReport(content).getOrElse {
                    IncidentReportAiResult(
                        title = "Incident report fallback",
                        impact = "Model output was not valid JSON.",
                        evidence = listOf(content.take(200)),
                        recommendedActions = listOf("Retry with smaller context")
                    )
                }
            }
            .onErrorResume {
                Mono.just(
                    IncidentReportAiResult(
                        title = "Incident report fallback",
                        impact = "AI request failed.",
                        evidence = listOf("Exception during model call"),
                        recommendedActions = listOf("Retry request")
                    )
                )
            }
    }

    private fun extractContent(respMap: Map<String, Any?>): String {
        val choices = respMap["choices"] as? List<*>
        return (((choices?.firstOrNull() as? Map<*, *>)?.get("message") as? Map<*, *>)?.get("content") as? String)
            ?: ""
    }

    private fun parseGradientResult(text: String): Result<GradientResult> = runCatching {
        val json = extractJsonObject(text)
        val node = JsonUtil.mapper.readTree(json)
        GradientResult(
            isAnomaly = node["is_anomaly"]?.asBoolean(false) ?: false,
            score = node["score"]?.asDouble(0.0) ?: 0.0,
            category = node["category"]?.asText("other") ?: "other",
            explanation = node["explanation"]?.asText("No explanation") ?: "No explanation"
        )
    }

    private fun parseWindowResult(text: String): Result<WindowAiResult> = runCatching {
        val json = extractJsonObject(text)
        val node = JsonUtil.mapper.readTree(json)
        WindowAiResult(
            isAnomaly = node["is_anomaly"]?.asBoolean(false) ?: false,
            severity = node["severity"]?.asText("low") ?: "low",
            category = node["category"]?.asText("other") ?: "other",
            summary = node["summary"]?.asText("") ?: "",
            topSignals = node["top_signals"]?.map { it.asText() }?.toList() ?: emptyList(),
            recommendedActions = node["recommended_actions"]?.map { it.asText() }?.toList() ?: emptyList()
        )
    }

    private fun parseAgentAnswer(text: String): Result<AgentAnswer> = runCatching {
        val json = extractJsonObject(text)
        val node = JsonUtil.mapper.readTree(json)
        AgentAnswer(
            answer = node["answer"]?.asText("") ?: "",
            steps = node["steps"]?.map { it.asText() }?.toList() ?: emptyList(),
            commands = node["commands"]?.map { it.asText() }?.toList() ?: emptyList(),
            cautions = node["cautions"]?.map { it.asText() }?.toList() ?: emptyList()
        )
    }

    private fun parseIncidentReport(text: String): Result<IncidentReportAiResult> = runCatching {
        val json = extractJsonObject(text)
        val node = JsonUtil.mapper.readTree(json)
        IncidentReportAiResult(
            title = node["title"]?.asText("Incident report") ?: "Incident report",
            impact = node["impact"]?.asText("") ?: "",
            timeline = node["timeline"]?.map { it.asText() }?.toList() ?: emptyList(),
            evidence = node["evidence"]?.map { it.asText() }?.toList() ?: emptyList(),
            recommendedActions = node["recommended_actions"]?.map { it.asText() }?.toList() ?: emptyList()
        )
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON object found" }
        return text.substring(start, end + 1)
    }

    private fun heuristicFromJson(logJson: String, note: String): GradientResult {
        return runCatching {
            val node = JsonUtil.mapper.readTree(logJson)

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

            GradientResult(
                isAnomaly = category != "normal",
                score = score,
                category = category,
                explanation = note
            )
        }.getOrElse {
            val s = logJson.lowercase()
            val isAnomaly =
                s.contains("error") ||
                s.contains("exception") ||
                s.contains("failed login") ||
                s.contains("bruteforce")

            GradientResult(
                isAnomaly = isAnomaly,
                score = if (isAnomaly) 0.7 else 0.1,
                category = if (isAnomaly) "other" else "normal",
                explanation = "$note (json-parse-fallback)"
            )
        }
    }
}