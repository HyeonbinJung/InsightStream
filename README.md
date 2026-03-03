# 🚀 InsightStream: AI-Powered Real-time Log Intelligence

> An intelligent monitoring platform that detects anomalies in real-time
> log streams using Kafka and AI.

------------------------------------------------------------------------

## 📌 Project Overview

**InsightStream** analyzes massive volumes of server logs generated in
modern high‑traffic environments **in real time**.

Rather than simply collecting logs, the system leverages **DigitalOcean
Gradient-powered AI models** to identify abnormal patterns instantly.
This significantly reduces incident response time and helps operators
quickly detect and diagnose system issues.

------------------------------------------------------------------------

## 🛠 Tech Stack

### 💻 Backend & Core

  -----------------------------------------------------------------------

  -----------------------------------------------------------------------

------------------------------------------------------------------------

### 🤖 Artificial Intelligence

  -----------------------------------------------------------------------

  -----------------------------------------------------------------------

------------------------------------------------------------------------

### 🌐 Frontend & Visualization

  -----------------------------------------------------------------------

  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 🏗 System Architecture

1.  **Producer**\
    Application or server logs are generated and sent to Kafka.

2.  **Kafka Stream**\
    Kafka buffers and reliably delivers high-volume log data streams.

3.  **Consumer (Spring Boot)**\
    The backend consumes logs from Kafka and sends them to the Gradient
    AI API for analysis.

4.  **AI Inference**\
    The AI model evaluates the logs and returns an anomaly score.

5.  **Web Dashboard**\
    Results are streamed via WebSocket and displayed in real time on the
    monitoring dashboard.

------------------------------------------------------------------------

## 🌟 Key Features

### Zero‑loss Ingestion

Kafka ensures reliable and fault-tolerant log processing.

### AI‑Powered Analysis

Uses machine learning instead of static thresholds to detect anomalies.

### Instant Alerting

Administrators receive immediate alerts when abnormal behavior is
detected.

### Interactive Visualization

Real-time dashboards provide clear visibility into system health.

------------------------------------------------------------------------

## 🚀 Getting Started

``` bash
# Clone the repository
git clone https://github.com/HyeonbinJung/InsightStream.git

# Run Kafka with Docker
docker-compose up -d

# Run the Spring Boot application
./gradlew bootRun
```
