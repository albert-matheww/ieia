import { Card } from "./Card";
import { DeltaTag } from "./primitives";
import { num } from "@/lib/format";
import { cn } from "@/lib/cn";

/**
 * A single instrument readout: uppercase panel label, large mono value, delta.
 * `invertDelta` = down is good (impact metrics). For the score, down is bad.
 */
export function StatCard({
  label, value, unit, delta, deltaSuffix = "%", invertDelta = true, accent, hint, dp,
}: {
  label: string;
  value: number | null;
  unit?: string;
  delta?: number | null;
  deltaSuffix?: string;
  invertDelta?: boolean;
  accent?: string;
  hint?: string;
  dp?: number;
}) {
  return (
    <Card accent={accent} className="animate-fade-up">
      <div className="p-4">
        <p className="label-inst">{label}</p>
        <div className="mt-2 flex items-baseline gap-1.5">
          <span className="readout text-[26px] font-medium leading-none">{num(value, { dp })}</span>
          {unit && <span className="font-mono text-[11px] text-content-dim">{unit}</span>}
        </div>
        <div className={cn("mt-2 flex items-center gap-2 text-[11px]")}>
          {delta !== undefined ? <DeltaTag value={delta} invert={invertDelta} suffix={deltaSuffix} /> : null}
          {hint && <span className="text-content-dim">{hint}</span>}
        </div>
      </div>
    </Card>
  );
}
