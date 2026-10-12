import type { ReactNode } from "react";
import { Spinner } from "./primitives";
import { cn } from "@/lib/cn";

export function PageHeader({ title, sub, actions }: { title: string; sub?: string; actions?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="font-display text-[22px] font-semibold tracking-tight text-content">{title}</h1>
        {sub && <p className="mt-1 max-w-xl text-[13px] text-content-muted">{sub}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  );
}

export function LoadingBlock({ label = "Loading", className }: { label?: string; className?: string }) {
  return (
    <div className={cn("flex items-center justify-center gap-2.5 py-16 text-content-dim", className)}>
      <Spinner className="h-4 w-4" />
      <span className="label-inst">{label}</span>
    </div>
  );
}

export function EmptyState({ title, body, action, icon }: { title: string; body?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-md border border-dashed border-line bg-surface-2 px-6 py-14 text-center">
      {icon && <div className="mb-3 text-content-dim">{icon}</div>}
      <h3 className="font-display text-[15px] font-semibold text-content">{title}</h3>
      {body && <p className="mt-1.5 max-w-sm text-[13px] text-content-muted">{body}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : "Something went wrong loading this view.";
  return (
    <div className="rounded-md border border-negative/30 bg-negative/5 px-5 py-8 text-center">
      <p className="label-inst text-negative">Couldn't load</p>
      <p className="mt-1.5 text-[13px] text-content-muted">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="btn btn-sm btn-ghost mt-4">Try again</button>
      )}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("animate-pulse rounded bg-surface-3", className)} />;
}
