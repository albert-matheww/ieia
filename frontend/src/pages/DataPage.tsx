import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { activityApi, recordsApi } from "@/api";
import type { ActivityType, EnvRecord } from "@/api/types";
import { PageHeader, LoadingBlock, ErrorState, EmptyState } from "@/components/ui/states";
import { Card } from "@/components/ui/Card";
import { Table, Th, Td, Tr } from "@/components/ui/Table";
import { Button, Field, Select, Badge } from "@/components/ui/primitives";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { monthLabel, num, categoryColor } from "@/lib/format";
import { ApiError } from "@/api/client";

const CATEGORIES = ["ELECTRICITY", "TRANSPORT", "FUEL", "WATER", "WASTE", "MATERIALS"];
const thisMonth = () => new Date().toISOString().slice(0, 7);

export default function DataPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const [filter, setFilter] = useState<string>("");
  const [modal, setModal] = useState<{ mode: "create" | "edit"; record?: EnvRecord } | null>(null);

  const activities = useQuery({ queryKey: ["activity-types"], queryFn: () => activityApi.list() });
  const records = useQuery({
    queryKey: ["records", filter],
    queryFn: () => recordsApi.list({ category: filter || undefined, size: 100, sort: "periodMonth,desc" }),
  });

  const remove = useMutation({
    mutationFn: (id: string) => recordsApi.remove(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["records"] }); qc.invalidateQueries({ queryKey: ["dashboard"] }); toast.push("Record deleted", "success"); },
    onError: (e) => toast.push(e instanceof ApiError ? e.message : "Delete failed", "error"),
  });

  return (
    <div>
      <PageHeader
        title="Environmental Data"
        sub="Every record is converted to impact using a sourced, region-aware emission factor."
        actions={
          <>
            <Select aria-label="Filter by category" value={filter} onChange={(e) => setFilter(e.target.value)} className="w-44">
              <option value="">All categories</option>
              {CATEGORIES.map((c) => <option key={c} value={c}>{cap(c)}</option>)}
            </Select>
            <Button onClick={() => setModal({ mode: "create" })}>Add record</Button>
          </>
        }
      />

      {records.isLoading || activities.isLoading ? (
        <LoadingBlock label="Loading records" />
      ) : records.error ? (
        <ErrorState error={records.error} onRetry={records.refetch} />
      ) : (records.data?.content.length ?? 0) === 0 ? (
        <EmptyState title="No records yet" body="Add your first activity — a monthly electricity bill is a good start."
          action={<Button onClick={() => setModal({ mode: "create" })}>Add record</Button>} />
      ) : (
        <Card>
          <Table>
            <thead>
              <tr>
                <Th>Period</Th><Th>Activity</Th><Th align="right">Amount</Th>
                <Th align="right">CO₂e (kg)</Th><Th align="right">Energy (MJ)</Th><Th></Th>
              </tr>
            </thead>
            <tbody>
              {records.data!.content.map((r) => {
                const co2e = r.impacts.find((i) => i.impactType === "CO2E")?.value;
                const energy = r.impacts.find((i) => i.impactType === "ENERGY")?.value;
                return (
                  <Tr key={r.id}>
                    <Td mono>{monthLabel(r.period)}</Td>
                    <Td>
                      <span className="inline-flex items-center gap-2">
                        <span className="h-2 w-2 rounded-sm" style={{ background: categoryColor(r.activityType.category) }} />
                        {r.activityType.name}
                      </span>
                      {r.warnings.length > 0 && <Badge tone="signal" className="ml-2">no factor</Badge>}
                    </Td>
                    <Td align="right" mono>{num(r.amount)} {r.unit}</Td>
                    <Td align="right" mono>{co2e != null ? num(co2e) : "—"}</Td>
                    <Td align="right" mono>{energy != null ? num(energy) : "—"}</Td>
                    <Td align="right">
                      <div className="flex justify-end gap-1">
                        <button className="btn btn-sm btn-ghost" onClick={() => setModal({ mode: "edit", record: r })}>Edit</button>
                        <button className="btn btn-sm btn-danger" onClick={() => { if (confirm("Delete this record?")) remove.mutate(r.id); }}>Delete</button>
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        </Card>
      )}

      {modal && (
        <RecordModal
          mode={modal.mode}
          record={modal.record}
          activities={activities.data ?? []}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null);
            qc.invalidateQueries({ queryKey: ["records"] });
            qc.invalidateQueries({ queryKey: ["dashboard"] });
            toast.push(modal.mode === "create" ? "Record added" : "Record updated", "success");
          }}
        />
      )}
    </div>
  );
}

function RecordModal({
  mode, record, activities, onClose, onSaved,
}: { mode: "create" | "edit"; record?: EnvRecord; activities: ActivityType[]; onClose: () => void; onSaved: () => void }) {
  const [activityTypeId, setActivityTypeId] = useState(record?.activityType.id ?? activities[0]?.id ?? "");
  const activity = useMemo(() => activities.find((a) => a.id === activityTypeId), [activities, activityTypeId]);
  const [period, setPeriod] = useState(record?.period ?? thisMonth());
  const [amount, setAmount] = useState(String(record?.amount ?? ""));
  const [unit, setUnit] = useState(record?.unit ?? activity?.defaultUnit ?? "");
  const [note, setNote] = useState(record?.note ?? "");
  const [attributes, setAttributes] = useState<Record<string, string>>(
    Object.fromEntries(Object.entries(record?.attributes ?? {}).map(([k, v]) => [k, String(v)])),
  );
  const [error, setError] = useState<string | null>(null);

  const schema = (activity?.attributeSchema ?? {}) as Record<string, any>;
  const attrKeys = Object.keys(schema).filter((k) => !schema[k]?.fixed);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        activityTypeId, period, amount: Number(amount), unit,
        attributes: attrKeys.length ? attributes : undefined,
        note: note || undefined,
      };
      return mode === "create" ? recordsApi.create(body) : recordsApi.update(record!.id, body);
    },
    onSuccess: onSaved,
    onError: (e) => setError(e instanceof ApiError ? e.message : "Couldn't save the record."),
  });

  return (
    <Modal open onClose={onClose} title={mode === "create" ? "Add environmental record" : "Edit record"}>
      <form className="space-y-3.5" onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate(); }}>
        <Select label="Activity" value={activityTypeId} onChange={(e) => {
          setActivityTypeId(e.target.value);
          const a = activities.find((x) => x.id === e.target.value);
          if (a) setUnit(a.defaultUnit);
        }}>
          {CATEGORIES.map((c) => (
            <optgroup key={c} label={cap(c)}>
              {activities.filter((a) => a.category === c).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </optgroup>
          ))}
        </Select>
        <div className="grid grid-cols-[1fr_1fr_0.9fr] gap-3">
          <Field label="Month" type="month" value={period} max={thisMonth()} onChange={(e) => setPeriod(e.target.value)} required />
          <Field label="Amount" type="number" step="any" min="0" value={amount} onChange={(e) => setAmount(e.target.value)} required />
          <Select label="Unit" value={unit} onChange={(e) => setUnit(e.target.value)}>
            {(activity?.allowedUnits ?? [unit]).map((u) => <option key={u} value={u}>{u}</option>)}
          </Select>
        </div>
        {attrKeys.map((k) => {
          const spec = schema[k] ?? {};
          const opts: string[] | undefined = spec.options;
          return opts?.length ? (
            <Select key={k} label={spec.label ?? cap(k)} value={attributes[k] ?? ""} onChange={(e) => setAttributes((a) => ({ ...a, [k]: e.target.value }))}>
              <option value="">—</option>
              {opts.map((o) => <option key={o} value={o}>{o}</option>)}
            </Select>
          ) : (
            <Field key={k} label={spec.label ?? cap(k)} value={attributes[k] ?? ""} onChange={(e) => setAttributes((a) => ({ ...a, [k]: e.target.value }))} />
          );
        })}
        <Field label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} />
        {error && <p className="rounded border border-negative/40 bg-negative/10 px-3 py-2 text-[12px] text-negative">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="ghost" onClick={onClose}>Cancel</Button>
          <Button type="submit" loading={save.isPending}>{mode === "create" ? "Add record" : "Save changes"}</Button>
        </div>
      </form>
    </Modal>
  );
}

function cap(s: string) { return s ? s.charAt(0) + s.slice(1).toLowerCase() : s; }
