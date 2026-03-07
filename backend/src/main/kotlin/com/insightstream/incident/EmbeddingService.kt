package com.insightstream.incident

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.sqrt

@Component
class EmbeddingService(
    @Value("\${insight.vector.dimensions:64}") private val dims: Int,
) {
    fun embed(text: String): DoubleArray {
        val vec = DoubleArray(dims)
        val tokens = text.lowercase().split(Regex("[^a-z0-9._/-]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return vec
        tokens.forEachIndexed { idx, token ->
            val h = token.hashCode()
            val slot = ((h xor (idx * 31)) and Int.MAX_VALUE) % dims
            vec[slot] += 1.0 + (token.length.coerceAtMost(16) / 24.0)
        }
        normalize(vec)
        return vec
    }

    fun similarity(a: DoubleArray, b: DoubleArray): Double {
        val n = minOf(a.size, b.size)
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }

    fun toPgVector(vec: DoubleArray): String = vec.joinToString(prefix = "[", postfix = "]") { "%.6f".format(it) }

    private fun normalize(vec: DoubleArray) {
        val mag = sqrt(vec.sumOf { it * it })
        if (mag == 0.0) return
        for (i in vec.indices) vec[i] /= mag
    }
}
