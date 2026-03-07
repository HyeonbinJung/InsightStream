package com.insightstream.stream

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean

@RestController
class StreamController(
    private val feed: LiveFeed,
) {
    private val scheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("sse-")
        initialize()
    }

    @GetMapping("/api/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(resp: HttpServletResponse): SseEmitter {
        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("X-Accel-Buffering", "no")

        val emitter = SseEmitter(0L)
        val closed = AtomicBoolean(false)
        val queue = ConcurrentLinkedQueue<String>()

        val flushTask: ScheduledFuture<*> = scheduler.scheduleAtFixedRate({
            if (closed.get()) return@scheduleAtFixedRate
            while (true) {
                val payload = queue.poll() ?: break
                try {
                    emitter.send(SseEmitter.event().name("message").data(payload))
                } catch (_: Exception) {
                    closed.set(true)
                    runCatching { emitter.complete() }
                    break
                }
            }
        }, 100)

        val heartbeatTask: ScheduledFuture<*> = scheduler.scheduleAtFixedRate({
            if (closed.get()) return@scheduleAtFixedRate
            queue.offer("""{"serverTs":"${Instant.now()}","payload":{"type":"ping"}}""")
        }, 10_000)

        lateinit var unsubscribe: () -> Unit
        unsubscribe = feed.subscribe { payload ->
            if (closed.get()) return@subscribe
            queue.offer(payload)
        }

        queue.offer("""{"serverTs":"${Instant.now()}","payload":{"type":"ping"}}""")

        fun cleanup() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { unsubscribe() }
            runCatching { flushTask.cancel(true) }
            runCatching { heartbeatTask.cancel(true) }
            runCatching { emitter.complete() }
        }

        emitter.onCompletion { cleanup() }
        emitter.onTimeout { cleanup() }
        emitter.onError { cleanup() }

        return emitter
    }
}
