import { forwardRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type SelectHTMLAttributes, type TextareaHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/cn";

type BtnVariant = "primary" | "ghost" | "danger";
export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & { variant?: BtnVariant; size?: "sm" | "md"; loading?: boolean }>(
  ({ className, variant = "primary", size = "md", loading, disabled, children, ...props }, ref) => (
    <button
      ref={ref}
      className={cn("btn", size === "sm" ? "btn-sm" : "btn-md", `btn-${variant}`, className)}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <Spinner className="h-3.5 w-3.5" />}
      {children}
    </button>
  ),
);
Button.displayName = "Button";

export function Spinner({ className }: { className?: string }) {
  return (
    <svg className={cn("animate-spin text-current", className ?? "h-4 w-4")} viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2.5" className="opacity-25" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
    </svg>
  );
}

export const Field = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label?: string; hint?: string; error?: string }>(
  ({ label, hint, error, className, id, ...props }, ref) => (
    <div>
      {label && <label htmlFor={id} className="field-label">{label}</label>}
      <input ref={ref} id={id} className={cn("field", error && "border-negative", className)} {...props} />
      {error ? <p className="mt-1 text-[11px] text-negative">{error}</p>
        : hint ? <p className="mt-1 text-[11px] text-content-dim">{hint}</p> : null}
    </div>
  ),
);
Field.displayName = "Field";

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { label?: string }>(
  ({ label, className, id, children, ...props }, ref) => (
    <div>
      {label && <label htmlFor={id} className="field-label">{label}</label>}
      <select ref={ref} id={id} className={cn("field appearance-none pr-8", className)} {...props}>
        {children}
      </select>
    </div>
  ),
);
Select.displayName = "Select";

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { label?: string }>(
  ({ label, className, id, ...props }, ref) => (
    <div>
      {label && <label htmlFor={id} className="field-label">{label}</label>}
      <textarea ref={ref} id={id} className={cn("field h-auto min-h-[76px] resize-y py-2", className)} {...props} />
    </div>
  ),
);
Textarea.displayName = "Textarea";

export function Badge({ children, tone = "neutral", className }: { children: ReactNode; tone?: "neutral" | "positive" | "negative" | "signal"; className?: string }) {
  const tones = {
    neutral: "border-line bg-surface-3 text-content-muted",
    positive: "border-positive/40 bg-positive/10 text-positive",
    negative: "border-negative/40 bg-negative/10 text-negative",
    signal: "border-signal/50 bg-signal/15 text-[#8a6d1f] dark:text-signal",
  } as const;
  return <span className={cn("chip", tones[tone], className)}>{children}</span>;
}

export function DeltaTag({ value, invert = true, suffix = "%" }: { value: number | null | undefined; invert?: boolean; suffix?: string }) {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return <span className="font-mono text-[11px] text-content-dim">—</span>;
  }
  // invert: for impact metrics, a decrease is good (positive tone)
  const good = invert ? value < 0 : value > 0;
  const neutral = Math.abs(value) < 0.05;
  return (
    <span
      className={cn(
        "font-mono text-[11px] font-medium tabular-nums",
        neutral ? "text-content-dim" : good ? "text-positive" : "text-negative",
      )}
    >
      {value > 0 ? "▲" : value < 0 ? "▼" : "·"} {Math.abs(value).toFixed(1)}{suffix}
    </span>
  );
}
