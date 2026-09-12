import { useMonthlyCapitalGains, useStepUpPlan } from "../api/queries.ts";
import type { MonthlyCapitalGain, StepUpSuggestion } from "../api/types.ts";
import { Panel } from "../components/Panel.tsx";
import { formatBRL } from "../lib/money.ts";

// Fase 5: the month-by-month gain/loss ledger (usecase/tax/capitalgains) and the step-up
// planner (usecase/tax/stepup) that fills the remaining R$20.000 exemption with the positions
// that realize the most exempt gain per real sold. Day-trade taxation isn't modeled here yet —
// see the doc comment on CapitalGainsCalculator.
export function Imposto() {
  const gains = useMonthlyCapitalGains();
  const stepUp = useStepUpPlan();

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-100">Imposto</h1>

      <Panel title="Planejador de step-up">
        {stepUp.isLoading && <p className="text-slate-400">Carregando…</p>}
        {stepUp.error && <p className="text-tax">Falha ao carregar: {String(stepUp.error)}</p>}
        {stepUp.data && (
          <div className="space-y-3">
            {stepUp.data.suggestions.length === 0 ? (
              <p className="text-sm text-slate-500">
                Nada a sugerir — sem posição com ganho não realizado ou sem teto de isenção sobrando.
              </p>
            ) : (
              <>
                <p className="text-sm text-slate-300">
                  Realiza <span className="tabular-nums text-yield">{formatBRL(stepUp.data.total_realized_gain)}</span>{" "}
                  de ganho isento, deixando{" "}
                  <span className="tabular-nums text-slate-100">{formatBRL(stepUp.data.remaining_ceiling_after)}</span>{" "}
                  de teto para o resto do mês.
                </p>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm tabular-nums">
                    <thead>
                      <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                        <th className="py-2 pr-4 font-medium">Ticker</th>
                        <th className="py-2 pr-4 text-right font-medium">Qtd.</th>
                        <th className="py-2 pr-4 text-right font-medium">Vender por</th>
                        <th className="py-2 pr-4 text-right font-medium">Ganho realizado</th>
                        <th className="py-2 font-medium">Recomprar em</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/5">
                      {stepUp.data.suggestions.map((s) => (
                        <StepUpRow key={s.listed_asset_id} suggestion={s} />
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>
        )}
      </Panel>

      <Panel title="Ganho de capital por mês">
        {gains.isLoading && <p className="text-slate-400">Carregando…</p>}
        {gains.error && <p className="text-tax">Falha ao carregar: {String(gains.error)}</p>}
        {gains.data && gains.data.length === 0 && (
          <p className="text-sm text-slate-500">Nenhuma venda registrada ainda.</p>
        )}
        {gains.data && gains.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Mês</th>
                  <th className="py-2 pr-4 font-medium">Ativo</th>
                  <th className="py-2 pr-4 text-right font-medium">Vendido</th>
                  <th className="py-2 pr-4 text-right font-medium">Ganho</th>
                  <th className="py-2 pr-4 text-right font-medium">Prejuízo usado</th>
                  <th className="py-2 pr-4 text-right font-medium">Tributável</th>
                  <th className="py-2 text-right font-medium">Imposto (DARF)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {gains.data.map((g) => (
                  <MonthRow key={`${g.month}-${g.is_fii}`} gain={g} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
}

function StepUpRow({ suggestion }: { suggestion: StepUpSuggestion }) {
  return (
    <tr>
      <td className="py-2 pr-4 text-slate-200">{suggestion.ticker}</td>
      <td className="py-2 pr-4 text-right text-slate-200">{suggestion.quantity}</td>
      <td className="py-2 pr-4 text-right text-slate-200">{formatBRL(suggestion.notional)}</td>
      <td className="py-2 pr-4 text-right text-yield">{formatBRL(suggestion.realized_gain)}</td>
      <td className="py-2 text-slate-400">{suggestion.rebuy_date}</td>
    </tr>
  );
}

function MonthRow({ gain }: { gain: MonthlyCapitalGain }) {
  return (
    <tr>
      <td className="py-2 pr-4 text-slate-300">{gain.month.slice(0, 7)}</td>
      <td className="py-2 pr-4 text-slate-200">
        {gain.is_fii ? "FIIs" : "Ações"}
        {gain.exempt && (
          <span className="ml-1.5 rounded bg-yield/15 px-1 py-0.5 text-[10px] text-yield">isento</span>
        )}
      </td>
      <td className="py-2 pr-4 text-right text-slate-200">{formatBRL(gain.proceeds)}</td>
      <td className={`py-2 pr-4 text-right ${gain.gross_gain < 0 ? "text-tax" : "text-slate-200"}`}>
        {formatBRL(gain.gross_gain)}
      </td>
      <td className="py-2 pr-4 text-right text-slate-400">{formatBRL(gain.loss_compensated)}</td>
      <td className="py-2 pr-4 text-right text-slate-200">{formatBRL(gain.taxable_gain)}</td>
      <td className="py-2 text-right text-tax">{formatBRL(gain.tax_due)}</td>
    </tr>
  );
}
