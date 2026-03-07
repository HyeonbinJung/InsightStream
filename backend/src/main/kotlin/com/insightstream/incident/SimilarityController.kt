package com.insightstream.incident

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/similarity")
class SimilarityController(
    private val store: IncidentStore,
) {
    @GetMapping
    fun similar(
        @RequestParam query: String,
        @RequestParam(defaultValue = "5") limit: Int,
    ): List<SimilarIncident> = store.searchSimilar(query, limit)
}
