package com.insightstream.controller

import com.insightstream.model.TraceRecord
import com.insightstream.service.TraceRecorder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/traces")
class TraceController(
    private val recorder: TraceRecorder,
) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "100") limit: Int): List<TraceRecord> = recorder.latest(limit)
}
