import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { anomaliesApi } from "@/api";
import type { AnomalyItem } from "@/api/types";
import { PageHeader, LoadingBlock, ErrorState, EmptyState } from "@/components/ui/states";
import { Card, CardBody, CardHead } from "@/components/ui/Card";
import { Button, Badge, Select } from "@/components/ui/primitives";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { AnomalyTrend } from "@/components/charts/Charts";
import { num, monthLabel, relTime } from "@/lib/format";

const SEV_TONE = { HIGH: "negative", MEDIUM: "signal", LOW: "neutral" } as const;

export default function AnomaliesPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const [status, setStatus] = useState("OPEN");
  const [selected, setSelected] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ["anomalies", status],
    queryFn: () => anomaliesApi.list({ status: status || undefined, size: 100 }),
  });
  const scan = useMutation({
    mutationFn: () => anomaliesApi.scan({ sensitivity: "MEDIUM" }),
    onSuccess: (r) => {
      qc.invalidateQueries({ queryKey: ["anomalies"] });
      qc.invalidateQueries({ queryKey: ["dashboard"] });
      toast.push(`Scan complete — ${r.detected} flagged, ${r.newlyOpened} new`, "success");
    },
    onError: (e: any) => toast.push(e?.code === "ML_SERVICE_UNAVAILABLE" ? "ML service is unavailable" : "Scan failed", "error"),
  });

  return (
    <div>
      <PageHeader
        title="Anomaly Detection"
        sub="An ensemble (robust z-score · IQR · seasonal residual · isolation forest) flags unusual months."
        actions={
          <>
            <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)} className="w-40">
              <option value="OPEN">Open</option>
              <option value="ACKNOWLEDGED">Acknowledged</option>
              <option value="RESOLVED">Resolved</option>
              <option value="DISMISSED">Dismissed</option>
              <option value="">All</option>
            </Select>
            <Button loading={scan.isPending} onClick={() => scan.mutate()}>Run scan</Button>
          </>
        }
      />

      {list.isLoading ? (
        <LoadingBlock label="Loading anomalies" />
      ) : list.error ? (
        <ErrorState error={list.error} onRetry={list.refetch} />
      ) : (list.data?.content.length ?? 0) === 0 ? (
        <EmptyState title={status === "OPEN" ? "No open anomalies" : "Nothing here"} body="Run a scan after adding a few months of data to check for unusual spikes or drops." />
      ) : (
        <div className="space-y-2.5">
          {list.data!.content.map((a) => (
            <button key={a.id} onClick={() => setSelected(a.id)} className="w-full text-left">
              <Card accent={a.severity === "HIGH" ? "#B04A34" : a.severity === "MEDIUM" ? "#E9C46A" : undefined} className="transition-colors hover:border-brand/60">
                <div className="flex items-center gap-4 p-4">
                  <Badge tone={SEV_TONE[a.severity]}>{a.severity}</Badge>
                  <div className="min-w-0 flex-1">
                    <p className="text-[13px] font-medium text-content">
                      {cap(a.metric)} {a.direction === "SPIKE" ? "spiked" : "dropped"} {num(Math.abs(a.deviationPct))}% · {monthLabel(a.period)}
                    </p>
                    <p className="truncate text-[11.5px] text-content-dim">{a.explanation}</p>
                  </div>
                  <div className="hidden text-right sm:block">
                    <p className="readout text-[13px]">{num(a.observedValue)}</p>
                    <p className="font-mono text-[10px] text-content-dim">vs {num(a.baselineValue)} baseline</p>
                  </div>
                  {a.estimatedCo2eImpactKg != null && (
                    <div className="hidden text-right md:block">
                      <p className="readout text-[13px] text-signal">≈{num(a.estimatedCo2eImpactKg)}</p>
                      <p className="font-mono text-[10px] text-content-dim">kgCO₂e impact</p>
                    </div>
                  )}
                </div>
              </Card>
            </button>
          ))}
        </div>
      )}

      {selected && <AnomalyDrawer id={selected} onClose={() => setSelected(null)} onReviewed={() => { setSelected(null); qc.invalidateQueries({ queryKey: ["anomalies"] }); qc.invalidateQueries({ queryKey: ["dashboard"] }); }} />}
    </div>
  );
}

function AnomalyDrawer({ id, onClose, onReviewed }: { id: string; onClose: () => void; onReviewed: () => void }) {
  const toast = useToast();
  const q = useQuery({ queryKey: ["anomaly", id], queryFn: () => anomaliesApi.get(id) });
  const review = useMutation({
    mutationFn: (s: string) => anomaliesApi.review(id, s),
    onSuccess: () => { toast.push("Anomaly updated", "success"); onReviewed(); },
    onError: () => toast.push("Couldn't update", "error"),
  });
  const a = q.data as AnomalyItem | undefined;

  return (
    <Modal open onClose={onClose} title="Investigate anomaly" wide>
      {q.isLoading || !a ? <LoadingBlock /> : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={SEV_TONE[a.severity]}>{a.severity}</Badge>
            <Badge>{a.direction}</Badge>
            <Badge>{a.method}</Badge>
            <Badge>{monthLabel(a.period)}</Badge>
            <span className="ml-auto text-[11px] text-content-dim">detected {relTime(a.detectedAt)}</span>
          </div>
          <p className="whitespace-pre-line text-[13px] text-content-muted">{a.explanation}</p>
          {a.context?.series?.length ? (
            <Card><CardHead title={`${cap(a.metric)} context`} sub="6 months around the anomaly" /><CardBody>
              <AnomalyTrend data={a.context.series} anomalyPeriod={a.period} />
            </CardBody></Card>
          ) : null}
          <div className="grid grid-cols-3 gap-3 text-center">
            {[["Observed", a.observedValue], ["Baseline", a.baselineValue], ["Deviation", a.deviationPct]].map(([l, v], i) => (
              <div key={l as string} className="rounded border border-line bg-surface p-2.5">
                <p className="label-inst">{l as string}</p>
                <p className="readout mt-1 text-[15px]">{num(v as number)}{i === 2 ? "%" : ""}</p>
              </div>
            ))}
          </div>
          <div className="flex flex-wrap justify-end gap-2 border-t border-line pt-3">
            <Button variant="ghost" size="sm" loading={review.isPending} onClick={() => review.mutate("ACKNOWLEDGED")}>Acknowledge</Button>
            <Button variant="ghost" size="sm" onClick={() => review.mutate("DISMISSED")}>Dismiss</Button>
            <Button size="sm" onClick={() => review.mutate("RESOLVED")}>Mark resolved</Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

function cap(s: string) { return s ? s.charAt(0) + s.slice(1).toLowerCase() : s; }
