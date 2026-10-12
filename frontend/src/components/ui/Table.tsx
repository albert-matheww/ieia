import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export function Table({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className="overflow-x-auto">
      <table className={cn("w-full min-w-[520px] border-collapse text-[12.5px]", className)}>{children}</table>
    </div>
  );
}

export function Th({ children, className, align = "left" }: { children?: ReactNode; className?: string; align?: "left" | "right" }) {
  return (
    <th
      className={cn(
        "label-inst border-b border-line px-3 py-2 font-medium",
        align === "right" ? "text-right" : "text-left",
        className,
      )}
    >
      {children}
    </th>
  );
}

export function Td({ children, className, mono, align = "left" }: { children?: ReactNode; className?: string; mono?: boolean; align?: "left" | "right" }) {
  return (
    <td
      className={cn(
        "border-b border-line px-3 py-2.5 text-content-muted",
        mono && "font-mono tabular-nums text-content",
        align === "right" && "text-right",
        className,
      )}
    >
      {children}
    </td>
  );
}

export function Tr({ children, onClick, className }: { children: ReactNode; onClick?: () => void; className?: string }) {
  return (
    <tr
      onClick={onClick}
      className={cn("transition-colors", onClick && "cursor-pointer hover:bg-surface-3", className)}
    >
      {children}
    </tr>
  );
}
