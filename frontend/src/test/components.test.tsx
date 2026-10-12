import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatCard } from "@/components/ui/StatCard";
import { LevelMeter } from "@/components/instruments/LevelMeter";
import { ScoreGauge } from "@/components/instruments/ScoreGauge";
import { DeltaTag } from "@/components/ui/primitives";

describe("UI components", () => {
  it("StatCard renders label, value and a delta", () => {
    render(<StatCard label="Total CO₂e" value={890} unit="kg" delta={-5.6} />);
    expect(screen.getByText("Total CO₂e")).toBeInTheDocument();
    expect(screen.getByText("890")).toBeInTheDocument();
    expect(screen.getByText(/5\.6%/)).toBeInTheDocument();
  });

  it("StatCard shows an em dash for null values", () => {
    render(<StatCard label="Water" value={null} />);
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  it("DeltaTag treats a decrease as positive for impact metrics", () => {
    const { container } = render(<DeltaTag value={-8} invert />);
    expect(container.firstChild).toHaveClass("text-positive");
  });

  it("DeltaTag treats an increase as negative for impact metrics", () => {
    const { container } = render(<DeltaTag value={12} invert />);
    expect(container.firstChild).toHaveClass("text-negative");
  });

  it("LevelMeter clamps and renders the rounded score", () => {
    render(<LevelMeter label="Carbon" value={73.4} color="#40514E" />);
    expect(screen.getByText("Carbon")).toBeInTheDocument();
    expect(screen.getByText("73")).toBeInTheDocument();
  });

  it("ScoreGauge exposes an accessible label with the score", () => {
    render(<ScoreGauge score={72} delta={6} />);
    expect(screen.getByLabelText(/Environmental score 72 out of 100/)).toBeInTheDocument();
  });
});
