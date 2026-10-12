import { useEffect, useRef, useState } from "react";
import { signed } from "@/lib/format";

/**
 * The signature element: an analog monitoring gauge. A semicircular arc with tick
 * marks and a needle that settles on the environmental score. Score reads in
 * large mono numerals at the pivot — an instrument readout, not a marketing stat.
 */
export function ScoreGauge({
  score, delta, size = 260,
}: { score: number | null; delta?: number | null; size?: number }) {
  const target = score ?? 0;
  const [angle, setAngle] = useState(-90);
  const reduce = useRef(typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches);

  useEffect(() => {
    const final = -90 + (Math.max(0, Math.min(100, target)) / 100) * 180;
    if (reduce.current) { setAngle(final); return; }
    const start = performance.now();
    const from = -90;
    let raf = 0;
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / 900);
      const eased = 1 - Math.pow(1 - p, 3);
      setAngle(from + (final - from) * eased);
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target]);

  const w = size;
  const h = size * 0.62;
  const cx = w / 2;
  const cy = h - 8;
  const r = w / 2 - 14;

  const ticks = Array.from({ length: 11 }, (_, i) => i);
  const bandColor = target >= 70 ? "var(--positive-hex)" : target >= 50 ? "#E9C46A" : "#B04A34";

  return (
    <div className="flex flex-col items-center">
      <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} role="img" aria-label={`Environmental score ${score ?? "unknown"} out of 100`}>
        {/* track */}
        <path
          d={arc(cx, cy, r, -90, 90)}
          fill="none"
          stroke="rgb(var(--line))"
          strokeWidth={10}
          strokeLinecap="round"
        />
        {/* value band */}
        <path
          d={arc(cx, cy, r, -90, -90 + (Math.max(0, Math.min(100, target)) / 100) * 180)}
          fill="none"
          stroke={bandColor}
          strokeWidth={10}
          strokeLinecap="round"
          style={{ transition: reduce.current ? undefined : "d 0.9s cubic-bezier(0.22,1,0.36,1)" }}
        />
        {/* ticks */}
        {ticks.map((i) => {
          const a = (-90 + i * 18) * (Math.PI / 180);
          const inner = r - 12;
          const outer = r - (i % 5 === 0 ? 2 : 6);
          return (
            <line
              key={i}
              x1={cx + Math.cos(a) * inner}
              y1={cy + Math.sin(a) * inner}
              x2={cx + Math.cos(a) * outer}
              y2={cy + Math.sin(a) * outer}
              stroke="rgb(var(--content-dim))"
              strokeWidth={i % 5 === 0 ? 1.5 : 1}
              opacity={0.7}
            />
          );
        })}
        {/* needle */}
        <g style={{ transform: `rotate(${angle}deg)`, transformOrigin: `${cx}px ${cy}px` }}>
          <line x1={cx} y1={cy} x2={cx} y2={cy - r + 6} stroke="rgb(var(--content))" strokeWidth={2.5} strokeLinecap="round" />
          <circle cx={cx} cy={cy} r={5} fill="rgb(var(--content))" />
        </g>
      </svg>
      <div className="-mt-9 flex flex-col items-center">
        <span className="readout text-[34px] font-semibold leading-none">{score === null ? "—" : Math.round(score)}</span>
        <span className="label-inst mt-1">Score / 100</span>
        {delta !== undefined && delta !== null && (
          <span className={`mt-1 font-mono text-[11px] ${delta >= 0 ? "text-positive" : "text-negative"}`}>
            {delta >= 0 ? "▲" : "▼"} {signed(delta, 1)} vs last month
          </span>
        )}
      </div>
    </div>
  );
}

function polar(cx: number, cy: number, r: number, deg: number) {
  const a = (deg * Math.PI) / 180;
  return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a) };
}

function arc(cx: number, cy: number, r: number, startDeg: number, endDeg: number) {
  const start = polar(cx, cy, r, startDeg);
  const end = polar(cx, cy, r, endDeg);
  const large = endDeg - startDeg <= 180 ? 0 : 1;
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${large} 1 ${end.x} ${end.y}`;
}
