import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { NAV } from "./nav";
import { useAuth } from "@/context/AuthContext";
import { getTheme, setTheme, type Theme } from "@/lib/theme";
import { cn } from "@/lib/cn";

function Logo() {
  return (
    <div className="flex items-center gap-2.5">
      <svg width="26" height="26" viewBox="0 0 32 32" aria-hidden>
        <rect width="32" height="32" rx="6" fill="#1B4332" />
        <path d="M6 22a10 10 0 0 1 20 0" fill="none" stroke="#E9C46A" strokeWidth="2.5" />
        <line x1="16" y1="22" x2="22" y2="13" stroke="#F6F4EF" strokeWidth="2" strokeLinecap="round" />
      </svg>
      <span className="font-display text-[15px] font-semibold tracking-tight text-content">IEIA</span>
    </div>
  );
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const [theme, setThemeState] = useState<Theme>(getTheme());
  const [mobileOpen, setMobileOpen] = useState(false);
  const loc = useLocation();

  useEffect(() => setMobileOpen(false), [loc.pathname]);

  const toggleTheme = () => {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    setThemeState(next);
  };

  const nav = NAV.filter((n) => !n.adminOnly || user?.role === "ADMIN");

  return (
    <div className="flex min-h-screen bg-surface">
      {/* Sidebar */}
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r border-line bg-surface-2 transition-transform lg:translate-x-0",
          mobileOpen ? "translate-x-0" : "-translate-x-full",
        )}
      >
        <div className="flex h-14 items-center border-b border-line px-4"><Logo /></div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto p-3">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) => cn("nav-link", isActive && "nav-link-active")}
            >
              <span className="text-content-dim">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-line p-3">
          <div className="flex items-center gap-2.5 rounded px-2 py-1.5">
            <div className="grid h-7 w-7 place-items-center rounded-full bg-brand-strong font-mono text-[11px] text-paper">
              {(user?.fullName ?? "?").slice(0, 1).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[12px] font-medium text-content">{user?.fullName}</p>
              <p className="truncate font-mono text-[10px] uppercase tracking-wider text-content-dim">{user?.role}</p>
            </div>
          </div>
        </div>
      </aside>

      {mobileOpen && <div className="fixed inset-0 z-30 bg-ink/30 lg:hidden" onClick={() => setMobileOpen(false)} />}

      {/* Main */}
      <div className="flex min-w-0 flex-1 flex-col lg:pl-60">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-line bg-surface/85 px-4 backdrop-blur lg:px-8">
          <button className="rounded p-1.5 text-content-muted hover:bg-surface-3 lg:hidden" onClick={() => setMobileOpen((o) => !o)} aria-label="Menu">
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M2 4.5h14M2 9h14M2 13.5h14" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></svg>
          </button>
          <div className="ml-auto flex items-center gap-2">
            <button onClick={toggleTheme} className="rounded p-1.5 text-content-muted hover:bg-surface-3" aria-label="Toggle theme" title="Toggle theme">
              {theme === "dark" ? (
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19" strokeLinecap="round" /></svg>
              ) : (
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z" strokeLinejoin="round" /></svg>
              )}
            </button>
            <button onClick={() => logout()} className="btn btn-sm btn-ghost">Sign out</button>
          </div>
        </header>
        <main className="mx-auto w-full max-w-[1180px] flex-1 px-4 py-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
