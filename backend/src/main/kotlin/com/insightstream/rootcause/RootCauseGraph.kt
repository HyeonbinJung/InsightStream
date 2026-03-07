package com.insightstream.rootcause

data class RootCauseNode(
    val id: String,
    val label: String,
    val type: String,
    val weight: Double = 1.0
)

data class RootCauseEdge(
    val from: String,
    val to: String,
    val label: String = ""
)

data class RootCauseGraph(
    val ts: String,
    val title: String,
    val confidence: Double,
    val nodes: List<RootCauseNode>,
    val edges: List<RootCauseEdge>,
    val hypothesis: String
)
