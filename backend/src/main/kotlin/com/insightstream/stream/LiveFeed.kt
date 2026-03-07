package com.insightstream.stream

import com.insightstream.alert.AlertEvent
import com.insightstream.infra.json.Jsons
import com.insightstream.log.LogEvent
import com.insightstream.window.SummaryEvent
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

@Component
class LiveFeed {
    private val logHistory = ArrayDeque<LogEvent>(500)
    private val alertHistory = ArrayDeque<AlertEvent>(200)
    private val summaryHistory = ArrayDeque<SummaryEvent>(60)
    private val subscribers = CopyOnWriteArrayList<(String) -> Unit>()

    @Synchronized
    fun addLog(event: LogEvent) {
        if (logHistory.size >= 500) logHistory.removeFirst()
        logHistory.addLast(event)
        broadcast("""{"type":"log","data":${toJson(event)}}""")
    }

    @Synchronized
    fun addAlert(event: AlertEvent) {
        if (alertHistory.size >= 200) alertHistory.removeFirst()
        alertHistory.addLast(event)
        broadcast("""{"type":"alert","data":${toJson(event)}}""")
    }

    @Synchronized
    fun addSummary(event: SummaryEvent) {
        if (summaryHistory.size >= 60) summaryHistory.removeFirst()
        summaryHistory.addLast(event)
        broadcast("""{"type":"summary","data":${toJson(event)}}""")
    }

    fun summary(): SummaryEvent? = synchronized(this) { summaryHistory.lastOrNull() }

    fun alerts(limit: Int = 20): List<AlertEvent> = synchronized(this) { alertHistory.takeLast(limit).reversed() }

    fun snapshot(): String {
        val logs = synchronized(this) { logHistory.toList() }
        val alerts = synchronized(this) { alertHistory.toList() }
        val summaries = synchronized(this) { summaryHistory.toList() }
        val payload = """{"type":"snapshot","data":{"logs":${toJson(logs)},"alerts":${toJson(alerts)},"summaries":${toJson(summaries)}}}"""
        return wrap(payload)
    }

    fun subscribe(push: (String) -> Unit): () -> Unit {
        subscribers.add(push)
        CompletableFuture.runAsync { runCatching { push(snapshot()) } }
        return { subscribers.remove(push) }
    }

    private fun broadcast(payload: String) {
        val wrapped = wrap(payload)
        subscribers.forEach { subscriber -> runCatching { subscriber(wrapped) } }
    }

    private fun wrap(payload: String): String = """{"serverTs":"${Instant.now()}","payload":$payload}"""

    private fun toJson(value: Any): String = Jsons.mapper.writeValueAsString(value)
}
