import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { recommendationsApi } from "@/api";
import type { Recommendation } from "@/api/types";
import { PageHeader, LoadingBlock, ErrorState, EmptyState } from "@/components/ui/states";
import { Card, CardBody } from "@/components/ui/Card";
import { Button, Badge, Select } from "@/components/ui/primitives";
import { useToast } from "@/components/ui/Toast";
import { num, categoryColor } from "@/lib/format";

const CONF_TONE = { HIGH: "positive", MEDIUM: "neutral", LOW: "signal" } as const;
const STATUSES = ["SUGGESTED", "ACCEPTED", "IN_PROGRESS", "DONE", "DISMISSED"];

export default function RecommendationsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const q = useQuery({ queryKey: ["recommendations"], queryFn: () => recommendationsApi.get(), retry: false });

  const regen = useMutation({
    mutationFn: (useLlm: boolean) => recommendationsApi.generate(useLlm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["recommendations"] }); toast.push("Recommendations refreshed", "success"); },
    onError: () => toast.push("Couldn't regenerate", "error"),
  });
  const setStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => recommendationsApi.updateStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["recommendations"] }),
  });

  if (q.isLoading) return <LoadingBlock label="Ranking your contributors" />;
  if (q.error) {
    return (q.error as any)?.code === "NOT_FOUND"
      ? <EmptyState title="No recommendations yet" body="Add a month or two of data and IEIA will rank your contributors and price each action." />
      : <ErrorState error={q.error} onRetry={q.refetch} />;
  }

  const d = q.data as Recommendation;

  return (
    <div>
      <PageHeader
        title="Recommendations"
        sub="Ranked by estimated CO₂e reduction. Every figure is computed with the same deterministic scenario engine as the simulator."
        actions={
          <>
            <Button variant="ghost" loading={regen.isPending} onClick={() => regen.mutate(false)}>Regenerate</Button>
            <Button loading={regen.isPending} onClick={() => regen.mutate(true)}>Regenerate with AI phrasing</Button>
          </>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2 text-[12px] text-content-muted">
        <Badge tone={d.basis === "DATA_DRIVEN" ? "positive" : "signal"}>{d.basis === "DATA_DRIVEN" ? "Data-driven" : "Generic (thin data)"}</Badge>
        <Badge>{d.source === "LLM_ASSISTED" ? "AI-phrased" : "Rule engine"}</Badge>
        {d.topContributor && (
          <span>Largest contributor: <strong className="text-content">{cap(d.topContributor.category)}</strong> ({num(d.topContributor.pctOfTotal)}% of CO₂e)</span>
        )}
      </div>

      <div className="space-y-3">
        {d.items.map((it) => (
          <Card key={it.id} accent={categoryColor(it.category)}>
            <CardBody className="flex flex-col gap-3 sm:flex-row sm:items-start">
              <div className="grid h-9 w-9 shrink-0 place-items-center rounded bg-surface-3 font-mono text-[14px] font-semibold text-content">{it.rank}</div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="font-display text-[14px] font-semibold text-content">{it.title}</h3>
                  <Badge tone={CONF_TONE[it.confidence]}>{it.confidence}</Badge>
                </div>
                <p className="mt-1.5 text-[12.5px] leading-relaxed text-content-muted">{it.rationale}</p>
                <div className="mt-2 flex items-center gap-2">
                  <Select aria-label="Status" className="h-7 w-36 text-[11px]" value={it.status}
                    onChange={(e) => setStatus.mutate({ id: it.id, status: e.target.value })}>
                    {STATUSES.map((s) => <option key={s} value={s}>{s.replace(/_/g, " ")}</option>)}
                  </Select>
                </div>
              </div>
              <div className="shrink-0 text-right">
                <p className="readout text-[20px] font-medium text-positive">−{num(it.estimatedReductionCo2eKg)}</p>
                <p className="label-inst">kgCO₂e / month</p>
                <p className="mt-0.5 font-mono text-[10.5px] text-content-dim">≈{num(it.estimatedReductionPct)}% of total</p>
              </div>
            </CardBody>
          </Card>
        ))}
      </div>
    </div>
  );
}

function cap(s: string) { return s ? s.charAt(0) + s.slice(1).toLowerCase() : s; }
