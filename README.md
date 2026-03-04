# 🚀 InsightStream
### AI-Powered Real-time Log Intelligence

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/SpringBoot-6DB33F?logo=springboot&logoColor=white)
![WebFlux](https://img.shields.io/badge/Spring-WebFlux-6DB33F?logo=spring&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?logo=apachekafka&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

![DigitalOcean](https://img.shields.io/badge/DigitalOcean%20Gradient-0080FF?logo=digitalocean&logoColor=white)
![Llama3](https://img.shields.io/badge/Llama3-000000?logo=meta&logoColor=white)

![React](https://img.shields.io/badge/React-20232A?logo=react&logoColor=61DAFB)
![Next.js](https://img.shields.io/badge/Next.js-000000?logo=nextdotjs&logoColor=white)
![SSE](https://img.shields.io/badge/SSE-Real--time%20Streaming-orange)
![CSS](https://img.shields.io/badge/CSS3-1572B6?logo=css3&logoColor=white)

> 🔍 Detect anomalies in massive log streams using **Kafka + AI**

InsightStream is an **AI-driven observability platform** that analyzes log streams in real time using **Apache Kafka and DigitalOcean Gradient AI models**.

Instead of manually searching through thousands of logs, InsightStream automatically detects anomalies, summarizes incidents, and provides operational insights through a **real-time dashboard**.

---

# 📸 Demo

Real-time monitoring dashboard

<img width="702" height="840" alt="image" src="https://github.com/user-attachments/assets/61f28222-5f4b-4a4b-aaf7-bb11c062f6f7" />

- Live log stream
- AI anomaly detection
- Alert notifications
- Operational metrics

---

## 🔗 Live Demo 


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
```text
             +------------------+
             |  Application     |
             |  Log Producers   |
             +---------+--------+
                       |
                       v
                 +-----------+
                 |  Kafka    |
                 |  Streams  |
                 +-----+-----+
                       |
                       v
             +--------------------+
             | Spring Boot        |
             | Log Consumer       |
             +---------+----------+
                       |
                       v
            +----------------------+
            | DigitalOcean         |
            | Gradient AI Inference|
            +----------+-----------+
                       |
                       v
              +----------------+
              | Dashboard UI   |
              | (Next.js)      |
              +----------------+
```

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

# 📊 Dashboard Features

InsightStream dashboard provides:

✔ Live log monitoring  
✔ AI anomaly alerts  
✔ System metrics  
✔ Scenario simulation  

---

# 🧪 Scenario Simulation

The dashboard provides built-in log generators so users can instantly simulate incidents and observe the detection pipeline.
To demonstrate anomaly detection, the dashboard includes built-in log generators:

| Scenario | Description |
|--------|-------------|
| 🔥 Error Spike | Sudden increase in server errors |
| 🛑 Brute Force | Repeated login failures |
| 🐢 DB Latency | Slow database queries |
| 💾 Memory Pressure | High memory usage |

These scenarios allow real-time observation of the AI detection pipeline.

---

## 📦 Deployment

InsightStream can be deployed using Docker Compose.

Services included:

- Apache Kafka
- Spring Boot backend
- Next.js dashboard

Production deployment can be extended with:

- Log collectors (Fluent Bit / Vector)
- Kafka clusters
- Reverse proxy (NGINX)

## 🤖 AI Prompt Design

InsightStream uses structured prompts to analyze log windows.

The AI model evaluates:

- anomaly likelihood
- severity level
- incident category
- recommended actions

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

- Incident timeline analysis
- Root cause detection
- Multi-service correlation
- Distributed tracing integration
