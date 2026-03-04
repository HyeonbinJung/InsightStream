# 🚀 InsightStream
### AI-Powered Real-time Log Intelligence

> 🔍 Detect anomalies in massive log streams using **Kafka + AI**

InsightStream is an **AI-driven observability platform** that analyzes log streams in real time using **Apache Kafka and DigitalOcean Gradient AI models**.

Instead of manually searching through thousands of logs, InsightStream automatically detects anomalies, summarizes incidents, and provides operational insights through a **real-time dashboard**.

---

# 📸 Demo

Real-time monitoring dashboard

<img width="587" height="506" alt="image" src="https://github.com/user-attachments/assets/02d36e28-f997-476a-9c49-6a37fc6e823e"/>

- Live log stream
- AI anomaly detection
- Alert notifications
- Operational metrics

---

# 🧠 Why InsightStream?

Modern systems generate **millions of logs per hour**.

Traditional monitoring tools rely on:

❌ manual investigation  
❌ static threshold alerts  
❌ delayed incident detection  

InsightStream solves this by combining **stream processing and AI analysis**.

✔ Detect anomalies automatically  
✔ Identify incident patterns  
✔ Provide actionable insights  

---

# 🏗 System Architecture
Application Logs
↓
Kafka Stream
↓
Spring Boot Consumer
↓
DigitalOcean Gradient AI
↓
InsightStream Dashboard


### Flow

1️⃣ **Producer**

Applications generate logs and publish them to Kafka.

2️⃣ **Kafka Stream**

Kafka ensures reliable and scalable log streaming.

3️⃣ **Consumer (Spring Boot)**

The backend consumes logs and performs analysis.

4️⃣ **AI Inference**

DigitalOcean Gradient models evaluate anomalies.

5️⃣ **Real-time Dashboard**

Insights are streamed to the UI instantly.

---

# ✨ Key Features

## ⚡ Real-time Log Streaming

Apache Kafka processes log events in real time.

- High throughput
- fault tolerant
- scalable ingestion

---

## 🤖 AI-Powered Anomaly Detection

Logs are analyzed using **DigitalOcean Gradient AI models**.

The AI identifies:

- error spikes
- latency anomalies
- security threats
- resource exhaustion

---

## 🚨 Instant Alerting

When anomalies are detected:

- alerts are generated
- severity levels are assigned
- engineers are notified instantly

---

## 📊 Interactive Dashboard

Real-time UI provides full system visibility.

Features:

- live log stream
- anomaly alerts
- AI summaries
- performance metrics

---

# 🛠 Tech Stack

## 💻 Backend & Core

| Technology | Description |
|------------|-------------|
| 🟣 Kotlin | Backend development |
| 🍃 Spring Boot | Microservice framework |
| 🟠 Apache Kafka | Log streaming platform |
| 🔗 WebFlux | Reactive processing |
| 🐳 Docker | Containerized deployment |

---

## 🤖 Artificial Intelligence

| Technology | Description |
|------------|-------------|
| 🌊 DigitalOcean Gradient | Serverless AI inference |
| 🧠 Llama3-8B | Anomaly detection model |

---

## 🌐 Frontend & Visualization

| Technology | Description |
|------------|-------------|
| ⚛️ React | UI framework |
| ▲ Next.js | Frontend platform |
| 🔄 SSE | Real-time data streaming |
| 🎨 CSS | Dashboard styling |

---

# 📊 Dashboard Features

InsightStream dashboard provides:

✔ Live log monitoring  
✔ AI anomaly alerts  
✔ System metrics  
✔ Scenario simulation  

---

# 🧪 Scenario Simulation

To demonstrate anomaly detection, the dashboard includes built-in log generators:

| Scenario | Description |
|--------|-------------|
| 🔥 Error Spike | Sudden increase in server errors |
| 🛑 Brute Force | Repeated login failures |
| 🐢 DB Latency | Slow database queries |
| 💾 Memory Pressure | High memory usage |

These scenarios allow real-time observation of the AI detection pipeline.

---

# 🚀 Getting Started

## 1️⃣ Clone the repository

```bash
git clone https://github.com/HyeonbinJung/InsightStream.git
cd InsightStream
```

## 2️⃣ Set DigitalOcean Gradient API key
```bash 
export DO_MODEL_ACCESS_KEY=YOUR_KEY
```
## 3️⃣ Run with Docker
```bash 
docker compose up -d --build
```
## 4️⃣ Open the dashboard
```bash 
http://localhost:3000
```
## 📁 Project Structure
```bash 
InsightStream
│
├─ backend
│  ├─ kafka consumer
│  ├─ AI inference
│  ├─ alert engine
│
├─ dashboard
│  ├─ realtime UI
│  ├─ log visualization
│
└─ docker
   ├─ kafka
   ├─ backend
   └─ dashboard
```
## 🔮 Future Improvements

Incident timeline analysis
Root cause detection
Multi-service correlation
distributed tracing integration
