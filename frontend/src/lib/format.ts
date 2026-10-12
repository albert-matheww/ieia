/** Presentation helpers. All measured values render in mono elsewhere; these format the strings. */

export function num(value: number | null | undefined, opts: { dp?: number; compact?: boolean } = {}): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  const { dp, compact } = opts;
  if (compact && Math.abs(value) >= 1000) {
    return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(value);
  }
  const digits = dp ?? (Math.abs(value) >= 100 ? 0 : Math.abs(value) >= 1 ? 1 : 2);
  return new Intl.NumberFormat("en", { maximumFractionDigits: digits, minimumFractionDigits: 0 }).format(value);
}

export function pct(value: number | null | undefined, dp = 1): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  const s = value.toFixed(dp);
  return `${value > 0 ? "+" : ""}${s}%`;
}

export function signed(value: number | null | undefined, dp = 1): string {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return `${value > 0 ? "+" : ""}${value.toFixed(dp)}`;
}

/** "2026-06" -> "Jun 2026" */
export function monthLabel(period: string | null | undefined): string {
  if (!period) return "—";
  const [y, m] = period.split("-").map(Number);
  if (!y || !m) return period;
  return new Date(y, m - 1, 1).toLocaleDateString("en", { month: "short", year: "numeric" });
}

/** "2026-06" -> "Jun" (compact, for axes) */
export function monthShort(period: string): string {
  const [y, m] = period.split("-").map(Number);
  if (!y || !m) return period;
  const d = new Date(y, m - 1, 1);
  return m === 1 ? d.toLocaleDateString("en", { month: "short", year: "2-digit" }) : d.toLocaleDateString("en", { month: "short" });
}

export function bytes(n: number | null | undefined): string {
  if (!n) return "—";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export function relTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.round(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.round(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return new Date(iso).toLocaleDateString("en", { month: "short", day: "numeric" });
}

export const CATEGORY_COLOR: Record<string, string> = {
  ELECTRICITY: "#E9C46A",
  ENERGY: "#E9C46A",
  TRANSPORT: "#6D6875",
  FUEL: "#B04A34",
  WATER: "#457B9D",
  WASTE: "#8A5A44",
  MATERIALS: "#7A8670",
  CARBON: "#40514E",
  CO2E: "#40514E",
};

export function categoryColor(key: string): string {
  return CATEGORY_COLOR[key?.toUpperCase()] ?? "#52796F";
}

export const RATING_TONE: Record<string, string> = {
  GOOD: "text-positive border-positive/40 bg-positive/10",
  AVERAGE: "text-sage border-line bg-surface-3",
  ABOVE_AVERAGE: "text-positive border-positive/40 bg-positive/10",
  NEEDS_IMPROVEMENT: "text-negative border-negative/40 bg-negative/10",
};
