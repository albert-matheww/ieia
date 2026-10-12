import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { reportsApi } from "@/api";
import { tokenStore } from "@/api/client";
import type { ReportSummary } from "@/api/types";
import { PageHeader, LoadingBlock, ErrorState, EmptyState } from "@/components/ui/states";
import { Card, CardBody, CardHead } from "@/components/ui/Card";
import { Table, Th, Td, Tr } from "@/components/ui/Table";
import { Button, Field, Select, Badge } from "@/components/ui/primitives";
import { useToast } from "@/components/ui/Toast";
import { bytes, monthLabel, relTime } from "@/lib/format";

const monthsAgo = (n: number) => { const d = new Date(); d.setMonth(d.getMonth() - n); return d.toISOString().slice(0, 7); };
const STATUS_TONE = { READY: "positive", FAILED: "negative", GENERATING: "signal", PENDING: "neutral" } as const;

export default function ReportsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const [form, setForm] = useState({ title: "", from: monthsAgo(6), to: monthsAgo(0), format: "PDF" });

  const list = useQuery({
    queryKey: ["reports"],
    queryFn: () => reportsApi.list(),
    refetchInterval: (q) => (q.state.data?.content.some((r) => r.status === "GENERATING" || r.status === "PENDING") ? 1500 : false),
  });

  const create = useMutation({
    mutationFn: () => reportsApi.create({ title: form.title || undefined, from: form.from, to: form.to, format: form.format }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["reports"] }); toast.push("Report queued", "success"); },
    onError: (e: any) => toast.push(e?.message ?? "Couldn't create the report", "error"),
  });
  const remove = useMutation({
    mutationFn: (id: string) => reportsApi.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["reports"] }),
  });

  async function download(r: ReportSummary) {
    try {
      const res = await fetch(reportsApi.downloadUrl(r.id), { headers: { Authorization: `Bearer ${tokenStore.access}` } });
      if (!res.ok) throw new Error();
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${r.title.replace(/[^a-z0-9]+/gi, "_")}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.push("Download failed", "error");
    }
  }

  return (
    <div>
      <PageHeader title="Reports" sub="A full environmental report — scores, trends, contributors, anomalies, forecasts, recommendations, scenarios and methodology." />

      <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
        <Card>
          <CardHead title="New report" />
          <CardBody>
            <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); create.mutate(); }}>
              <Field label="Title (optional)" value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
              <div className="grid grid-cols-2 gap-3">
                <Field label="From" type="month" value={form.from} onChange={(e) => setForm((f) => ({ ...f, from: e.target.value }))} />
                <Field label="To" type="month" value={form.to} onChange={(e) => setForm((f) => ({ ...f, to: e.target.value }))} />
              </div>
              <Select label="Format" value={form.format} onChange={(e) => setForm((f) => ({ ...f, format: e.target.value }))}>
                <option value="PDF">PDF (downloadable)</option>
                <option value="JSON">JSON (structured data)</option>
              </Select>
              <Button type="submit" className="w-full" loading={create.isPending}>Generate report</Button>
            </form>
          </CardBody>
        </Card>

        <Card>
          <CardHead title="Your reports" />
          <CardBody className="p-0">
            {list.isLoading ? <LoadingBlock label="Loading reports" />
              : list.error ? <ErrorState error={list.error} onRetry={list.refetch} />
              : (list.data?.content.length ?? 0) === 0 ? <div className="p-6"><EmptyState title="No reports yet" body="Generate one from the panel on the left." /></div>
              : (
                <Table>
                  <thead><tr><Th>Report</Th><Th>Period</Th><Th>Format</Th><Th>Status</Th><Th></Th></tr></thead>
                  <tbody>
                    {list.data!.content.map((r) => (
                      <Tr key={r.id}>
                        <Td><span className="text-content">{r.title}</span><br /><span className="text-[10.5px] text-content-dim">{relTime(r.createdAt)}</span></Td>
                        <Td mono>{monthLabel(r.periodFrom)} – {monthLabel(r.periodTo)}</Td>
                        <Td><Badge>{r.format}</Badge></Td>
                        <Td><Badge tone={STATUS_TONE[r.status]}>{r.status}</Badge>{r.fileSizeBytes ? <span className="ml-2 font-mono text-[10px] text-content-dim">{bytes(r.fileSizeBytes)}</span> : null}</Td>
                        <Td align="right">
                          <div className="flex justify-end gap-1">
                            {r.format === "PDF" && r.status === "READY" && <button className="btn btn-sm btn-ghost" onClick={() => download(r)}>Download</button>}
                            <button className="btn btn-sm btn-danger" onClick={() => remove.mutate(r.id)}>Delete</button>
                          </div>
                        </Td>
                      </Tr>
                    ))}
                  </tbody>
                </Table>
              )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
