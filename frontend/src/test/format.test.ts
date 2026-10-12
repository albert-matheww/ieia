import { describe, expect, it } from "vitest";
import { num, pct, monthLabel, monthShort, bytes, categoryColor } from "@/lib/format";

describe("format helpers", () => {
  it("num() adapts precision and handles nullish", () => {
    expect(num(null)).toBe("—");
    expect(num(1234.56)).toBe("1,235");
    expect(num(12.34)).toBe("12.3");
    expect(num(0.123)).toBe("0.12");
    expect(num(4200, { compact: true })).toBe("4.2K");
  });

  it("pct() is signed", () => {
    expect(pct(5.2)).toBe("+5.2%");
    expect(pct(-3)).toBe("-3.0%");
    expect(pct(null)).toBe("—");
  });

  it("monthLabel / monthShort format periods", () => {
    expect(monthLabel("2026-06")).toBe("Jun 2026");
    expect(monthShort("2026-06")).toBe("Jun");
    expect(monthShort("2026-01")).toBe("Jan 26");
  });

  it("bytes() is human readable", () => {
    expect(bytes(512)).toBe("512 B");
    expect(bytes(2048)).toBe("2.0 KB");
  });

  it("categoryColor() maps known keys and falls back", () => {
    expect(categoryColor("ELECTRICITY")).toBe("#E9C46A");
    expect(categoryColor("water")).toBe("#457B9D");
    expect(categoryColor("unknown")).toBe("#52796F");
  });
});
