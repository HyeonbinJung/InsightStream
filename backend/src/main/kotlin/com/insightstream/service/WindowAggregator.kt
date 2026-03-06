package com.insightstream.service

import com.insightstream.model.LogEvent
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque

data class WindowSnapshot(
    val ts: String,
    val windowSec: Int,
    val logs: List<LogEvent>
)

@Component
class WindowAggregator {
    private val buf = ConcurrentLinkedDeque<LogEvent>()

    fun add(e: LogEvent) {
        buf.addLast(e)
        while (buf.size > 5000) buf.pollFirst()
    }

    fun snapshot(windowSec: Int): WindowSnapshot {
        val now = Instant.now()
        val cutoff = now.minusSeconds(windowSec.toLong())

        // drop old entries
        while (true) {
            val first = buf.peekFirst() ?: break
            val t = runCatching { Instant.parse(first.ts) }.getOrNull()
            if (t != null && t.isBefore(cutoff)) buf.pollFirst() else break
        }

        return WindowSnapshot(
            ts = now.toString(),
            windowSec = windowSec,
            logs = buf.toList()
        )
    }
}