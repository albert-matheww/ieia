import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { scenariosApi } from "@/api";
import type { ScenarioResult } from "@/api/types";
import { PageHeader, ErrorState } from "@/components/ui/states";
import { Card, CardBody, CardHead } from "@/components/ui/Card";
import { Button, Field, Select } from "@/components/ui/primitives";
import { useToast } from "@/components/ui/Toast";
import { num, signed, categoryColor } from "@/lib/format";

const monthsAgo = (n: number) => { const d = new Date(); d.setMonth(d.getMonth() - n); return d.toISOString().slice(0, 7); };

type Lever = {
  targetType: "CATEGORY" | "ACTIVITY_TYPE";
  targetValue: string;
  change: "PCT_REDUCTION" | "PCT_INCREASE" | "MODAL_SHIFT" | "RECYCLING_RATE";
  value: number;
  toActivityCode?: string;
};

const PRESETS: { label: string; lever: Lever }[] = [
  { label: "Cut electricity 20%", lever: { targetType: "CATEGORY", targetValue: "ELECTRICITY", change: "PCT_REDUCTION", value: 20 } },
  { label: "30% of car km → metro", lever: { targetType: "ACTIVITY_TYPE", targetValue: "car_petrol", change: "MODAL_SHIFT", value: 30, toActivityCode: "metro_rail" } },
  { label: "Recycling rate → 70%", lever: { targetType: "CATEGORY", targetValue: "WASTE", change: "RECYCLING_RATE", value: 70 } },
  { label: "Cut fuel 15%", lever: { targetType: "CATEGORY", targetValue: "FUEL", change: "PCT_REDUCTION", value: 15 } },
];

export default function SimulatorPage() {
  const toast = useToast();
  const [from] = useState(monthsAgo(3));
  const [to] = useState(monthsAgo(1));
  const [levers, setLevers] = useState<Lever[]>([PRESETS[0].lever]);
  const [result, setResult] = useState<ScenarioResult | null>(null);

  const simulate = useMutation({
    mutationFn: () => scenariosApi.simulate({
      name: "scenario",
      basePeriodFrom: from,
      basePeriodTo: to,
      adjustments: levers.map((l) => ({
        target: { type: l.targetType, value: l.targetValue },
        change: l.change,
        value: l.value,
        params: l.change === "MODAL_SHIFT" ? { toActivityCode: l.toActivityCode, sharePct: l.value } : undefined,
      })),
    }),
    onSuccess: (r) => setResult(r),
    onError: (e: any) => toast.push(e?.message ?? "Couldn't run the scenario", "error"),
  });

  const save = useMutation({
    mutationFn: (name: string) => scenariosApi.save({
      name, basePeriodFrom: from, basePeriodTo: to,
      adjustments: levers.map((l) => ({
        target: { type: l.targetType, value: l.targetValue }, change: l.change, value: l.value,
        params: l.change === "MODAL_SHIFT" ? { toActivityCode: l.toActivityCode, sharePct: l.value } : undefined,
      })),
    }),
    onSuccess: () => toast.push("Scenario saved", "success"),
  });

  const setLever = (i: number, patch: Partial<Lever>) => setLevers((ls) => ls.map((l, idx) => idx === i ? { ...l, ...patch } : l));

  return (
    <div>
      <PageHeader
        title="What-If Simulator"
        sub={`Deterministic — every lever is applied to your real baseline (avg of ${from}–${to}) and the real emission factors.`}
      />

      <div className="grid gap-4 lg:grid-cols-[380px_1fr]">
        <Card>
          <CardHead title="Levers" action={<Button size="sm" variant="ghost" onClick={() => setLevers((l) => [...l, PRESETS[1].lever])}>Add</Button>} />
          <CardBody className="space-y-4">
            <div className="flex flex-wrap gap-1.5">
              {PRESETS.map((p) => (
                <button key={p.label} onClick={() => { setLevers([p.lever]); }} className="chip border-line hover:bg-surface-3">{p.label}</button>
              ))}
            </div>
            {levers.map((l, i) => (
              <div key={i} className="rounded border border-line bg-surface p-3">
                <div className="grid grid-cols-2 gap-2">
                  <Select label="Target" value={`${l.targetType}:${l.targetValue}`} onChange={(e) => {
                    const [tt, tv] = e.target.value.split(":");
                    setLever(i, { targetType: tt as Lever["targetType"], targetValue: tv, change: tv === "WASTE" ? "RECYCLING_RATE" : "PCT_REDUCTION" });
                  }}>
                    <optgroup label="Category">
                      {["ELECTRICITY", "TRANSPORT", "FUEL", "WATER", "WASTE", "MATERIALS"].map((c) => <option key={c} value={`CATEGORY:${c}`}>{cap(c)}</option>)}
                    </optgroup>
                    <optgroup label="Activity">
                      {["car_petrol", "two_wheeler", "electricity_grid"].map((c) => <option key={c} value={`ACTIVITY_TYPE:${c}`}>{c.replace(/_/g, " ")}</option>)}
                    </optgroup>
                  </Select>
                  <Select label="Change" value={l.change} onChange={(e) => setLever(i, { change: e.target.value as Lever["change"] })}>
                    <option value="PCT_REDUCTION">Reduce %</option>
                    <option value="PCT_INCREASE">Increase %</option>
                    <option value="MODAL_SHIFT">Shift to…</option>
                    <option value="RECYCLING_RATE">Set recycling %</option>
                  </Select>
                </div>
                <div className="mt-2 grid grid-cols-2 gap-2">
                  <Field label={l.change === "RECYCLING_RATE" ? "Target %" : l.change === "MODAL_SHIFT" ? "Share %" : "Percent"} type="number" min="0" max="100" value={l.value} onChange={(e) => setLever(i, { value: Number(e.target.value) })} />
                  {l.change === "MODAL_SHIFT" && (
                    <Select label="Shift to" value={l.toActivityCode ?? "metro_rail"} onChange={(e) => setLever(i, { toActivityCode: e.target.value })}>
                      {["metro_rail", "bus", "two_wheeler"].map((c) => <option key={c} value={c}>{c.replace(/_/g, " ")}</option>)}
                    </Select>
                  )}
                </div>
                {levers.length > 1 && (
                  <button className="mt-2 text-[11px] text-negative hover:underline" onClick={() => setLevers((ls) => ls.filter((_, idx) => idx !== i))}>Remove lever</button>
                )}
              </div>
            ))}
            <Button className="w-full" loading={simulate.isPending} onClick={() => simulate.mutate()}>Run scenario</Button>
          </CardBody>
        </Card>

        <div>
          {simulate.error && <ErrorState error={simulate.error} />}
          {!result && !simulate.error && (
            <Card><CardBody className="grid place-items-center py-20 text-center text-[13px] text-content-dim">
              Set your levers and run a scenario. Results compare against your real recent months.
            </CardBody></Card>
          )}
          {result && <ScenarioResultView r={result} onSave={() => { const n = prompt("Name this scenario"); if (n) save.mutate(n); }} />}
        </div>
      </div>
    </div>
  );
}

function ScenarioResultView({ r, onSave }: { r: ScenarioResult; onSave: () => void }) {
  const rows: { label: string; base: number; proj: number; delta: number; unit: string; invert?: boolean }[] = [
    { label: "CO₂e", base: r.baseline.co2eKg, proj: r.projected.co2eKg, delta: r.deltas.co2eKg, unit: "kg" },
    { label: "Energy", base: r.baseline.energyMj, proj: r.projected.energyMj, delta: r.deltas.energyMj, unit: "MJ" },
    { label: "Water", base: r.baseline.waterLitre, proj: r.projected.waterLitre, delta: r.deltas.waterLitre, unit: "L" },
    { label: "Waste", base: r.baseline.wasteKg, proj: r.projected.wasteKg, delta: r.deltas.wasteKg, unit: "kg" },
    { label: "Score", base: r.baseline.overallScore, proj: r.projected.overallScore, delta: r.deltas.overallScore, unit: "", invert: true },
  ];
  return (
    <div className="space-y-4">
      <Card accent="#2D6A4F">
        <CardHead title="Projected vs baseline" sub={`${r.monthsAveraged} month(s) averaged`} action={<Button size="sm" variant="ghost" onClick={onSave}>Save scenario</Button>} />
        <CardBody>
          <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
            {rows.map((row) => {
              const good = row.invert ? row.delta > 0 : row.delta < 0;
              return (
                <div key={row.label} className="rounded border border-line bg-surface p-3">
                  <p className="label-inst">{row.label}</p>
                  <p className="readout mt-1.5 text-[18px]">{num(row.proj)} <span className="text-[10px] text-content-dim">{row.unit}</span></p>
                  <p className={`mt-0.5 font-mono text-[11px] ${Math.abs(row.delta) < 0.05 ? "text-content-dim" : good ? "text-positive" : "text-negative"}`}>
                    {signed(row.delta)} {row.unit} · from {num(row.base)}
                  </p>
                </div>
              );
            })}
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHead title="By category (CO₂e/month)" />
        <CardBody className="space-y-2">
          {r.byCategory.filter((c) => Math.abs(c.deltaCo2e) > 0.01).map((c) => (
            <div key={c.category} className="flex items-center gap-3 text-[12.5px]">
              <span className="h-2 w-2 rounded-sm" style={{ background: categoryColor(c.category) }} />
              <span className="flex-1 text-content">{cap(c.category)}</span>
              <span className="readout text-[11px] text-content-dim">{num(c.baselineCo2e)} → {num(c.projectedCo2e)}</span>
              <span className={`readout w-16 text-right text-[11px] ${c.deltaCo2e < 0 ? "text-positive" : "text-negative"}`}>{signed(c.deltaCo2e)}</span>
            </div>
          ))}
        </CardBody>
      </Card>

      <Card>
        <CardHead title="Assumptions" sub="How each lever was applied" />
        <CardBody>
          <ul className="list-disc space-y-1.5 pl-4 text-[12px] text-content-muted">
            {r.assumptions.map((a, i) => <li key={i}>{a}</li>)}
          </ul>
          <p className="mt-3 border-t border-line pt-2 text-[11px] text-content-dim">{r.notes.join(" ")}</p>
        </CardBody>
      </Card>
    </div>
  );
}

function cap(s: string) { return s ? s.charAt(0) + s.slice(1).toLowerCase() : s; }
