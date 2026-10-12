import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Area, ComposedChart, CartesianGrid, Line, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { predictionsApi } from "@/api";
import type { PredictionResponse } from "@/api/types";
import { PageHeader, LoadingBlock, ErrorState, EmptyState } from "@/components/ui/states";
import { Card, CardBody, CardHead } from "@/components/ui/Card";
import { Button, Select, Badge } from "@/components/ui/primitives";
import { StatCard } from "@/components/ui/StatCard";
import { num, pct, signed, monthShort, monthLabel, categoryColor } from "@/lib/format";

const METRICS = [
  { key: "CO2E", label: "CO₂e", unit: "kg" },
  { key: "ENERGY", label: "Energy", unit: "MJ" },
  { key: "WATER", label: "Water", unit: "L" },
  { key: "SCORE", label: "Score", unit: "" },
];

export default function PredictionsPage() {
  const [metric, setMetric] = useState("CO2E");
  const [horizon, setHorizon] = useState(1);
  const meta = METRICS.find((m) => m.key === metric)!;

  const pred = useQuery({
    queryKey: ["prediction", metric, horizon],
    queryFn: () => predictionsApi.get(metric, horizon),
    retry: false,
  });
  const hist = useQuery({ queryKey: ["prediction-history", metric], queryFn: () => predictionsApi.history(metric), retry: false });
  const recompute = useMutation({
    mutationFn: () => predictionsApi.create(metric, horizon, true),
    onSuccess: () => pred.refetch(),
  });

  return (
    <div>
      <PageHeader
        title="Predictions"
        sub="Interpretable time-series models with cross-validated error and an explicit uncertainty band."
        actions={
          <>
            <Select aria-label="Metric" value={metric} onChange={(e) => setMetric(e.target.value)} className="w-32">
              {METRICS.map((m) => <option key={m.key} value={m.key}>{m.label}</option>)}
            </Select>
            <Select aria-label="Horizon" value={horizon} onChange={(e) => setHorizon(Number(e.target.value))} className="w-32">
              {[1, 2, 3, 6].map((h) => <option key={h} value={h}>{h} month{h > 1 ? "s" : ""}</option>)}
            </Select>
            <Button variant="ghost" loading={recompute.isPending} onClick={() => recompute.mutate()}>Recompute</Button>
          </>
        }
      />

      {pred.isLoading ? (
        <LoadingBlock label="Running the model" />
      ) : pred.error ? (
        (pred.error as any)?.code === "UNPROCESSABLE"
          ? <EmptyState title="Not enough history to forecast" body={(pred.error as any).message} />
          : (pred.error as any)?.code === "ML_SERVICE_UNAVAILABLE"
            ? <EmptyState title="The ML service is unavailable" body="Forecasts are produced by a separate service. Start it and try again — no numbers are shown while it's down." />
            : <ErrorState error={pred.error} onRetry={pred.refetch} />
      ) : (
        <PredictionView p={pred.data as PredictionResponse} meta={meta} historyPoints={hist.data ?? []} />
      )}
    </div>
  );
}

function PredictionView({ p, meta, historyPoints }: { p: PredictionResponse; meta: { label: string; unit: string }; historyPoints: PredictionResponse[] }) {
  // Plot current -> predicted; the interval band widens from a point at "now".
  const rows = [
    { period: p.generatedForMonth, value: p.currentValue, band: [p.currentValue, p.currentValue] as [number, number] },
    {
      period: p.horizonMonth,
      value: p.predictedValue,
      band: [p.lowerBound ?? p.predictedValue, p.upperBound ?? p.predictedValue] as [number, number],
    },
  ];
  const isNaive = p.model.name === "SeasonalNaive";

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label={`Current monthly ${meta.label}`} value={p.currentValue} unit={meta.unit} accent={categoryColor("carbon")} />
        <StatCard label={`Predicted (${monthLabel(p.horizonMonth)})`} value={p.predictedValue} unit={meta.unit} accent={categoryColor("energy")} />
        <Card className="animate-fade-up">
          <div className="p-4">
            <p className="label-inst">Expected change</p>
            <p className={`readout mt-2 text-[26px] font-medium ${(p.expectedChangePct ?? 0) <= 0 ? "text-positive" : "text-negative"}`}>
              {pct(p.expectedChangePct)}
            </p>
            <p className="mt-2 font-mono text-[11px] text-content-dim">{signed(p.expectedChangeAbs)} {meta.unit}/mo</p>
          </div>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHead
          title="Forecast with uncertainty band"
          sub={p.confidenceNote ?? undefined}
          action={<Badge tone={isNaive ? "signal" : "neutral"}>{p.model.name}</Badge>}
        />
        <CardBody>
          <ResponsiveContainer width="100%" height={230}>
            <ComposedChart data={rows} margin={{ top: 8, right: 12, left: -8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="2 3" vertical={false} />
              <XAxis dataKey="period" tickFormatter={monthShort} axisLine={false} tickLine={false} />
              <YAxis width={48} domain={["auto", "auto"]} tickFormatter={(v) => num(v, { compact: true })} axisLine={false} tickLine={false} />
              <Tooltip
                content={({ active, payload, label }) => active && payload?.length ? (
                  <div className="rounded border border-line bg-surface-2 px-2.5 py-2 text-[11px] shadow-pop">
                    <p className="label-inst mb-1">{monthShort(String(label))}</p>
                    <p className="readout">{num(Number(payload.find((x) => x.dataKey === "value")?.value), { dp: 1 })} {meta.unit}</p>
                  </div>
                ) : null}
              />
              <Area type="linear" dataKey="band" stroke="none" fill="#2D6A4F" fillOpacity={0.14} />
              <Line type="linear" dataKey="value" stroke="#2D6A4F" strokeWidth={2.5} dot={{ r: 4, fill: "#2D6A4F" }} />
            </ComposedChart>
          </ResponsiveContainer>
          <p className="mt-2 text-[11px] text-content-dim">
            Band = ±1.96·σ of cross-validation residuals. Point forecasts are indicative, not exact.
          </p>
        </CardBody>
      </Card>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHead title="Model evaluation" sub="Expanding-window time-series cross-validation" />
          <CardBody>
            <dl className="grid grid-cols-2 gap-3 text-[12.5px]">
              {Object.entries(p.evaluation ?? {}).filter(([k]) => ["mae", "rmse", "r2", "cvFolds", "nTrain"].includes(k)).map(([k, v]) => (
                <div key={k} className="rounded border border-line bg-surface p-2.5">
                  <dt className="label-inst">{k.toUpperCase()}</dt>
                  <dd className="readout mt-1 text-[15px]">{v == null ? "—" : num(Number(v), { dp: 2 })}</dd>
                </div>
              ))}
            </dl>
          </CardBody>
        </Card>
        <Card>
          <CardHead title="Feature importance" sub="What the model leans on" />
          <CardBody className="space-y-2">
            {p.featureImportance.length === 0 && <p className="text-[12px] text-content-dim">Not available for this model.</p>}
            {p.featureImportance.slice(0, 6).map((f) => (
              <div key={f.feature} className="flex items-center gap-2 text-[12px]">
                <span className="w-28 font-mono text-[11px] text-content-muted">{f.feature}</span>
                <div className="h-2 flex-1 rounded-full bg-surface-3">
                  <div className="h-2 rounded-full bg-brand" style={{ width: `${Math.min(100, f.importance * 100)}%` }} />
                </div>
                <span className="readout w-10 text-right text-[11px]">{(f.importance * 100).toFixed(0)}%</span>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHead title="Limitations" />
        <CardBody>
          <ul className="list-disc space-y-1 pl-4 text-[12.5px] text-content-muted">
            {p.limitations.map((l) => <li key={l}>{l}</li>)}
          </ul>
        </CardBody>
      </Card>

      {historyPoints.length > 1 && (
        <p className="mt-3 text-[11px] text-content-dim">{historyPoints.length} past forecasts on record for this metric.</p>
      )}
    </>
  );
}
