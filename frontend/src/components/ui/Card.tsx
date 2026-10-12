import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export function Card({ children, className, accent, style }: { children: ReactNode; className?: string; accent?: string; style?: React.CSSProperties }) {
  return (
    <div
      className={cn("card", accent && "card-accent", className)}
      style={accent ? { ...style, ["--rule" as string]: accent } : style}
    >
      {children}
    </div>
  );
}

export function CardHead({ title, sub, action }: { title: ReactNode; sub?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-line px-4 py-3">
      <div>
        <h3 className="font-display text-[13px] font-semibold text-content">{title}</h3>
        {sub && <p className="mt-0.5 text-[11.5px] text-content-dim">{sub}</p>}
      </div>
      {action}
    </div>
  );
}

export function CardBody({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("p-4", className)}>{children}</div>;
}
