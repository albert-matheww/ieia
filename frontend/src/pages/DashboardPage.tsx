import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { dashboardApi } from "@/api";
import type { Dashboard } from "@/api/types";
import { PageHeader, LoadingBlock, ErrorState, EmptyState } from "@/components/ui/states";
import { StatCard } from "@/components/ui/StatCard";
import { Card, CardBody, CardHead } from "@/components/ui/Card";
import { Badge } from "@/components/ui/primitives";
import { ScoreGauge } from "@/components/instruments/ScoreGauge";
import { LevelMeter } from "@/components/instruments/LevelMeter";
import { TrendChart, Donut, CategoryBars } from "@/components/charts/Charts";
import { num, monthLabel, categoryColor } from "@/lib/format";

const SEV_TONE = { HIGH: "negative", MEDIUM: "signal", LOW: "neutral" } as const;

export default function DashboardPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["dashboard"],
    queryFn: () => dashboardApi.get(),
  });

  if (isLoading) return <LoadingBlock label="Reading your console" />;
  if (error) {
    const notFound = (error as any)?.code === "NOT_FOUND";
    return notFound ? (
      <EmptyState
        title="No data on the console yet"
        body="Log some electricity, transport, water or waste activity and your score, trends and forecasts appear here."
        action={<Link to="/data" className="btn btn-md btn-primary">Add environmental data</Link>}
      />
    ) : <ErrorState error={error} onRetry={refetch} />;
  }

  const d = data as Dashboard;
  const s = d.summary;
  const cat = d.categoryScores;

  return (
    <div>
      <PageHeader
        title="Dashboard"
        sub={`Environmental console — ${monthLabel(d.period)}`}
        actions={<Badge tone="neutral">{d.dataQuality.monthsOfHistory} mo history</Badge>}
      />

      {/* Signature: the gauge + category level meters */}
      <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
        <Card className="animate-fade-up">
          <CardBody className="flex flex-col items-center pt-6">
            <ScoreGauge score={s.environmentalScore.value} delta={s.environmentalScore.delta} />
          </CardBody>
        </Card>
        <Card className="animate-fade-up">
          <CardHead title="Category readouts" sub="Sub-scores that compose the overall — higher is better" />
          <CardBody className="grid gap-4 sm:grid-cols-2">
            <LevelMeter label="Carbon" value={cat.carbon} color={categoryColor("carbon")} />
            <LevelMeter label="Energy" value={cat.energy} color={categoryColor("energy")} />
            <LevelMeter label="Water" value={cat.water} color={categoryColor("water")} />
            <LevelMeter label="Waste" value={cat.waste} color={categoryColor("waste")} />
            <LevelMeter label="Transport" value={cat.transport} color={categoryColor("transport")} />
            <div className="flex items-end">
              <Link to="/impact" className="btn btn-sm btn-ghost">Impact analysis →</Link>
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Stat readouts */}
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Total CO₂e" value={s.co2eKg.value} unit="kg" delta={s.co2eKg.deltaPct} accent={categoryColor("carbon")} />
        <StatCard label="Energy" value={s.energyMj.value} unit="MJ" delta={s.energyMj.deltaPct} accent={categoryColor("energy")} />
        <StatCard label="Water" value={s.waterLitre.value} unit="L" delta={s.waterLitre.deltaPct} accent={categoryColor("water")} />
        <StatCard label="Waste landfilled" value={s.wasteKg.value} unit="kg" delta={s.wasteKg.deltaPct} accent={categoryColor("waste")}
          hint={`${Math.round((d.summary.recycledFraction ?? 0) * 100)}% diverted`} />
      </div>

      {/* Charts */}
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHead title="CO₂e trend" sub="Monthly, kilograms" />
          <CardBody><TrendChart data={d.charts.co2eTrend} color={categoryColor("carbon")} unit="kgCO₂e" /></CardBody>
        </Card>
        <Card>
          <CardHead title="Environmental score over time" />
          <CardBody><TrendChart data={d.charts.scoreTrend} color="#2D6A4F" area={false} unit="score" /></CardBody>
        </Card>
        <Card>
          <CardHead title="Emissions by category" sub={`${monthLabel(d.period)}`} />
          <CardBody>
            <CategoryBars data={d.charts.emissionsByCategory.map((c) => ({ key: c.category, label: cap(c.category), value: c.kg }))} />
          </CardBody>
        </Card>
        <Card>
          <CardHead title="Waste composition" sub="By disposal route, kg" />
          <CardBody>
            {d.charts.wasteComposition.length
              ? <Donut data={d.charts.wasteComposition.map((w) => ({ key: w.method, label: w.method, value: w.kg }))} />
              : <p className="py-8 text-center text-[12px] text-content-dim">No waste recorded this month.</p>}
          </CardBody>
        </Card>
      </div>

      {/* Contributors + anomalies */}
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHead title="Top contributors" sub="Largest sources of CO₂e this month" />
          <CardBody className="space-y-2.5">
            {d.topContributors.map((c) => (
              <div key={c.activityCode} className="flex items-center gap-3">
                <span className="h-2 w-2 shrink-0 rounded-sm" style={{ background: categoryColor(c.category) }} />
                <span className="flex-1 truncate text-[12.5px] text-content">{c.name}</span>
                <span className="readout text-[12px]">{num(c.co2eKg)} kg</span>
                <span className="w-12 text-right font-mono text-[11px] text-content-dim">{num(c.pct)}%</span>
              </div>
            ))}
          </CardBody>
        </Card>
        <Card accent={d.openAnomalies.length ? "#B04A34" : undefined}>
          <CardHead title="Open anomalies" sub="Unusual spikes or drops to investigate"
            action={<Link to="/anomalies" className="btn btn-sm btn-ghost">All</Link>} />
          <CardBody className="space-y-2.5">
            {d.openAnomalies.length === 0 && <p className="py-6 text-center text-[12px] text-content-dim">Nothing unusual detected.</p>}
            {d.openAnomalies.map((a) => (
              <Link key={a.id} to="/anomalies" className="flex items-center gap-3 rounded p-1.5 hover:bg-surface-3">
                <Badge tone={SEV_TONE[a.severity]}>{a.severity}</Badge>
                <span className="flex-1 text-[12.5px] text-content">
                  {cap(a.metric)} {a.direction === "SPIKE" ? "up" : "down"} {num(Math.abs(a.deviationPct))}% · {monthLabel(a.period)}
                </span>
                {a.estimatedCo2eImpactKg != null && (
                  <span className="readout text-[11px] text-content-dim">≈{num(a.estimatedCo2eImpactKg)} kg</span>
                )}
              </Link>
            ))}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}

function cap(s: string) {
  return s ? s.charAt(0) + s.slice(1).toLowerCase() : s;
}
