package com.insightstream.controller

import com.insightstream.model.SimilarIncident
import com.insightstream.service.IncidentMemoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/similarity")
class SimilarityController(
    private val memory: IncidentMemoryService,
) {
    @GetMapping
    fun similar(
        @RequestParam query: String,
        @RequestParam(defaultValue = "5") limit: Int,
    ): List<SimilarIncident> = memory.findSimilar(query, limit)
}
