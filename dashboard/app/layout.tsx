import "./globals.css";
import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "InsightStream",
  description: "AI-Powered Real-time Log Intelligence",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <div className="bg">
          <header className="header">
            <div className="brand">
              <div className="logo">IS</div>
              <div>
                <div className="title">InsightStream</div>
                <div className="sub">AI-Powered Real-time Log Intelligence</div>
              </div>
            </div>
            <nav className="row">
              <Link className="btn" href="/">Home</Link>
              <Link className="btn btnPrimary" href="/dashboard">Dashboard</Link>
            </nav>
          </header>
          <main className="main">{children}</main>
          <footer className="footer">
            <span>Demo dashboard • Built for DigitalOcean Gradient Hackathon</span>
          </footer>
        </div>
      </body>
    </html>
  );
}
