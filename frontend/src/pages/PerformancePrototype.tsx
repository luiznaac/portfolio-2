import type { ReactNode } from "react";
import type { BenchmarkIndex, PeriodPerformance } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { PrototypeSwitcher } from "../components/PrototypeSwitcher.tsx";
import { monthPeriodLabel } from "../lib/month.ts";
import { BENCHMARK_INDEXES } from "../lib/performance.ts";
import { Performance as RealPerformance } from "./Performance.tsx";

// THROWAWAY UI prototype for wayfinder ticket #299 (Q6): how does the cumulative Performance chart
// show an IBOV stretch the source could not price? Three variants on the existing /performance route,
// switchable via ?variant=A|B|C with stub data (the real page is untouched when the param is absent).
// Question: gap, dashed carry-forward with a shaded band, or today's silent flat line + coverage strip?

const VARIANTS = ["A", "B", "C"] as const;
const NAMES: Record<string, string> = {
  A: "linha quebrada",
  B: "faixa + tracejado",
  C: "plana + faixa de cobertura",
};

const MISSING_FROM = "2021-09";
const MISSING_TO = "2022-12";

// 2020-12 → 2023-12. IBOV priced 2020-12…2021-08 and 2023-01…2023-12; the 16 months between are the
// stretch a dead source leaves (BCB SGS 7 ends 2019-09 in production, so today every month is flat).
function ibovPriced(period: string): boolean {
  return period <= "2021-08" || period >= "2023-01";
}

const PERIODS = (() => {
  const periods: string[] = [];
  let year = 2020;
  let month = 12;
  for (let i = 0; i < 37; i += 1) {
    periods.push(`${year}-${String(month).padStart(2, "0")}`);
    month += 1;
    if (month > 12) {
      month = 1;
      year += 1;
    }
  }
  return periods;
})();

const STUB: PeriodPerformance[] = PERIODS.map((period, index) => {
  const wave = Math.sin(index / 2.7);
  const portfolio = index === 0 ? 0 : wave * 2.6 + 0.55;
  const cdi = 0.72 + Math.cos(index / 4) * 0.05;
  const ipca = 0.38 + Math.sin(index / 5) * 0.06;
  const ibov = ibovPriced(period) ? wave * 4.1 + 0.4 : undefined;
  const weighted = 0.6 * cdi + 0.2 * ipca + (ibov !== undefined ? 0.2 * ibov : 0);
  const weight = ibov !== undefined ? 1 : 0.8;

  return {
    period,
    decomposition: {
      value_start: index === 0 ? undefined : 100000 + index * 3100,
      value_end: 100000 + index * 3400,
      net_aportes: 2400,
      net_income: 320,
      return_percentage: index === 0 ? undefined : portfolio,
    },
    benchmark_return: index === 0 ? undefined : weighted / weight,
    benchmarks:
      index === 0
        ? []
        : [
            { index: "CDI" as BenchmarkIndex, return_percentage: cdi },
            { index: "IPCA" as BenchmarkIndex, return_percentage: ipca },
            ...(ibov === undefined
              ? []
              : [{ index: "IBOV" as BenchmarkIndex, return_percentage: ibov }]),
          ],
    classes: [],
    strategies: [],
  };
});

const IBOV_GAP = STUB.filter((month) => !ibovPriced(month.period)).length;

interface Cumulative {
  period: string;
  portfolio: number;
  blend: number;
  cdi: number;
  ipca: number;
  ibov?: number;
  ibovPriced: boolean;
}

const POINTS: Cumulative[] = (() => {
  let portfolio = 100;
  let blend = 100;
  let cdi = 100;
  let ipca = 100;
  let ibov = 100;

  return STUB.map((month, index) => {
    const priced = ibovPriced(month.period);
    if (index > 0) {
      portfolio *= 1 + (month.decomposition.return_percentage ?? 0) / 100;
      blend *= 1 + (month.benchmark_return ?? 0) / 100;
      cdi *=
        1 + (month.benchmarks?.find((row) => row.index === "CDI")?.return_percentage ?? 0) / 100;
      ipca *=
        1 + (month.benchmarks?.find((row) => row.index === "IPCA")?.return_percentage ?? 0) / 100;
      const value = month.benchmarks?.find((row) => row.index === "IBOV")?.return_percentage;
      if (priced && value !== undefined) ibov *= 1 + value / 100;
    }

    return {
      period: month.period,
      portfolio,
      blend,
      cdi,
      ipca,
      ibov: priced ? ibov : undefined,
      ibovPriced: priced,
    };
  });
})();

const GEOMETRY = (() => {
  const width = 720;
  const height = 220;
  const pad = { top: 16, right: 16, bottom: 30, left: 46 };
  const area = {
    left: pad.left,
    top: pad.top,
    width: width - pad.left - pad.right,
    height: height - pad.top - pad.bottom,
  };
  const values = POINTS.flatMap((point) => [
    point.portfolio,
    point.blend,
    point.cdi,
    point.ipca,
    point.ibov,
  ]).filter((value): value is number => value !== undefined);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const padY = (max - min) * 0.12 || 1;
  const low = min - padY;
  const high = max + padY;
  const x = (index: number) => area.left + (index / Math.max(1, POINTS.length - 1)) * area.width;
  const y = (value: number) => area.top + (1 - (value - low) / (high - low)) * area.height;
  return { width, height, pad, area, x, y, low, high };
})();

const { x, y } = GEOMETRY;

// Variant A: the IBOV line simply stops where the source stopped and resumes at the last known level.
function VariantA() {
  const runs: { from: number; to: number }[] = [];
  POINTS.forEach((point, index) => {
    if (!point.ibovPriced) return;
    const last = runs.at(-1);
    if (last && last.to === index - 1) last.to = index;
    else runs.push({ from: index, to: index });
  });

  return (
    <div>
      <ChartFrame>
        {runs.map((run) => (
          <polyline
            key={`ibov-${run.from}`}
            points={POINTS.slice(run.from, run.to + 1)
              .map((point, offset) => `${x(run.from + offset)},${y(point.ibov ?? 0)}`)
              .join(" ")}
            fill="none"
            className="stroke-ibov"
            strokeWidth={1.4}
          />
        ))}
      </ChartFrame>
      <Legend
        note={`IBOV sem dados em ${IBOV_GAP} meses (${monthPeriodLabel(MISSING_FROM)} – ${monthPeriodLabel(MISSING_TO)}) · linha interrompida`}
      />
    </div>
  );
}

// Variant B: the line stays connected as a flat dashed carry across the gap, with a shaded band, so
// the stale stretch is a visible shape rather than just a caption.
function VariantB() {
  const firstGap = POINTS.findIndex((point) => !point.ibovPriced);
  const lastGap = POINTS.length - 1 - [...POINTS].reverse().findIndex((point) => !point.ibovPriced);

  return (
    <div>
      <ChartFrame>
        <rect
          x={x(firstGap)}
          y={GEOMETRY.pad.top}
          width={Math.max(2, x(lastGap) - x(firstGap))}
          height={GEOMETRY.area.height}
          className="fill-ink-faint"
          opacity={0.08}
        />
        <text
          x={(x(firstGap) + x(lastGap)) / 2}
          y={GEOMETRY.pad.top + 10}
          textAnchor="middle"
          className="fill-ink-faint font-mono text-[9px]"
        >
          IBOV sem dados
        </text>
        <polyline
          points={POINTS.slice(0, firstGap + 1)
            .map((point, index) => `${x(index)},${y(point.ibov ?? 0)}`)
            .join(" ")}
          fill="none"
          className="stroke-ibov"
          strokeWidth={1.4}
        />
        <polyline
          points={POINTS.slice(firstGap, lastGap + 1)
            .map((point, offset) => `${x(firstGap + offset)},${y(point.ibov ?? 0)}`)
            .join(" ")}
          fill="none"
          className="stroke-ibov"
          strokeWidth={1.2}
          strokeDasharray="3 3"
          opacity={0.6}
        />
        <polyline
          points={POINTS.slice(lastGap)
            .map((point, offset) => `${x(lastGap + offset)},${y(point.ibov ?? 0)}`)
            .join(" ")}
          fill="none"
          className="stroke-ibov"
          strokeWidth={1.4}
        />
        <circle cx={x(firstGap)} cy={y(POINTS[firstGap].ibov ?? 0)} r={2.2} className="fill-ibov" />
        <circle cx={x(lastGap)} cy={y(POINTS[lastGap].ibov ?? 0)} r={2.2} className="fill-ibov" />
      </ChartFrame>
      <Legend note="IBOV tracejado = valor repetido do último dado conhecido, não performance" />
    </div>
  );
}

// Variant C: the chart keeps today's unbroken flat line, and a coverage strip above it carries the
// truth (months priced, last data) so the flatness is explained rather than implied.
function VariantC() {
  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center gap-x-4 gap-y-1 rounded border border-line bg-surface-2 px-3 py-2">
        {BENCHMARK_INDEXES.map((index) => {
          const present =
            index !== "IBOV" ? POINTS.length : POINTS.filter((point) => point.ibovPriced).length;
          return (
            <span key={index} className="flex items-center gap-2 text-[11px] text-ink-dim">
              <span className={`h-1.5 w-3 rounded-full bg-${index.toLowerCase()}`} />
              <span className="font-mono uppercase">{index}</span>
              <span className="font-mono text-ink-faint">
                {present}/{POINTS.length}
              </span>
            </span>
          );
        })}
        <span className="ml-auto text-[11px] text-tax">IBOV · {IBOV_GAP} meses sem dado</span>
      </div>
      <ChartFrame>
        <polyline
          points={POINTS.map((point, index) => `${x(index)},${y(point.ibov ?? 100)}`).join(" ")}
          fill="none"
          className="stroke-ibov"
          strokeWidth={1.4}
        />
      </ChartFrame>
      <Legend note="linha plana = ausência de dado, não 0% de retorno" />
    </div>
  );
}

function ChartFrame({ children }: { children: ReactNode }) {
  return (
    <svg
      width="100%"
      height={GEOMETRY.height}
      viewBox={`0 0 ${GEOMETRY.width} ${GEOMETRY.height}`}
      role="img"
      aria-label="Retorno acumulado com lacuna de IBOV"
    >
      {[GEOMETRY.low, GEOMETRY.high].map((value) => (
        <g key={value}>
          <line
            x1={GEOMETRY.pad.left}
            y1={y(value)}
            x2={GEOMETRY.width - GEOMETRY.pad.right}
            y2={y(value)}
            className="stroke-line-soft"
            strokeWidth={1}
          />
          <text x={4} y={y(value) + 3} className="fill-ink-faint font-mono text-[9px]">
            {Math.round(value)}
          </text>
        </g>
      ))}
      {children}
      <polyline
        points={POINTS.map((point, index) => `${x(index)},${y(point.blend)}`).join(" ")}
        fill="none"
        className="stroke-blend"
        strokeWidth={1.5}
      />
      <polyline
        points={POINTS.map((point, index) => `${x(index)},${y(point.portfolio)}`).join(" ")}
        fill="none"
        className="stroke-ink"
        strokeWidth={1.9}
      />
      <polyline
        points={POINTS.map((point, index) => `${x(index)},${y(point.cdi)}`).join(" ")}
        fill="none"
        className="stroke-cdi"
        strokeWidth={1.1}
      />
      <polyline
        points={POINTS.map((point, index) => `${x(index)},${y(point.ipca)}`).join(" ")}
        fill="none"
        className="stroke-ipca"
        strokeWidth={1.1}
      />
      <text
        x={GEOMETRY.pad.left}
        y={GEOMETRY.height - 10}
        className="fill-ink-faint font-mono text-[9px]"
      >
        {monthPeriodLabel(POINTS[0].period)}
      </text>
      <text
        x={GEOMETRY.width - GEOMETRY.pad.right}
        y={GEOMETRY.height - 10}
        textAnchor="end"
        className="fill-ink-faint font-mono text-[9px]"
      >
        {monthPeriodLabel(POINTS[POINTS.length - 1].period)}
      </text>
    </svg>
  );
}

function Legend({ note }: { note: string }) {
  return (
    <div className="mt-2 flex flex-wrap items-center gap-4">
      {[
        { label: "Carteira", className: "bg-ink" },
        { label: "Benchmark alvo", className: "bg-blend" },
        { label: "CDI", className: "bg-cdi" },
        { label: "IPCA", className: "bg-ipca" },
        { label: "IBOV", className: "bg-ibov" },
      ].map((item) => (
        <span key={item.label} className="flex items-center gap-1.5 text-[11px] text-ink-dim">
          <span className={`h-1.5 w-3 rounded-full ${item.className}`} />
          {item.label}
        </span>
      ))}
      <span className="w-full text-[11px] text-ink-faint">{note}</span>
    </div>
  );
}

// The benchmark table from the same month, so Q5's "sem dados" row and Q7's blend note are judged
// beside the chart rather than in isolation.
function BenchmarkTable() {
  const rows: { label: string; target: number; value?: number }[] = [
    { label: "IBOV", target: 40 },
    { label: "CDI", target: 40, value: 0.71 },
    { label: "IPCA", target: 20, value: 0.36 },
  ];
  // The real rule (BenchmarkBlendCalculator): the blend is absent while any targeted leg is
  // unpriced — it never renormalises over the priced ones.
  return (
    <div className="space-y-1.5">
      {rows.map((row) => (
        <div
          key={row.label}
          className="grid grid-cols-[56px_52px_1fr_64px] items-center gap-2 text-[11.5px]"
        >
          <span className="font-mono text-ink-dim">{row.label}</span>
          <span className="font-mono text-ink-faint">{row.target}%</span>
          <span className="font-mono text-ink-faint">
            {row.value === undefined ? "—" : `${row.value.toFixed(2)}%`}
          </span>
          <span className="text-right font-mono text-ink-faint">
            {row.value === undefined ? "sem dados" : ""}
          </span>
        </div>
      ))}
      <div className="flex items-baseline justify-between border-t border-line pt-1.5">
        <span className="text-[11.5px] text-ink-dim">Blend alvo</span>
        <span className="font-mono text-[12px] text-ink">—</span>
      </div>
      <p className="text-[10.5px] text-ink-faint">
        Benchmark alvo indisponível: IBOV sem dados (o blend nunca mistura bases)
      </p>
    </div>
  );
}

export function PerformancePrototype({ variant }: { variant: string }) {
  const active = VARIANTS.includes(variant as (typeof VARIANTS)[number]) ? variant : VARIANTS[0];

  return (
    <section>
      <div className="mb-4">
        <h1 className="font-cond text-[24px] font-bold tracking-[.005em]">Performance</h1>
        <p className="text-[13px] text-ink-dim">
          Protótipo · o que o histórico faz quando o IBOV não tem dado no período.
        </p>
      </div>

      <div className="mb-4 grid items-start gap-3 xl:grid-cols-2">
        <Panel title="Carteira × benchmark · acumulado">
          {active === "A" && <VariantA />}
          {active === "B" && <VariantB />}
          {active === "C" && <VariantC />}
        </Panel>
        <Panel title="Benchmark do mês">
          <BenchmarkTable />
        </Panel>
      </div>

      <Panel title="Cenário">
        <p className="text-[12px] text-ink-dim">
          {POINTS.length} meses fechados ({monthPeriodLabel("2020-12")} –{" "}
          {monthPeriodLabel("2023-12")}
          ); IBOV precificado em {POINTS.length - IBOV_GAP}, ausente em {IBOV_GAP} (
          {monthPeriodLabel(MISSING_FROM)} – {monthPeriodLabel(MISSING_TO)}). CDI/IPCA e o blend
          sempre presentes. A linha preta é a carteira; a âmbar, o blend alvo.
        </p>
      </Panel>

      <PrototypeSwitcher variants={[...VARIANTS]} names={NAMES} />
    </section>
  );
}

// The route host: keeps the real page (and its hooks) untouched unless the prototype param is present.
export function PerformanceRoute() {
  const variant = new URLSearchParams(window.location.search).get("variant");
  if (variant) return <PerformancePrototype variant={variant} />;
  return <RealPerformance />;
}
