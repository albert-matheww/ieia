import type { ReactNode } from "react";

/**
 * Split auth screen. Left: a live "monitoring panel" motif — a faint gauge arc
 * over the deep pine field, stating the product thesis (measure, don't guess).
 */
export function AuthShell({ title, sub, children }: { title: string; sub: string; children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <aside className="relative hidden overflow-hidden bg-pine p-10 text-paper lg:flex lg:flex-col lg:justify-between">
        <div className="flex items-center gap-2.5">
          <svg width="28" height="28" viewBox="0 0 32 32"><rect width="32" height="32" rx="6" fill="#0f3123" /><path d="M6 22a10 10 0 0 1 20 0" fill="none" stroke="#E9C46A" strokeWidth="2.5" /><line x1="16" y1="22" x2="22" y2="13" stroke="#F6F4EF" strokeWidth="2" strokeLinecap="round" /></svg>
          <span className="font-display text-[15px] font-semibold tracking-tight">IEIA</span>
        </div>

        <div className="relative">
          <svg viewBox="0 0 400 220" className="absolute -left-6 -top-24 w-[120%] opacity-[0.13]" aria-hidden>
            <path d="M20 200a180 180 0 0 1 360 0" fill="none" stroke="#F6F4EF" strokeWidth="2" />
            {Array.from({ length: 19 }).map((_, i) => {
              const a = (Math.PI * (i / 18)) - Math.PI;
              return <line key={i} x1={200 + Math.cos(a) * 168} y1={200 + Math.sin(a) * 168} x2={200 + Math.cos(a) * (i % 3 ? 176 : 182)} y2={200 + Math.sin(a) * (i % 3 ? 176 : 182)} stroke="#F6F4EF" strokeWidth="1.5" />;
            })}
          </svg>
          <h1 className="relative font-display text-[32px] font-semibold leading-tight tracking-tight">
            Measure the impact.<br />Don't estimate it.
          </h1>
          <p className="relative mt-4 max-w-sm text-[13.5px] leading-relaxed text-paper/75">
            IEIA turns your electricity, fuel, transport, water and waste activity into a
            traceable footprint, a transparent score, forecasts, and prioritised actions —
            every number backed by a source.
          </p>
        </div>

        <div className="flex gap-6 font-mono text-[10.5px] uppercase tracking-[0.14em] text-paper/55">
          <span>Configurable factors</span><span>· Time-series ML</span><span>· Grounded AI</span>
        </div>
      </aside>

      <main className="flex items-center justify-center bg-surface px-6 py-12">
        <div className="w-full max-w-sm animate-fade-up">
          <h2 className="font-display text-[20px] font-semibold tracking-tight text-content">{title}</h2>
          <p className="mb-6 mt-1 text-[13px] text-content-muted">{sub}</p>
          {children}
        </div>
      </main>
    </div>
  );
}
