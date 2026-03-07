package com.insightstream.log

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.random.Random

@RestController
@RequestMapping("/api/test")
class LogSeedController(
    private val logs: LogPublisher,
) {
    @PostMapping("/seed")
    fun seed(
        @RequestParam scenario: String,
        @RequestParam(defaultValue = "20") count: Int,
    ): Map<String, Any> {
        repeat(count) { idx ->
            logs.send(logs.toJson(generate(scenario, idx + 1)))
        }

        return mapOf("ok" to true, "sent" to count, "scenario" to scenario)
    }

    private fun generate(scenario: String, idx: Int): LogEvent {
        val ts = logs.nowIso()
        return when (scenario) {
            "normal" -> LogEvent(ts, "api", "INFO", "GET /health 200", Random.nextInt(8, 60))
            "errors" -> LogEvent(
                ts = ts,
                service = "api",
                level = "ERROR",
                message = "GET /checkout 500 NullPointerException",
                latencyMs = Random.nextInt(200, 1500),
                stacktrace = """
java.lang.NullPointerException: Cannot invoke "String.length()" because "s" is null
    at com.example.checkout.PaymentService.charge(PaymentService.kt:42)
    at com.example.checkout.CheckoutController.checkout(CheckoutController.kt:88)
    at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014)
""".trimIndent(),
            )
            "bruteforce" -> LogEvent(ts, "auth", "WARN", "Failed login attempt user=admin ip=203.0.113.${Random.nextInt(1,255)}", Random.nextInt(20, 140))
            "db-latency" -> LogEvent(ts, "db", "WARN", "Query slow: SELECT * FROM orders WHERE ...", Random.nextInt(800, 6000))
            "memory" -> LogEvent(ts, "worker", "WARN", "GC overhead high / heap usage=${Random.nextInt(70, 97)}%", Random.nextInt(80, 900))
            else -> LogEvent(ts, "api", "INFO", "Test log #$idx", Random.nextInt(10, 250))
        }
    }
}
