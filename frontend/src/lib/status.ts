// The shell's reads of the portfolio status aggregate (issue #368): the sidebar's per-object
// attention counts and the top bar's freshness and open-month lines. Every sentence is written
// here from typed fields — the wire carries numbers, ids and enum kinds only.

import type { AttentionItem, PortfolioStatus } from "../api/types.ts";
import { formatIsoDate } from "./format.ts";
import { monthPeriodLabel } from "./month.ts";
import type { ObjectKey } from "./routes.ts";

// Which sidebar object owns a finding. The aggregate's object kind is coarser than the IA on
// purpose: a corporate-action review lives under Importações, a stale quote under Ativos.
const OBJECT_OF: Record<AttentionItem["object"], ObjectKey> = {
  MONTH: "plan",
  IMPORT: "imports",
  STRATEGY: "strategies",
  CORPORATE_ACTION: "imports",
  ASSET: "assets",
};

export interface AttentionBadge {
  count: number;
  action: boolean;
}

export function attentionBadges(
  items: AttentionItem[],
): Partial<Record<ObjectKey, AttentionBadge>> {
  const badges: Partial<Record<ObjectKey, AttentionBadge>> = {};
  for (const item of items) {
    const key = OBJECT_OF[item.object];
    const current = badges[key] ?? { count: 0, action: false };
    badges[key] = {
      count: current.count + 1,
      action: current.action || item.severity === "ACTION",
    };
  }
  return badges;
}

export function quoteFreshnessLabel(status: PortfolioStatus): string {
  const asOf = status.data.quotes_as_of;
  return asOf === undefined ? "sem cotações" : `cotações ${formatIsoDate(asOf)}`;
}

export function openMonthLabel(status: PortfolioStatus): string {
  if (!status.month.open || status.open_month === undefined) return "Nenhum mês aberto";
  return `${monthPeriodLabel(status.open_month.period)} · aberto`;
}
