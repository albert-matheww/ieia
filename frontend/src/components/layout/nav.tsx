import type { ReactNode } from "react";

export interface NavItem { to: string; label: string; icon: ReactNode; adminOnly?: boolean; }

const I = (path: string) => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
    {path.split("|").map((d, i) => <path key={i} d={d} />)}
  </svg>
);

export const NAV: NavItem[] = [
  { to: "/", label: "Dashboard", icon: I("M3 13h8V3H3zM13 21h8V11h-8zM13 3v6h8V3zM3 21h8v-6H3z") },
  { to: "/data", label: "Environmental Data", icon: I("M4 7c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3z|M4 7v10c0 1.7 3.6 3 8 3s8-1.3 8-3V7|M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3") },
  { to: "/impact", label: "Impact Analysis", icon: I("M4 19V5|M4 19h16|M8 15l3-4 3 2 4-6") },
  { to: "/predictions", label: "Predictions", icon: I("M3 12h4l3 8 4-16 3 8h4") },
  { to: "/anomalies", label: "Anomaly Detection", icon: I("M12 3 2 21h20zM12 10v5|M12 18h.01") },
  { to: "/simulator", label: "What-If Simulator", icon: I("M6 3v6a6 6 0 0 0 12 0V3|M6 21v-6a6 6 0 0 1 12 0v6|M6 3h12|M6 21h12") },
  { to: "/recommendations", label: "Recommendations", icon: I("M9 18h6|M10 22h4|M12 2a7 7 0 0 0-4 12.7c.6.5 1 1.3 1 2.1h6c0-.8.4-1.6 1-2.1A7 7 0 0 0 12 2z") },
  { to: "/assistant", label: "AI Assistant", icon: I("M21 12a9 9 0 1 1-4-7.5|M8 12h.01M12 12h.01M16 12h.01|M3 21l2-5") },
  { to: "/reports", label: "Reports", icon: I("M14 3v5h5|M14 3H6v18h12V8zM9 13h6M9 17h6M9 9h2") },
  { to: "/settings", label: "Settings", icon: I("M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z|M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 7 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1A2 2 0 1 1 2.3 17l.1-.1a1.6 1.6 0 0 0-1.1-2.7H1a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 2.3 7l-.1-.1A2 2 0 1 1 5 4.1l.1.1a1.6 1.6 0 0 0 1.8.3H7a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 2.7 1.1l.1-.1A2 2 0 1 1 21.7 7l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H23a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z") },
];
