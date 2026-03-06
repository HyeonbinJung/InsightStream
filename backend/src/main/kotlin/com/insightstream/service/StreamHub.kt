package com.insightstream.service

import com.insightstream.model.AlertEvent
import com.insightstream.model.LogEvent
import com.insightstream.model.SummaryEvent
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

@Component
class StreamHub {
    private val logHistory = ArrayDeque<LogEvent>(500)
    private val alertHistory = ArrayDeque<AlertEvent>(200)
    private val summaryHistory = ArrayDeque<SummaryEvent>(60)

    private val subscribers = CopyOnWriteArrayList<(String) -> Unit>()

    @Synchronized
    fun addLog(e: LogEvent) {
        if (logHistory.size >= 500) logHistory.removeFirst()
        logHistory.addLast(e)
        broadcast("""{"type":"log","data":${toJsonSafe(e)}}""")
    }

    @Synchronized
    fun addAlert(a: AlertEvent) {
        if (alertHistory.size >= 200) alertHistory.removeFirst()
        alertHistory.addLast(a)
        broadcast("""{"type":"alert","data":${toJsonSafe(a)}}""")
    }

    @Synchronized
    fun addSummary(s: SummaryEvent) {
        if (summaryHistory.size >= 60) summaryHistory.removeFirst()
        summaryHistory.addLast(s)
        broadcast("""{"type":"summary","data":${toJsonSafe(s)}}""")
    }

    fun latestSummary(): SummaryEvent? = synchronized(this) { summaryHistory.lastOrNull() }

    fun latestAlerts(limit: Int = 20): List<AlertEvent> = synchronized(this) { alertHistory.takeLast(limit).reversed() }

    fun snapshotJson(): String {
        val logs = synchronized(this) { logHistory.toList() }
        val alerts = synchronized(this) { alertHistory.toList() }
        val summaries = synchronized(this) { summaryHistory.toList() }
        val payload = """{"type":"snapshot","data":{"logs":${toJsonSafe(logs)},"alerts":${toJsonSafe(alerts)},"summaries":${toJsonSafe(summaries)}}}"""
        return wrap(payload)
    }

    fun subscribe(push: (String) -> Unit): () -> Unit {
        subscribers.add(push)
        CompletableFuture.runAsync { runCatching { push(snapshotJson()) } }
        return { subscribers.remove(push) }
    }

    private fun broadcast(payload: String) {
        val wrapped = wrap(payload)
        subscribers.forEach { s -> runCatching { s(wrapped) } }
    }

    private fun wrap(payload: String): String = """{"serverTs":"${Instant.now()}","payload":$payload}"""

    private fun toJsonSafe(any: Any): String = JsonUtil.mapper.writeValueAsString(any)
}
