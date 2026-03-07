package com.insightstream.trace

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/traces")
class TraceController(
    private val traces: TraceRecorder,
) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "100") limit: Int): List<TraceRecord> = traces.list(limit)
}
