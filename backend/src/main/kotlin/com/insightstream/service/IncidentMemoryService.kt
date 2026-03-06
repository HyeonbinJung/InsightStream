package com.insightstream.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.insightstream.model.IncidentReport
import com.insightstream.model.SimilarIncident
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.util.Collections
import java.util.LinkedHashMap
import java.util.UUID
import kotlin.math.sqrt

data class StoredIncident(
    val report: IncidentReport,
    val embedding: DoubleArray
)

@Component
class IncidentMemoryService(
    private val embeddingService: EmbeddingService,
    private val jdbcTemplate: JdbcTemplate?,
    @Value("\${insight.vector.enabled:false}") private val vectorEnabled: Boolean,
    @Value("\${insight.vector.dimensions:64}") private val dimensions: Int,
) {
    private val mapper = jacksonObjectMapper()

    private val fallbackById = Collections.synchronizedMap(LinkedHashMap<String, StoredIncident>())
    private val fallbackOrdered = mutableListOf<String>()

    init {
        val jdbc = jdbcTemplate
        if (vectorEnabled && jdbc != null) {
            runCatching {
                jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector")
                jdbc.execute(
                    """
                    CREATE TABLE IF NOT EXISTS incident_reports (
                        id TEXT PRIMARY KEY,
                        ts TEXT NOT NULL,
                        title TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        category TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        impact TEXT NOT NULL,
                        timeline_json TEXT NOT NULL,
                        evidence_json TEXT NOT NULL,
                        actions_json TEXT NOT NULL,
                        similar_ids_json TEXT NOT NULL,
                        embedding vector(${dimensions})
                    )
                    """.trimIndent()
                )
            }.onFailure {
                println("pgvector init failed, falling back to memory: ${it.message}")
            }
        }
    }

    fun allocateId(): String = UUID.randomUUID().toString()

    fun remember(report: IncidentReport) {
        val embedding = embeddingService.embed(buildEmbeddingText(report))

        val jdbc = jdbcTemplate
        if (vectorEnabled && jdbc != null) {
            val saved = runCatching {
                jdbc.update(
                    """
                    INSERT INTO incident_reports (
                        id, ts, title, severity, category, summary, impact,
                        timeline_json, evidence_json, actions_json, similar_ids_json, embedding
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                    ON CONFLICT (id) DO UPDATE SET
                        ts = EXCLUDED.ts,
                        title = EXCLUDED.title,
                        severity = EXCLUDED.severity,
                        category = EXCLUDED.category,
                        summary = EXCLUDED.summary,
                        impact = EXCLUDED.impact,
                        timeline_json = EXCLUDED.timeline_json,
                        evidence_json = EXCLUDED.evidence_json,
                        actions_json = EXCLUDED.actions_json,
                        similar_ids_json = EXCLUDED.similar_ids_json,
                        embedding = EXCLUDED.embedding
                    """.trimIndent(),
                    report.id,
                    report.ts,
                    report.title,
                    report.severity,
                    report.category,
                    report.summary,
                    report.impact,
                    mapper.writeValueAsString(report.timeline),
                    mapper.writeValueAsString(report.evidence),
                    mapper.writeValueAsString(report.recommendedActions),
                    mapper.writeValueAsString(report.similarIncidentIds),
                    embeddingService.toPgVector(embedding)
                )
                true
            }.getOrElse {
                println("pgvector remember failed, using fallback: ${it.message}")
                false
            }

            if (saved) return
        }

        saveFallback(report, embedding)
    }

    fun latest(limit: Int = 20): List<IncidentReport> {
        val jdbc = jdbcTemplate
        if (vectorEnabled && jdbc != null) {
            val dbResult = runCatching {
                jdbc.query(
                    "SELECT * FROM incident_reports ORDER BY ts DESC LIMIT ?",
                    { rs, _ -> mapReport(rs) },
                    limit
                )
            }.getOrElse {
                println("pgvector latest failed, falling back to memory: ${it.message}")
                null
            }

            if (dbResult != null) return dbResult
        }

        return fallbackOrdered.asReversed()
            .mapNotNull { fallbackById[it]?.report }
            .take(limit)
    }

    fun get(id: String): IncidentReport? {
        val jdbc = jdbcTemplate
        if (vectorEnabled && jdbc != null) {
            val dbResult: IncidentReport? = runCatching {
                jdbc.query(
                    "SELECT * FROM incident_reports WHERE id = ? LIMIT 1",
                    org.springframework.jdbc.core.ResultSetExtractor { rs ->
                        if (rs.next()) mapReport(rs) else null
                    },
                    id
                )
            }.getOrElse {
                println("pgvector get failed, falling back to memory: ${it.message}")
                null
            }
    
            if (dbResult != null) return dbResult
        }
    
        return fallbackById[id]?.report
    }

    fun findSimilar(text: String, limit: Int = 5): List<SimilarIncident> {
        val q = embeddingService.embed(text)
        val jdbc = jdbcTemplate

        if (vectorEnabled && jdbc != null) {
            val dbResult = runCatching {
                val pgVector = embeddingService.toPgVector(q)

                jdbc.query(
                    """
                    SELECT id, ts, title, summary, severity, category,
                           1 - (embedding <=> CAST(? AS vector)) AS score
                    FROM incident_reports
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """.trimIndent(),
                    { rs, _ ->
                        SimilarIncident(
                            id = rs.getString("id"),
                            ts = rs.getString("ts"),
                            title = rs.getString("title"),
                            summary = rs.getString("summary"),
                            severity = rs.getString("severity"),
                            category = rs.getString("category"),
                            score = rs.getDouble("score")
                        )
                    },
                    pgVector,
                    pgVector,
                    limit
                )
            }.getOrElse {
                println("pgvector similarity failed, falling back to memory: ${it.message}")
                null
            }

            if (dbResult != null) return dbResult
        }

        return fallbackById.values
            .map { stored ->
                SimilarIncident(
                    id = stored.report.id,
                    ts = stored.report.ts,
                    title = stored.report.title,
                    summary = stored.report.summary,
                    severity = stored.report.severity,
                    category = stored.report.category,
                    score = cosineSimilarity(q, stored.embedding)
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun saveFallback(report: IncidentReport, embedding: DoubleArray) {
        val existed = fallbackById.containsKey(report.id)
        fallbackById[report.id] = StoredIncident(report, embedding)

        if (!existed) {
            fallbackOrdered.add(report.id)
        } else {
            fallbackOrdered.remove(report.id)
            fallbackOrdered.add(report.id)
        }

        while (fallbackOrdered.size > 500) {
            val old = fallbackOrdered.removeAt(0)
            fallbackById.remove(old)
        }
    }

    private fun mapReport(rs: ResultSet): IncidentReport {
        val timeline = runCatching {
            mapper.readValue(rs.getString("timeline_json"), Array<String>::class.java).toList()
        }.getOrElse { emptyList() }

        val evidence = runCatching {
            mapper.readValue(rs.getString("evidence_json"), Array<String>::class.java).toList()
        }.getOrElse { emptyList() }

        val actions = runCatching {
            mapper.readValue(rs.getString("actions_json"), Array<String>::class.java).toList()
        }.getOrElse { emptyList() }

        val similarIds = runCatching {
            mapper.readValue(rs.getString("similar_ids_json"), Array<String>::class.java).toList()
        }.getOrElse { emptyList() }

        return IncidentReport(
            id = rs.getString("id"),
            ts = rs.getString("ts"),
            title = rs.getString("title"),
            severity = rs.getString("severity"),
            category = rs.getString("category"),
            summary = rs.getString("summary"),
            impact = rs.getString("impact"),
            timeline = timeline,
            evidence = evidence,
            recommendedActions = actions,
            similarIncidentIds = similarIds
        )
    }

    private fun buildEmbeddingText(report: IncidentReport): String =
        listOf(
            report.title,
            report.summary,
            report.impact,
            report.category,
            report.severity,
            report.timeline.joinToString(" "),
            report.evidence.joinToString(" "),
            report.recommendedActions.joinToString(" ")
        ).joinToString(" ")

    private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0.0

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
}