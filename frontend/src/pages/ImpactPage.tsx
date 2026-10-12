import { useQuery } from "@tanstack/react-query";
import { impactApi, scoresApi } from "@/api";
import { PageHeader, LoadingBlock, ErrorState, EmptyState } from "@/components/ui/states";
import { Card, CardBody, CardHead } from "@/components/ui/Card";
import { StatCard } from "@/components/ui/StatCard";
import { TrendChart, CategoryBars } from "@/components/charts/Charts";
import { Table, Th, Td, Tr } from "@/components/ui/Table";
import { Badge } from "@/components/ui/primitives";
import { num, pct, categoryColor, monthLabel } from "@/lib/format";

export default function ImpactPage() {
  const impact = useQuery({ queryKey: ["impact"], queryFn: () => impactApi.impact() });
  const trends = useQuery({ queryKey: ["impact-trends"], queryFn: () => impactApi.trends(undefined, undefined, "CO2E,ENERGY,WATER,WASTE,SCORE") });
  const explain = useQuery({ queryKey: ["impact-explain"], queryFn: () => impactApi.explain(), retry: false });
  const methodology = useQuery({ queryKey: ["score-methodology"], queryFn: () => scoresApi.methodology() });

  if (impact.isLoading) return <LoadingBlock label="Aggregating impact" />;
  if (impact.error) {
    return (impact.error as any)?.code === "NOT_FOUND"
      ? <EmptyState title="Nothing to analyse yet" body="Add environmental records to see breakdowns, trends and explanations." />
      : <ErrorState error={impact.error} onRetry={impact.refetch} />;
  }

  const d = impact.data as any;
  const t = d.totals;
  const series = (trends.data?.series ?? {}) as Record<string, { period: string; value: number }[]>;

  return (
    <div>
      <PageHeader title="Impact Analysis" sub={`${monthLabel(d.from)} – ${monthLabel(d.to)} · ${d.months} months · ${t.recordCount} records`} />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="CO₂e (period)" value={t.co2eKg} unit="kg" accent={categoryColor("carbon")} />
        <StatCard label="Energy" value={t.energyMj} unit="MJ" accent={categoryColor("energy")} />
        <StatCard label="Water" value={t.waterLitre} unit="L" accent={categoryColor("water")} />
        <StatCard label="Waste landfilled" value={t.wasteKg} unit="kg" accent={categoryColor("waste")}
          hint={`${Math.round((d.recycledFraction ?? 0) * 100)}% diverted`} />
      </div>

      {explain.data && !explain.isError && (
        <Card className="mt-4" accent="#2D6A4F">
          <CardHead title="What changed" sub={`${monthLabel(explain.data.period)} vs ${monthLabel(explain.data.comparedTo)}`} />
          <CardBody>
            <p className="whitespace-pre-line text-[13px] leading-relaxed text-content-muted">{explain.data.narrative}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {(explain.data.drivers ?? []).slice(0, 5).map((dr: any) => (
                <Badge key={dr.category} tone={dr.direction === "IMPROVED" ? "positive" : "negative"}>
                  {dr.category} {pct(dr.changePct)} · {dr.pointsImpact > 0 ? "+" : ""}{dr.pointsImpact} pts
                </Badge>
              ))}
            </div>
          </CardBody>
        </Card>
      )}

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        {(["CO2E", "ENERGY", "WATER", "WASTE"] as const).map((m) => (
          <Card key={m}>
            <CardHead title={`${label(m)} trend`} />
            <CardBody><TrendChart data={series[m] ?? []} color={categoryColor(m === "CO2E" ? "carbon" : m)} unit={label(m)} /></CardBody>
          </Card>
        ))}
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHead title="Emissions by category" sub="Full period, kgCO₂e" />
          <CardBody>
            <CategoryBars data={(d.emissionsByCategory ?? []).map((c: any) => ({ key: c.category, label: cap(c.category), value: c.kg }))} />
          </CardBody>
        </Card>
        <Card>
          <CardHead title="Emissions by activity" sub="Top sources" />
          <CardBody>
            <Table className="min-w-0">
              <thead><tr><Th>Activity</Th><Th align="right">kgCO₂e</Th><Th align="right">Share</Th></tr></thead>
              <tbody>
                {(d.emissionsByActivity ?? []).slice(0, 10).map((a: any) => (
                  <Tr key={a.activityCode}>
                    <Td>{a.name}</Td>
                    <Td align="right" mono>{num(a.kg)}</Td>
                    <Td align="right" mono>{num(a.pct)}%</Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          </CardBody>
        </Card>
      </div>

      {methodology.data && (
        <Card className="mt-4">
          <CardHead title="Scoring methodology" sub={`Version ${methodology.data.version} — transparent and hand-recomputable`} />
          <CardBody className="space-y-2 text-[12.5px] text-content-muted">
            <p className="font-mono text-[11.5px] text-content">{methodology.data.formula}</p>
            <div className="flex flex-wrap gap-2 pt-1">
              {Object.entries(methodology.data.weights ?? {}).map(([k, v]) => (
                <Badge key={k}>{k}: {String(v)}</Badge>
              ))}
            </div>
          </CardBody>
        </Card>
      )}
    </div>
  );
}

function label(m: string) { return m === "CO2E" ? "CO₂e" : cap(m); }
function cap(s: string) { return s ? s.charAt(0) + s.slice(1).toLowerCase() : s; }
