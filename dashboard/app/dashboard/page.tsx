"use client";

import { useEffect, useMemo, useRef, useState } from "react";

type LogEvent = {
  ts: string;
  service: string;
  level: string;
  message: string;
  latencyMs?: number | null;
  stacktrace?: string | null;
};

type AlertEvent = {
  ts: string;
  service: string;
  category: string;
  score: number;
  level: string;
  message: string;
  explanation: string;
  status: string;
};

type SummaryEvent = {
  ts: string;
  windowSec: number;
  totalLogs: number;
  errorCount: number;
  warnCount: number;
  servicesTop: string[];
  isAnomaly: boolean;
  severity: string;
  category: string;
  summary: string;
  topSignals: string[];
  recommendedActions: ListString;
};

type ListString = string[];

type StreamMsg =
  | {
      serverTs: string;
      payload: {
        type: "snapshot";
        data: { logs: LogEvent[]; alerts: AlertEvent[]; summaries: SummaryEvent[] };
      };
    }
  | { serverTs: string; payload: { type: "log"; data: LogEvent } }
  | { serverTs: string; payload: { type: "alert"; data: AlertEvent } }
  | { serverTs: string; payload: { type: "summary"; data: SummaryEvent } }
  | { serverTs: string; payload: { type: "ping" } };

type AgentMode = "log" | "overview";
type TabKey = "logs" | "alerts" | "analysis";

const BACKEND =
  process.env.NEXT_PUBLIC_BACKEND_URL ||
  (typeof window !== "undefined"
    ? `${window.location.protocol}//${window.location.hostname}:8080`
    : "http://localhost:8080");

export default function DashboardPage() {
  const [logs, setLogs] = useState<LogEvent[]>([]);
  const [alerts, setAlerts] = useState<AlertEvent[]>([]);
  const [summaries, setSummaries] = useState<SummaryEvent[]>([]);
  const [status, setStatus] = useState<"connecting" | "live" | "offline">("connecting");

  const [tab, setTab] = useState<TabKey>("logs");
  const [selectedLog, setSelectedLog] = useState<LogEvent | null>(null);

  const [search, setSearch] = useState("");
  const [serviceFilter, setServiceFilter] = useState("all");
  const [levelFilter, setLevelFilter] = useState("all");
  const [anomalyOnly, setAnomalyOnly] = useState(false);

  const [notifEnabled, setNotifEnabled] = useState(false);
  const [toast, setToast] = useState<{ title: string; body: string } | null>(null);

  const [agentMode, setAgentMode] = useState<AgentMode>("log");
  const [agentQuestion, setAgentQuestion] = useState("");
  const [agentAnswer, setAgentAnswer] = useState("");
  const [agentLoading, setAgentLoading] = useState(false);

  const [showStacktrace, setShowStacktrace] = useState(true);
  const [showRawJson, setShowRawJson] = useState(false);
  const [showAiAnswer, setShowAiAnswer] = useState(true);

  const lastNotifyAtRef = useRef(0);
  const lastNotifyKeyRef = useRef("");
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem("insightstream:v3:snapshot");
      if (saved) {
        const parsed = JSON.parse(saved);
        setLogs(parsed.logs || []);
        setAlerts(parsed.alerts || []);
        setSummaries(parsed.summaries || []);
      }
    } catch {}
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(
        "insightstream:v3:snapshot",
        JSON.stringify({ logs, alerts, summaries })
      );
    } catch {}
  }, [logs, alerts, summaries]);

  useEffect(() => {
    const es = new EventSource(`${BACKEND}/api/stream`);
    esRef.current = es;

    es.onopen = () => setStatus("live");
    es.onerror = () => setStatus("offline");

    es.addEventListener("message", (evt: MessageEvent) => {
      try {
        const msg: StreamMsg = JSON.parse(evt.data);
        const p: any = (msg as any).payload;
        if (!p?.type) return;

        if (p.type === "snapshot") {
          setLogs(p.data.logs || []);
          setAlerts(p.data.alerts || []);
          setSummaries(p.data.summaries || []);
          return;
        }

        if (p.type === "log") {
          setLogs((prev) => [...prev.slice(-1999), p.data]);
          return;
        }

        if (p.type === "alert") {
          setAlerts((prev) => [p.data, ...prev].slice(0, 300));
          return;
        }

        if (p.type === "summary") {
          const s = p.data as SummaryEvent;
          setSummaries((prev) => [s, ...prev].slice(0, 120));

          if (s.isAnomaly) {
            const now = Date.now();
            const key = `${s.category}|${s.severity}`;
            const within1s = now - lastNotifyAtRef.current < 1000;
            const sameKey = lastNotifyKeyRef.current === key;

            if (!within1s || !sameKey) {
              lastNotifyAtRef.current = now;
              lastNotifyKeyRef.current = key;

              const title = `⚠ ${s.category} (${s.severity})`;
              const body = s.summary;
              setToast({ title, body });

              if (
                notifEnabled &&
                typeof window !== "undefined" &&
                "Notification" in window &&
                Notification.permission === "granted"
              ) {
                try {
                  new Notification(title, { body });
                } catch {}
              }
            }
          }
        }
      } catch {}
    });

    return () => es.close();
  }, [notifEnabled]);

  const latestSummary = summaries[0];

  const metrics = useMemo(() => {
    const last100 = logs.slice(-100);
    const errRate = last100.length
      ? Math.round((last100.filter((l) => l.level === "ERROR").length / last100.length) * 100)
      : 0;
    const p95 = percentile(last100.map((l) => l.latencyMs || 0), 95);
    const activeAlerts = alerts.filter((a) => a.status === "NEW").length;
    const throughput = Math.max(0, Math.round(last100.length / 20));
    return { errRate, p95, activeAlerts, throughput };
  }, [logs, alerts]);

  const serviceOptions = useMemo(() => {
    return Array.from(new Set(logs.map((l) => l.service))).sort();
  }, [logs]);

  const filteredLogs = useMemo(() => {
    const q = search.trim().toLowerCase();

    return logs
      .slice(-600)
      .reverse()
      .filter((l) => {
        if (serviceFilter !== "all" && l.service !== serviceFilter) return false;
        if (levelFilter !== "all" && l.level !== levelFilter) return false;

        if (
          q &&
          !`${l.ts} ${l.service} ${l.level} ${l.message} ${l.stacktrace || ""}`.toLowerCase().includes(q)
        ) {
          return false;
        }

        if (anomalyOnly) {
          const hasRelatedAlert = alerts.some(
            (a) =>
              a.service === l.service &&
              Math.abs(new Date(a.ts).getTime() - new Date(l.ts).getTime()) < 60_000
          );
          if (!hasRelatedAlert) return false;
        }

        return true;
      });
  }, [logs, alerts, search, serviceFilter, levelFilter, anomalyOnly]);

  async function seed(scenario: string, count: number) {
    await fetch(
      `${BACKEND}/api/test/seed?scenario=${encodeURIComponent(scenario)}&count=${count}`,
      { method: "POST" }
    );
  }

  async function enableAlerts() {
    if (typeof window === "undefined" || !("Notification" in window)) {
      setToast({
        title: "Notifications unavailable",
        body: "This browser does not support notifications.",
      });
      return;
    }

    const perm = await Notification.requestPermission();
    if (perm === "granted") {
      setNotifEnabled(true);
      setToast({
        title: "Alerts enabled",
        body: "Browser notifications are now enabled.",
      });
    } else {
      setNotifEnabled(false);
      setToast({
        title: "Permission denied",
        body: "Browser notifications were not enabled.",
      });
    }
  }

  function resetData() {
    if (!window.confirm("Delete all locally saved dashboard data?")) return;
    localStorage.removeItem("insightstream:v3:snapshot");
    setLogs([]);
    setAlerts([]);
    setSummaries([]);
    setSelectedLog(null);
    setAgentAnswer("");
    setAgentQuestion("");
    setToast({ title: "Data reset", body: "Local dashboard data has been cleared." });
  }

  async function askAgent() {
    if (!agentQuestion.trim()) return;

    setAgentLoading(true);
    setAgentAnswer("");

    try {
      const relatedAlerts =
        agentMode === "log" && selectedLog
          ? alerts.filter((a) => a.service === selectedLog.service).slice(0, 5)
          : alerts.slice(0, 5);

      const res = await fetch(`${BACKEND}/api/agent/ask`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          question: agentQuestion,
          mode: agentMode,
          log: agentMode === "log" ? selectedLog : null,
          summary: summaries[0] || null,
          alerts: relatedAlerts,
          recentLogs: logs.slice(-20),
        }),
      });

      const data = await res.json();
      const answer = data.answer || "No answer.";
      setAgentAnswer(answer);
      setShowAiAnswer(true);
    } catch {
      setAgentAnswer("Failed to get AI response.");
      setShowAiAnswer(true);
    } finally {
      setAgentLoading(false);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <section className="card">
        <div className="cardHeader">
          <div>
            <div className="cardTitle">Overview</div>
            <div className="cardSub">Focused incident dashboard</div>
          </div>
          <div className="row">
            <span className="badge">Backend: {BACKEND}</span>
            <span className="badge">Stream: {status}</span>
            <button className="btn" onClick={enableAlerts}>
              {notifEnabled ? "Alerts enabled" : "Enable Alerts"}
            </button>
            <button className="btn" onClick={resetData}>
              Reset Data
            </button>
          </div>
        </div>

        <div className="cardBody">
          <div className="metrics">
            <MetricCard label="Throughput" value={`${metrics.throughput}/s`} hint="last 100 logs" />
            <MetricCard label="Error rate" value={`${metrics.errRate}%`} hint="last 100 logs" />
            <MetricCard label="P95 latency" value={`${metrics.p95} ms`} hint="latencyMs" />
            <MetricCard label="Active alerts" value={`${metrics.activeAlerts}`} hint="status=NEW" />
          </div>

          <div className="row" style={{ marginTop: 12 }}>
            <button className="btn btnPrimary" onClick={() => seed("normal", 10)}>Normal ×10</button>
            <button className="btn" onClick={() => seed("errors", 20)}>Error Spike ×20</button>
            <button className="btn" onClick={() => seed("bruteforce", 20)}>Brute Force ×20</button>
            <button className="btn" onClick={() => seed("db-latency", 20)}>DB Latency ×20</button>
            <button className="btn" onClick={() => seed("memory", 20)}>Memory ×20</button>
          </div>
        </div>
      </section>

      <section className="card">
        <div className="cardHeader">
          <div>
            <div className="cardTitle">AI Window Summary</div>
            <div className="cardSub">Latest incident interpretation</div>
          </div>
        </div>
        <div className="cardBody">
          {!latestSummary ? (
            <div className="small">No summaries yet. Generate logs first.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <div className="row">
                <span className="badge">{latestSummary.category}</span>
                <span className="badge">severity={latestSummary.severity}</span>
                <span className="badge">{formatDateTime(latestSummary.ts)}</span>
              </div>

              <div style={{ fontSize: 20, fontWeight: 900 }}>{latestSummary.summary}</div>

              <div className="row">
                <span className="badge">total={latestSummary.totalLogs}</span>
                <span className="badge">error={latestSummary.errorCount}</span>
                <span className="badge">warn={latestSummary.warnCount}</span>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <InlineListCard title="Top signals" items={latestSummary.topSignals} />
                <InlineListCard title="Recommended actions" items={latestSummary.recommendedActions} />
              </div>
            </div>
          )}
        </div>
      </section>

      <section className="card">
        <div className="cardHeader">
          <div className="row" style={{ gap: 8 }}>
            <TabButton active={tab === "logs"} onClick={() => setTab("logs")}>Logs</TabButton>
            <TabButton active={tab === "alerts"} onClick={() => setTab("alerts")}>Alerts</TabButton>
            <TabButton active={tab === "analysis"} onClick={() => setTab("analysis")}>Analysis</TabButton>
          </div>

          {tab === "logs" && (
            <div className="row" style={{ gap: 8 }}>
              <input
                className="btn"
                style={{ minWidth: 220, cursor: "text" }}
                placeholder="Search logs..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />

              <select
                className="btn selectBtn"
                value={serviceFilter}
                onChange={(e) => setServiceFilter(e.target.value)}
              >
                <option value="all">All services</option>
                {serviceOptions.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>

              <select
                className="btn selectBtn"
                value={levelFilter}
                onChange={(e) => setLevelFilter(e.target.value)}
              >
                <option value="all">All levels</option>
                <option value="INFO">INFO</option>
                <option value="WARN">WARN</option>
                <option value="ERROR">ERROR</option>
              </select>

              <label className="small" style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <input
                  type="checkbox"
                  checked={anomalyOnly}
                  onChange={(e) => setAnomalyOnly(e.target.checked)}
                />
                anomaly only
              </label>
            </div>
          )}
        </div>

        <div className="cardBody">
          {tab === "logs" && (
            <div style={{ display: "grid", gridTemplateColumns: "1.35fr .9fr", gap: 16 }}>
              <div
                style={{
                  maxHeight: 640,
                  overflow: "auto",
                  border: "1px solid rgba(255,255,255,.08)",
                  borderRadius: 14,
                }}
              >
                <table className="table">
                  <thead>
                    <tr>
                      <th style={{ width: 180 }}>Time</th>
                      <th style={{ width: 90 }}>Service</th>
                      <th style={{ width: 90 }}>Level</th>
                      <th>Message</th>
                      <th style={{ width: 100 }}>Latency</th>
                      <th style={{ width: 70 }}>Trace</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredLogs.map((l, idx) => (
                      <tr
                        key={`${l.ts}-${idx}`}
                        style={{
                          cursor: "pointer",
                          background:
                            selectedLog?.ts === l.ts && selectedLog?.message === l.message
                              ? "rgba(255,255,255,.05)"
                              : undefined,
                        }}
                        onClick={() => {
                          setSelectedLog(l);
                          setAgentMode("log");
                        }}
                      >
                        <td className="mono">{formatDateTime(l.ts)}</td>
                        <td className="mono">{l.service}</td>
                        <td className={`mono level${l.level}`}>{l.level}</td>
                        <td>{truncate(l.message, 120)}</td>
                        <td className="mono">{l.latencyMs ?? "-"}</td>
                        <td className="mono">{l.stacktrace ? "yes" : "-"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                <div className="card" style={{ background: "rgba(255,255,255,.025)" }}>
                  <div className="cardHeader">
                    <div className="cardTitle">Selected log</div>
                  </div>
                  <div className="cardBody">
                    {!selectedLog ? (
                      <div className="small">Select a log row to inspect details.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                        <Detail label="Timestamp" value={formatDateTime(selectedLog.ts)} />
                        <Detail label="Service" value={selectedLog.service} />
                        <Detail label="Level" value={selectedLog.level} />
                        <Detail
                          label="Latency"
                          value={selectedLog.latencyMs != null ? `${selectedLog.latencyMs} ms` : "-"}
                        />

                        <InlineSection title="Message" defaultOpen>
                          <div style={inlineTextStyle}>{selectedLog.message}</div>
                        </InlineSection>

                        {selectedLog.stacktrace && (
                          <InlineSection
                            title="Stacktrace"
                            defaultOpen={showStacktrace}
                            onToggle={setShowStacktrace}
                          >
                            <div style={stackStyle}>{selectedLog.stacktrace}</div>
                          </InlineSection>
                        )}

                        <InlineSection
                          title="Raw JSON"
                          defaultOpen={showRawJson}
                          onToggle={setShowRawJson}
                        >
                          <div style={stackStyle}>
                            {JSON.stringify(selectedLog, null, 2)}
                          </div>
                        </InlineSection>
                      </div>
                    )}
                  </div>
                </div>

                <div className="card" style={{ background: "rgba(255,255,255,.025)" }}>
                  <div className="cardHeader">
                    <div className="cardTitle">Ask AI</div>
                  </div>
                  <div className="cardBody">
                    <div className="row" style={{ marginBottom: 8 }}>
                      <button
                        className="btn"
                        style={{
                          background: agentMode === "log" ? "rgba(255,255,255,.12)" : undefined,
                        }}
                        onClick={() => setAgentMode("log")}
                      >
                        This log
                      </button>
                      <button
                        className="btn"
                        style={{
                          background:
                            agentMode === "overview" ? "rgba(255,255,255,.12)" : undefined,
                        }}
                        onClick={() => setAgentMode("overview")}
                      >
                        Overall incident
                      </button>
                    </div>

                    <textarea
                      value={agentQuestion}
                      onChange={(e) => setAgentQuestion(e.target.value)}
                      placeholder={
                        agentMode === "log"
                          ? "e.g. What is the likely cause of this error?"
                          : "e.g. What is the biggest issue right now and what should I check first?"
                      }
                      style={textareaStyle}
                    />

                    <div className="row" style={{ marginTop: 8 }}>
                      <button className="btn btnPrimary" onClick={askAgent} disabled={agentLoading}>
                        {agentLoading ? "Thinking..." : "Ask AI"}
                      </button>
                    </div>

                    {agentAnswer && (
                      <InlineSection
                        title="AI answer"
                        defaultOpen={showAiAnswer}
                        onToggle={setShowAiAnswer}
                      >
                        <div style={inlineTextStyle}>{agentAnswer}</div>
                      </InlineSection>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {tab === "alerts" && (
            <div style={{ display: "flex", flexDirection: "column", gap: 10, maxHeight: 560, overflow: "auto" }}>
              {alerts.length === 0 ? (
                <div className="small">No alerts yet.</div>
              ) : (
                alerts.map((a, idx) => (
                  <div key={`${a.ts}-${idx}`} className="alertNEW" style={{ padding: 12, borderRadius: 12 }}>
                    <div className="row" style={{ justifyContent: "space-between" }}>
                      <span className="badge">{a.category}</span>
                      <span className="badge">score={a.score.toFixed(2)}</span>
                    </div>
                    <div style={{ fontWeight: 800, marginTop: 8 }}>{a.service} • {a.level}</div>
                    <div style={{ marginTop: 6 }}>{a.message}</div>
                    <div className="small" style={{ marginTop: 6 }}>{a.explanation}</div>
                    <div className="small mono" style={{ marginTop: 6 }}>{formatDateTime(a.ts)}</div>
                  </div>
                ))
              )}
            </div>
          )}

          {tab === "analysis" && (
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div className="card" style={{ background: "rgba(255,255,255,.025)" }}>
                <div className="cardHeader">
                  <div className="cardTitle">Recent summaries</div>
                </div>
                <div className="cardBody" style={{ display: "flex", flexDirection: "column", gap: 10, maxHeight: 520, overflow: "auto" }}>
                  {summaries.length === 0 ? (
                    <div className="small">No summaries available.</div>
                  ) : (
                    summaries.slice(0, 12).map((s, i) => (
                      <div
                        key={`${s.ts}-${i}`}
                        style={{
                          border: "1px solid rgba(255,255,255,.08)",
                          borderRadius: 12,
                          padding: 12,
                        }}
                      >
                        <div className="row">
                          <span className="badge">{s.category}</span>
                          <span className="badge">{s.severity}</span>
                        </div>
                        <div style={{ marginTop: 8, fontWeight: 700 }}>{s.summary}</div>
                        <div className="small mono" style={{ marginTop: 6 }}>{formatDateTime(s.ts)}</div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="card" style={{ background: "rgba(255,255,255,.025)" }}>
                <div className="cardHeader">
                  <div className="cardTitle">Overall AI Q&A</div>
                </div>
                <div className="cardBody">
                  <div className="small" style={{ marginBottom: 8 }}>
                    Ask about the overall incident state using latest summary + recent alerts + recent logs.
                  </div>

                  <textarea
                    value={agentMode === "overview" ? agentQuestion : ""}
                    onChange={(e) => {
                      setAgentMode("overview");
                      setAgentQuestion(e.target.value);
                    }}
                    placeholder="e.g. What is the biggest issue right now and what should I do first?"
                    style={textareaStyle}
                  />

                  <div className="row" style={{ marginTop: 10 }}>
                    <button
                      className="btn btnPrimary"
                      onClick={() => {
                        setAgentMode("overview");
                        askAgent();
                      }}
                      disabled={agentLoading}
                    >
                      {agentLoading && agentMode === "overview"
                        ? "Thinking..."
                        : "Ask AI about overall incident"}
                    </button>
                  </div>

                  {agentMode === "overview" && agentAnswer && (
                    <InlineSection
                      title="AI answer"
                      defaultOpen={showAiAnswer}
                      onToggle={setShowAiAnswer}
                    >
                      <div style={inlineTextStyle}>{agentAnswer}</div>
                    </InlineSection>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      {toast && (
        <div
          style={{
            position: "fixed",
            right: 16,
            bottom: 16,
            maxWidth: 380,
            padding: 14,
            border: "1px solid rgba(255,255,255,.12)",
            background: "rgba(8,10,18,.96)",
            borderRadius: 14,
            boxShadow: "0 18px 50px rgba(0,0,0,.45)",
            zIndex: 9999,
          }}
        >
          <div style={{ fontWeight: 900, marginBottom: 6 }}>{toast.title}</div>
          <div className="small" style={{ whiteSpace: "pre-wrap" }}>{toast.body}</div>
          <div style={{ marginTop: 10, display: "flex", justifyContent: "flex-end" }}>
            <button className="btn" onClick={() => setToast(null)}>Close</button>
          </div>
        </div>
      )}
    </div>
  );
}

function MetricCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div
      style={{
        border: "1px solid rgba(255,255,255,.08)",
        borderRadius: 14,
        padding: 14,
        background: "rgba(255,255,255,.025)",
      }}
    >
      <div className="small">{label}</div>
      <div style={{ fontSize: 26, fontWeight: 900, marginTop: 6 }}>{value}</div>
      <div className="small mono" style={{ marginTop: 4 }}>{hint}</div>
    </div>
  );
}

function InlineListCard({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="card" style={{ background: "rgba(255,255,255,.025)" }}>
      <div className="cardHeader">
        <div className="cardTitle">{title}</div>
      </div>
      <div className="cardBody">
        {items?.length ? (
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {items.map((x, i) => (
              <li key={i} className="small">{x}</li>
            ))}
          </ul>
        ) : (
          <div className="small">—</div>
        )}
      </div>
    </div>
  );
}

function InlineSection({
  title,
  children,
  defaultOpen = true,
  onToggle,
}: {
  title: string;
  children: React.ReactNode;
  defaultOpen?: boolean;
  onToggle?: (open: boolean) => void;
}) {
  const [open, setOpen] = useState(defaultOpen);

  useEffect(() => {
    setOpen(defaultOpen);
  }, [defaultOpen]);

  return (
    <div style={{ border: "1px solid rgba(255,255,255,.08)", borderRadius: 12, overflow: "hidden" }}>
      <button
        className="btn"
        style={{
          width: "100%",
          justifyContent: "space-between",
          display: "flex",
          background: "rgba(255,255,255,.03)",
          border: "none",
          borderRadius: 0,
        }}
        onClick={() => {
          const next = !open;
          setOpen(next);
          onToggle?.(next);
        }}
      >
        <span>{title}</span>
        <span>{open ? "−" : "+"}</span>
      </button>
      {open && <div style={{ padding: 12 }}>{children}</div>}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      className="btn"
      onClick={onClick}
      style={{
        background: active ? "rgba(255,255,255,.12)" : undefined,
        borderColor: active ? "rgba(255,255,255,.22)" : undefined,
      }}
    >
      {children}
    </button>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="small">{label}</div>
      <div style={{ marginTop: 3, fontWeight: 700 }}>{value}</div>
    </div>
  );
}

function formatDateTime(ts: string) {
  try {
    return new Date(ts).toLocaleString("sv-SE");
  } catch {
    return ts;
  }
}

function truncate(text: string, n: number) {
  return text.length > n ? `${text.slice(0, n)}…` : text;
}

function percentile(values: number[], p: number) {
  const arr = values.filter((v) => Number.isFinite(v)).sort((a, b) => a - b);
  if (!arr.length) return 0;
  const idx = Math.ceil((p / 100) * arr.length) - 1;
  return (arr[Math.max(0, Math.min(arr.length - 1, idx))] ?? 0) | 0;
}

const inlineTextStyle: React.CSSProperties = {
  whiteSpace: "pre-wrap",
  wordBreak: "break-word",
  lineHeight: 1.6,
  fontSize: 13,
};

const stackStyle: React.CSSProperties = {
  whiteSpace: "pre-wrap",
  wordBreak: "break-word",
  lineHeight: 1.55,
  fontSize: 12,
  fontFamily:
    'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
  background: "rgba(255,255,255,.03)",
  border: "1px solid rgba(255,255,255,.08)",
  borderRadius: 10,
  padding: 12,
};

const textareaStyle: React.CSSProperties = {
  width: "100%",
  minHeight: 96,
  borderRadius: 12,
  padding: 12,
  border: "1px solid rgba(255,255,255,.12)",
  background: "rgba(255,255,255,.04)",
  color: "inherit",
  resize: "vertical",
};