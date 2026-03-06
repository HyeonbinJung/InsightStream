import Link from "next/link";

export default function LandingPage() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <section className="card">
        <div className="cardHeader">
          <div>
            <div className="cardTitle">🚀 InsightStream</div>
            <div className="cardSub">AI-Powered Real-time Log Intelligence (Kafka + Kotlin/Spring Boot + DigitalOcean Gradient)</div>
          </div>
          <div className="row">
            <Link className="btn btnPrimary" href="/dashboard">Open Demo Dashboard</Link>
            <a className="btn" href="#install">Install Guide</a>
            <a className="btn" href="#prod">Production Integration</a>
          </div>
        </div>
        <div className="cardBody">
          <p style={{ margin: 0 }}>
            InsightStream streams logs through <b>Apache Kafka</b>, analyzes anomalies with <b>DigitalOcean Gradient</b>, and visualizes
            live insights in a real-time dashboard.
          </p>

          <div className="grid" style={{ marginTop: 14 }}>
            <div className="card" style={{ background: "rgba(255,255,255,.03)" }}>
              <div className="cardHeader">
                <div className="cardTitle">✨ What you can demo</div>
              </div>
              <div className="cardBody">
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  <li className="small">Generate scenarios (error spike, brute force, DB latency, memory pressure)</li>
                  <li className="small">Live log stream + alerts</li>
                  <li className="small">AI window summary with recommended actions</li>
                  <li className="small">Ask Agent: click a log and ask AI follow-up questions</li>
                </ul>
              </div>
            </div>

            <div className="card" style={{ background: "rgba(255,255,255,.03)" }}>
              <div className="cardHeader">
                <div className="cardTitle">🏗 Architecture</div>
              </div>
              <div className="cardBody">
                <pre className="codeBlock">{`App/Server Logs
   ↓
Kafka (stream)
   ↓
Spring Boot Consumer (Kotlin)
   ↓
DigitalOcean Gradient AI
   ↓
SSE → Next.js Dashboard`}</pre>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="install" className="card">
        <div className="cardHeader">
          <div>
            <div className="cardTitle">🧩 Install Guide</div>
            <div className="cardSub">Run everything locally with Docker Compose</div>
          </div>
        </div>
        <div className="cardBody">
          <ol style={{ margin: 0, paddingLeft: 18 }}>
            <li className="small">Set your DigitalOcean Gradient Model Access Key (optional for demo fallback).</li>
          </ol>
          <pre className="codeBlock">{`export DO_MODEL_ACCESS_KEY="YOUR_KEY"
export DO_INFERENCE_MODEL="llama3-8b-instruct"   # optional`}</pre>
          <ol start={2} style={{ margin: 0, paddingLeft: 18 }}>
            <li className="small">Build and run services:</li>
          </ol>
          <pre className="codeBlock">{`docker compose up -d --build`}</pre>
          <ol start={3} style={{ margin: 0, paddingLeft: 18 }}>
            <li className="small">Open the dashboard:</li>
          </ol>
          <pre className="codeBlock">{`http://localhost:3000/dashboard`}</pre>

          <div className="callout" style={{ marginTop: 12 }}>
            <b>Demo mode:</b> If <span className="mono">DO_MODEL_ACCESS_KEY</span> is not set, the backend falls back to a heuristic detector
            so the demo still runs.
          </div>
        </div>
      </section>

      <section id="prod" className="card">
        <div className="cardHeader">
          <div>
            <div className="cardTitle">🛡 Production Integration</div>
            <div className="cardSub">How to feed real logs into InsightStream</div>
          </div>
        </div>
        <div className="cardBody" style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div className="small">
            InsightStream consumes JSON logs from a Kafka topic. In production, you typically integrate using one of these approaches:
          </div>

          <div className="card" style={{ background: "rgba(255,255,255,.03)" }}>
            <div className="cardHeader">
              <div className="cardTitle">A) App produces JSON logs to Kafka</div>
            </div>
            <div className="cardBody">
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                <li className="small">Your service publishes structured JSON logs to Kafka topic (e.g., <span className="mono">logs.raw</span>).</li>
                <li className="small">InsightStream consumes and analyzes them.</li>
              </ul>
            </div>
          </div>

          <div className="card" style={{ background: "rgba(255,255,255,.03)" }}>
            <div className="cardHeader">
              <div className="cardTitle">B) Log collector → Kafka</div>
            </div>
            <div className="cardBody">
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                <li className="small">Use Fluent Bit / Vector / Logstash to ship logs into Kafka.</li>
                <li className="small">This is more common for multi-service environments.</li>
              </ul>
            </div>
          </div>

          <div className="callout">
            <b>Note:</b> For cost and stability, InsightStream focuses on <b>window-based AI analysis</b> (aggregate summaries) and keeps per-log
            AI detection optional.
          </div>
        </div>
      </section>
    </div>
  );
}
