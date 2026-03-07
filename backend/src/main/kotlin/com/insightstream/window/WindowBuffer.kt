package com.insightstream.window

import com.insightstream.log.LogEvent
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque

data class LogWindow(
    val ts: String,
    val windowSec: Int,
    val logs: List<LogEvent>,
)

@Component
class WindowBuffer {
    private val buffer = ConcurrentLinkedDeque<LogEvent>()

    @Synchronized
    fun add(event: LogEvent) {
        buffer.addLast(event)
        while (buffer.size > 5_000) buffer.pollFirst()
    }

    @Synchronized
    fun snapshot(windowSec: Int): LogWindow {
        val now = Instant.now()
        val cutoff = now.minusSeconds(windowSec.toLong())

        while (true) {
            val first = buffer.peekFirst() ?: break
            val ts = runCatching { Instant.parse(first.ts) }.getOrNull()
            if (ts != null && ts.isBefore(cutoff)) buffer.pollFirst() else break
        }

        return LogWindow(
            ts = now.toString(),
            windowSec = windowSec,
            logs = buffer.toList(),
        )
    }
}
