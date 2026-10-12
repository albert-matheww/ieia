import { cn } from "@/lib/cn";

/**
 * Horizontal "level meter" for a category sub-score (0–100), with tick marks —
 * the linear counterpart to the ScoreGauge on a monitoring panel.
 */
export function LevelMeter({
  label, value, color, className,
}: { label: string; value: number | null; color: string; className?: string }) {
  const v = value === null ? 0 : Math.max(0, Math.min(100, value));
  return (
    <div className={cn("", className)}>
      <div className="flex items-baseline justify-between">
        <span className="label-inst">{label}</span>
        <span className="readout text-[12px]">{value === null ? "—" : Math.round(value)}</span>
      </div>
      <div className="relative mt-1.5 h-2 rounded-full bg-surface-3">
        <div
          className="absolute inset-y-0 left-0 rounded-full transition-[width] duration-700 ease-out"
          style={{ width: `${v}%`, background: color }}
        />
        {[25, 50, 75].map((t) => (
          <span key={t} className="absolute top-1/2 h-2.5 w-px -translate-y-1/2 bg-surface" style={{ left: `${t}%` }} />
        ))}
      </div>
    </div>
  );
}
