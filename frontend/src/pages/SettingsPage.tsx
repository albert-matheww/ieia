import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { benchmarksApi, profileApi, scoresApi } from "@/api";
import { useAuth } from "@/context/AuthContext";
import { PageHeader } from "@/components/ui/states";
import { Card, CardBody, CardHead } from "@/components/ui/Card";
import { Button, Field, Select, Badge } from "@/components/ui/primitives";
import { useToast } from "@/components/ui/Toast";
import { num, RATING_TONE, monthLabel } from "@/lib/format";
import { cn } from "@/lib/cn";

export default function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const toast = useToast();

  const [profile, setProfile] = useState({ fullName: user?.fullName ?? "", region: user?.organization?.region ?? "GLOBAL" });
  const [pw, setPw] = useState({ current: "", next: "" });

  const saveProfile = useMutation({
    mutationFn: () => profileApi.update({ fullName: profile.fullName, region: profile.region }),
    onSuccess: async () => { await refreshUser(); toast.push("Profile updated", "success"); },
    onError: (e: any) => toast.push(e?.message ?? "Couldn't save", "error"),
  });
  const changePw = useMutation({
    mutationFn: () => profileApi.changePassword(pw.current, pw.next),
    onSuccess: () => { setPw({ current: "", next: "" }); toast.push("Password changed — sign in again next time", "success"); },
    onError: (e: any) => toast.push(e?.message ?? "Couldn't change password", "error"),
  });

  const methodology = useQuery({ queryKey: ["score-methodology"], queryFn: () => scoresApi.methodology() });
  const benchmark = useQuery({ queryKey: ["benchmark-comparison"], queryFn: () => benchmarksApi.comparison(), retry: false });

  return (
    <div>
      <PageHeader title="Settings" sub="Profile, security, and how your score is measured." />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHead title="Profile" />
          <CardBody>
            <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); saveProfile.mutate(); }}>
              <Field label="Full name" value={profile.fullName} onChange={(e) => setProfile((p) => ({ ...p, fullName: e.target.value }))} />
              <Field label="Email" value={user?.email ?? ""} disabled />
              <Select label="Region (drives factors & baselines)" value={profile.region} onChange={(e) => setProfile((p) => ({ ...p, region: e.target.value }))}>
                <option value="GLOBAL">Global</option>
                <option value="IN">India</option>
              </Select>
              <Button type="submit" loading={saveProfile.isPending}>Save profile</Button>
            </form>
          </CardBody>
        </Card>

        <Card>
          <CardHead title="Change password" />
          <CardBody>
            <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); changePw.mutate(); }}>
              <Field label="Current password" type="password" value={pw.current} onChange={(e) => setPw((p) => ({ ...p, current: e.target.value }))} required />
              <Field label="New password" type="password" hint="At least 10 characters, a letter and a digit." value={pw.next} onChange={(e) => setPw((p) => ({ ...p, next: e.target.value }))} required />
              <Button type="submit" variant="ghost" loading={changePw.isPending}>Update password</Button>
            </form>
          </CardBody>
        </Card>

        {user?.organization && (
          <Card>
            <CardHead title="Organisation" />
            <CardBody className="space-y-2 text-[12.5px] text-content-muted">
              <Row k="Name" v={user.organization.name} />
              <Row k="Size" v={user.organization.sizeCategory} />
              <Row k="Region" v={user.organization.region} />
              <Row k="Headcount" v={user.organization.headcount ?? "—"} />
            </CardBody>
          </Card>
        )}

        {benchmark.data && !benchmark.isError && (
          <Card>
            <CardHead title="Benchmarking" sub={`${monthLabel(benchmark.data.period)} · ${benchmark.data.segment}`} />
            <CardBody className="space-y-2">
              {benchmark.data.categories.map((c) => (
                <div key={c.category} className="flex items-center gap-3 text-[12.5px]">
                  <span className="w-16 text-content">{cap(c.category)}</span>
                  <span className="readout text-[11px] text-content-dim">{num(c.value)} vs {c.benchmarkValue != null ? num(c.benchmarkValue) : "—"}</span>
                  {c.rating && <span className={cn("chip ml-auto", RATING_TONE[c.rating] ?? "border-line")}>{c.rating.replace(/_/g, " ")}</span>}
                </div>
              ))}
              <p className="border-t border-line pt-2 text-[10.5px] text-content-dim">{benchmark.data.disclaimer}</p>
            </CardBody>
          </Card>
        )}

        {methodology.data && (
          <Card className="lg:col-span-2">
            <CardHead title="Scoring methodology" sub={`Version ${methodology.data.version}`} />
            <CardBody className="space-y-2 text-[12.5px] text-content-muted">
              <p className="font-mono text-[11.5px] text-content">{methodology.data.formula}</p>
              <div className="flex flex-wrap gap-2">
                {Object.entries(methodology.data.weights ?? {}).map(([k, v]) => <Badge key={k}>{k}: {String(v)}</Badge>)}
              </div>
              <p>{methodology.data.description}</p>
            </CardBody>
          </Card>
        )}
      </div>
    </div>
  );
}

function Row({ k, v }: { k: string; v: React.ReactNode }) {
  return <div className="flex justify-between border-b border-line py-1.5 last:border-0"><span className="label-inst">{k}</span><span className="text-content">{v}</span></div>;
}
function cap(s: string) { return s ? s.charAt(0) + s.slice(1).toLowerCase() : s; }
