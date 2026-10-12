<div align="center">

# 🚀 InsightStream

**AI-powered, real-time log intelligence — Kafka streams in, an LLM explains what went wrong.**

<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" height="26">
<img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" height="26">
<img src="https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" height="26">
<img src="https://img.shields.io/badge/PostgreSQL%20%2B%20pgvector-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" height="26">
<img src="https://img.shields.io/badge/Llama%203-0467DF?style=for-the-badge&logo=meta&logoColor=white" height="26">
<img src="https://img.shields.io/badge/DigitalOcean%20Gradient%20AI-0080FF?style=for-the-badge&logo=digitalocean&logoColor=white" height="26">
<img src="https://img.shields.io/badge/Next.js-000000?style=for-the-badge&logo=nextdotjs&logoColor=white" height="26">
<img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" height="26">

🏆 Built for the **DigitalOcean Gradient™ AI Hackathon** &nbsp;·&nbsp; 🎥 [Demo video](https://youtu.be/VGjj662PiOE?si=Lb_zM17urur0GL0J)

[Overview](#-overview) · [Features](#-key-features) · [Architecture](#%EF%B8%8F-architecture) · [Getting Started](#-getting-started) · [Structure](#%EF%B8%8F-project-structure) · [Notes](#-notes--limitations)

</div>

---

## 📌 Overview

Modern systems produce millions of log lines an hour, and static threshold alerts catch problems late. **InsightStream** streams logs through **Apache Kafka**, aggregates them into time windows, and asks an LLM (**Llama 3 8B Instruct** on **DigitalOcean Gradient AI**) to judge each window — anomaly likelihood, severity, category, root cause and recommended actions — then pushes everything to a live dashboard.

<p align="center"><img width="420" alt="InsightStream dashboard" src="https://github.com/user-attachments/assets/af1ec1e6-4023-4f31-b954-0a051382d57f"></p>

## ✨ Key Features

| Feature | Description |
|---|---|
| ⚡ **Streaming ingestion** | Logs are published to Kafka (`logs.raw`) and consumed by the Spring Boot backend |
| 🪟 **Windowed analysis** | Logs are buffered into time windows; per-window metrics are published to `logs.metrics.window` |
| 🤖 **LLM incident reports** | Each window is analyzed by the Gradient-hosted model — severity, category, evidence and actions; a rule-based fallback runs when no API key is set |
| 🧭 **Root-cause graph** | Builds a weighted graph of signals and suspected causes (nodes + labelled edges) for each incident |
| 🔎 **Similar incidents** | Hashed token embeddings (64-dim) + cosine similarity to surface past look-alikes, optionally stored in pgvector |
| 💬 **Ask the agent** | Natural-language questions about the system via `POST /api/agent/ask` |
| 📡 **Live dashboard** | Server-Sent Events stream logs, alerts, metrics and traces into a Next.js UI |
| 🧪 **Scenario simulation** | One-click generators for 🔥 error spikes, 🛑 brute-force logins, 🐢 DB latency and 💾 memory pressure |

## 🏗️ Architecture

```text
 Log producers / scenario seeder
              │
              ▼
      ┌───────────────┐      logs.raw
      │ Apache Kafka  │◀───────────────┐
      └───────┬───────┘                │
              ▼                        │
 ┌──────────────────────────┐          │
 │ Spring Boot (Kotlin)     │──────────┘
 │  • LogConsumer           │
 │  • WindowAnalysisJob ────┼──▶ DigitalOcean Gradient AI (Llama 3)
 │  • Incident / RootCause  │
 │  • Similarity (pgvector) │──▶ PostgreSQL + pgvector
 │  • SSE  /api/stream      │
 └────────────┬─────────────┘
              ▼
     Next.js dashboard (live)
```

| API | Purpose |
|---|---|
| `GET /api/stream` | Server-Sent Events feed for the dashboard |
| `GET /api/incidents` · `/latest` · `/{id}` · `POST /generate` | Incident reports |
| `GET /api/root-cause` | Root-cause graph |
| `GET /api/similarity` | Similar past incidents |
| `GET /api/window-metrics` · `GET /api/traces` | Window metrics and traces |
| `POST /api/agent/ask` | Ask the AI agent |
| `POST /api/test/seed` | Generate scenario logs |

## 🧰 Tech Stack

| Layer | Tools |
|---|---|
| Backend | Kotlin, Spring Boot (Web, Actuator, JDBC), Spring Kafka, Jackson |
| Streaming | Apache Kafka 3.7 |
| AI | DigitalOcean Gradient AI inference — Llama 3 8B Instruct (configurable) |
| Storage | PostgreSQL 16 + pgvector |
| Frontend | Next.js, React, Server-Sent Events |
| Ops | Docker, Docker Compose |

## 🚀 Getting Started

```bash
git clone https://github.com/HyeonbinJung/InsightStream.git
cd InsightStream

export DO_MODEL_ACCESS_KEY=YOUR_KEY        # optional — without it a rule-based fallback is used
docker compose up -d --build                # kafka · postgres(pgvector) · backend · dashboard
```

Open **http://localhost:3000** and trigger a scenario from the dashboard.

| Variable | Default |
|---|---|
| `DO_INFERENCE_BASE_URL` | `https://inference.do-ai.run` |
| `DO_INFERENCE_MODEL` | `llama3-8b-instruct` |
| `INSIGHT_LOG_TOPIC` / `INSIGHT_ALERT_TOPIC` | `logs.raw` / `alerts` |

## 🗂️ Project Structure

```text
InsightStream
├── backend/                         Spring Boot (Kotlin)
│   └── src/main/kotlin/com/insightstream/
│       ├── log/         Kafka publisher / consumer, scenario seeder
│       ├── window/      window buffering and LLM analysis job
│       ├── ai/          DigitalOcean Gradient API client
│       ├── incident/    incident reports, embeddings, similarity
│       ├── rootcause/   root-cause graph
│       ├── metric/      window metrics
│       ├── trace/       trace recording
│       ├── agent/       ask-the-agent endpoint
│       ├── stream/      SSE live feed
│       └── infra/       Kafka topics, CORS, JSON, telemetry config
├── dashboard/                       Next.js app (live UI)
└── docker-compose.yml               kafka · postgres · backend · dashboard
```

## 📝 Notes & Limitations

- The similarity store uses **pgvector** and is optional (`insight.vector.enabled`, off by default).
- Without `DO_MODEL_ACCESS_KEY` the backend still runs, using a deterministic fallback instead of the LLM.
- Built as a hackathon prototype — production use would add log collectors (Fluent Bit / Vector), a Kafka cluster and a reverse proxy.

---

<div align="center"><sub>Built by <a href="https://github.com/HyeonbinJung">Hyeonbin Jung</a></sub></div>
