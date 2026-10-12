import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { Button, Field } from "@/components/ui/primitives";
import { ApiError } from "@/api/client";
import { AuthShell } from "./AuthShell";

export default function LoginPage() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(email.trim(), password);
      nav("/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't sign in. Try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Sign in" sub="Open your environmental impact console.">
      <form onSubmit={onSubmit} className="space-y-4">
        <Field id="email" label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field id="password" label="Password" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
        {error && <p className="rounded border border-negative/40 bg-negative/10 px-3 py-2 text-[12px] text-negative">{error}</p>}
        <Button type="submit" loading={busy} className="w-full">Sign in</Button>
      </form>
      <p className="mt-5 text-center text-[12.5px] text-content-muted">
        New here? <Link to="/register" className="font-medium text-brand hover:underline">Create an account</Link>
      </p>
    </AuthShell>
  );
}
