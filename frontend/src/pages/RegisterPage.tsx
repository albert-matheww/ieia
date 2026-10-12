import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { Button, Field, Select } from "@/components/ui/primitives";
import { ApiError } from "@/api/client";
import { AuthShell } from "./AuthShell";

export default function RegisterPage() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({
    fullName: "", email: "", password: "", accountType: "INDIVIDUAL",
    organizationName: "", region: "GLOBAL", headcount: "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setErrors({});
    setBusy(true);
    try {
      await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        accountType: form.accountType,
        region: form.region,
        ...(form.accountType === "ORGANIZATION"
          ? { organizationName: form.organizationName.trim(), headcount: form.headcount ? Number(form.headcount) : undefined }
          : {}),
      });
      nav("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length) {
        setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])));
      } else {
        setErrors({ _: err instanceof ApiError ? err.message : "Couldn't create the account." });
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Create your account" sub="Track an individual footprint or an organisation's.">
      <form onSubmit={onSubmit} className="space-y-3.5">
        <Field id="fullName" label="Full name" required value={form.fullName} onChange={(e) => set("fullName", e.target.value)} error={errors.fullName} />
        <Field id="email" label="Email" type="email" autoComplete="email" required value={form.email} onChange={(e) => set("email", e.target.value)} error={errors.email} />
        <Field id="password" label="Password" type="password" autoComplete="new-password" required
          hint="At least 10 characters, with a letter and a digit."
          value={form.password} onChange={(e) => set("password", e.target.value)} error={errors.password} />
        <div className="grid grid-cols-2 gap-3">
          <Select id="accountType" label="Account type" value={form.accountType} onChange={(e) => set("accountType", e.target.value)}>
            <option value="INDIVIDUAL">Individual</option>
            <option value="ORGANIZATION">Organisation</option>
          </Select>
          <Select id="region" label="Region" value={form.region} onChange={(e) => set("region", e.target.value)}>
            <option value="GLOBAL">Global</option>
            <option value="IN">India</option>
          </Select>
        </div>
        {form.accountType === "ORGANIZATION" && (
          <div className="grid grid-cols-[2fr_1fr] gap-3">
            <Field id="orgName" label="Organisation name" required value={form.organizationName} onChange={(e) => set("organizationName", e.target.value)} error={errors.organizationName} />
            <Field id="headcount" label="Headcount" type="number" min={1} value={form.headcount} onChange={(e) => set("headcount", e.target.value)} />
          </div>
        )}
        {errors._ && <p className="rounded border border-negative/40 bg-negative/10 px-3 py-2 text-[12px] text-negative">{errors._}</p>}
        <Button type="submit" loading={busy} className="w-full">Create account</Button>
      </form>
      <p className="mt-5 text-center text-[12.5px] text-content-muted">
        Already have one? <Link to="/login" className="font-medium text-brand hover:underline">Sign in</Link>
      </p>
    </AuthShell>
  );
}
