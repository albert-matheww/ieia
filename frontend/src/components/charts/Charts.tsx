import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart,
  ReferenceArea, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { categoryColor, monthShort, num } from "@/lib/format";
import type { TimeSeriesPoint } from "@/api/types";

const AXIS = { stroke: "rgb(var(--line))", tick: { fill: "rgb(var(--content-dim))", fontSize: 11 } };

function TipBox({ label, rows }: { label: string; rows: { name: string; value: string; color?: string }[] }) {
  return (
    <div className="rounded border border-line bg-surface-2 px-2.5 py-2 text-[11px] shadow-pop">
      <p className="label-inst mb-1">{label}</p>
      {rows.map((r) => (
        <div key={r.name} className="flex items-center gap-2">
          {r.color && <span className="h-2 w-2 rounded-sm" style={{ background: r.color }} />}
          <span className="text-content-muted">{r.name}</span>
          <span className="readout ml-auto">{r.value}</span>
        </div>
      ))}
    </div>
  );
}

export function TrendChart({
  data, color = "#2D6A4F", height = 200, area = true, unit,
}: { data: TimeSeriesPoint[]; color?: string; height?: number; area?: boolean; unit?: string }) {
  const rows = data.map((d) => ({ period: d.period, value: d.value ?? 0 }));
  const Chart = area ? AreaChart : LineChart;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <Chart data={rows} margin={{ top: 6, right: 8, left: -12, bottom: 0 }}>
        <defs>
          <linearGradient id={`g-${color}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.28} />
            <stop offset="100%" stopColor={color} stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="2 3" vertical={false} />
        <XAxis dataKey="period" tickFormatter={monthShort} axisLine={false} tickLine={false} {...AXIS} />
        <YAxis width={44} tickFormatter={(v) => num(v, { compact: true })} axisLine={false} tickLine={false} {...AXIS} />
        <Tooltip
          content={({ active, payload, label }) =>
            active && payload?.length ? (
              <TipBox label={monthShort(String(label))} rows={[{ name: unit ?? "value", value: num(Number(payload[0].value), { dp: 1 }), color }]} />
            ) : null
          }
        />
        {area ? (
          <Area type="monotone" dataKey="value" stroke={color} strokeWidth={2} fill={`url(#g-${color})`} />
        ) : (
          <Line type="monotone" dataKey="value" stroke={color} strokeWidth={2} dot={false} />
        )}
      </Chart>
    </ResponsiveContainer>
  );
}

export function AnomalyTrend({
  data, anomalyPeriod, color = "#457B9D", height = 200,
}: { data: TimeSeriesPoint[]; anomalyPeriod?: string; color?: string; height?: number }) {
  const rows = data.map((d) => ({ period: d.period, value: d.value ?? 0 }));
  const idx = rows.findIndex((r) => r.period === anomalyPeriod);
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={rows} margin={{ top: 6, right: 8, left: -12, bottom: 0 }}>
        <CartesianGrid strokeDasharray="2 3" vertical={false} />
        <XAxis dataKey="period" tickFormatter={monthShort} axisLine={false} tickLine={false} {...AXIS} />
        <YAxis width={44} tickFormatter={(v) => num(v, { compact: true })} axisLine={false} tickLine={false} {...AXIS} />
        <Tooltip content={({ active, payload, label }) => active && payload?.length ? (
          <TipBox label={monthShort(String(label))} rows={[{ name: "value", value: num(Number(payload[0].value), { dp: 1 }) }]} />
        ) : null} />
        {idx >= 0 && (
          <ReferenceArea x1={rows[Math.max(0, idx - 0.5)]?.period} x2={rows[idx]?.period} fill="#B04A34" fillOpacity={0.12} />
        )}
        <Line type="monotone" dataKey="value" stroke={color} strokeWidth={2}
          dot={(props: any) => {
            const isAnom = props.payload.period === anomalyPeriod;
            return <circle key={props.key} cx={props.cx} cy={props.cy} r={isAnom ? 4.5 : 0} fill="#B04A34" stroke="rgb(var(--surface-2))" strokeWidth={1.5} />;
          }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

export function CategoryBars({ data, height = 200 }: { data: { key: string; label: string; value: number }[]; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 16, left: 8, bottom: 0 }}>
        <CartesianGrid strokeDasharray="2 3" horizontal={false} />
        <XAxis type="number" tickFormatter={(v) => num(v, { compact: true })} axisLine={false} tickLine={false} {...AXIS} />
        <YAxis type="category" dataKey="label" width={96} axisLine={false} tickLine={false} {...AXIS} />
        <Tooltip cursor={{ fill: "rgb(var(--surface-3))" }}
          content={({ active, payload }) => active && payload?.length ? (
            <TipBox label={String(payload[0].payload.label)} rows={[{ name: "kgCO₂e", value: num(Number(payload[0].value), { dp: 1 }), color: categoryColor(payload[0].payload.key) }]} />
          ) : null} />
        <Bar dataKey="value" radius={[0, 3, 3, 0]} barSize={16}>
          {data.map((d) => <Cell key={d.key} fill={categoryColor(d.key)} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function Donut({ data, height = 200 }: { data: { key: string; label: string; value: number }[]; height?: number }) {
  const total = data.reduce((s, d) => s + d.value, 0);
  return (
    <div className="flex items-center gap-4">
      <ResponsiveContainer width="55%" height={height}>
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="label" innerRadius="58%" outerRadius="92%" paddingAngle={2} stroke="none">
            {data.map((d) => <Cell key={d.key} fill={categoryColor(d.key)} />)}
          </Pie>
          <Tooltip content={({ active, payload }) => active && payload?.length ? (
            <TipBox label={String(payload[0].name)} rows={[{ name: "kg", value: num(Number(payload[0].value), { dp: 1 }), color: categoryColor(String(payload[0].payload.key)) }]} />
          ) : null} />
        </PieChart>
      </ResponsiveContainer>
      <ul className="flex-1 space-y-1.5">
        {data.map((d) => (
          <li key={d.key} className="flex items-center gap-2 text-[12px]">
            <span className="h-2.5 w-2.5 rounded-sm" style={{ background: categoryColor(d.key) }} />
            <span className="text-content-muted">{d.label}</span>
            <span className="readout ml-auto text-[11px]">{total ? Math.round((d.value / total) * 100) : 0}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
