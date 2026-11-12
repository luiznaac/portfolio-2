import type { ImportDestinationObject, ImportSource, ImportStatus } from "../api/types.ts";
import { ROUTES } from "./routes.ts";

// One destination as the assistant renders it: the console object the record lands on, the source
// and parser the context pre-fills, the columns the recognised template maps, and the objects an
// apply touches. `supported` is false until that destination's own variant of the assistant lands;
// the route and label still resolve so a staged session can name where it goes.
export interface DestinationMeta {
  object: ImportDestinationObject;
  label: string;
  route: string;
  source: ImportSource;
  parser: string;
  supported: boolean;
  changes: readonly { object: string; what: string }[];
  mapping: readonly { from: string; to: string }[];
}

const TRADES_MAPPING = [
  { from: "Data", to: "Data do negócio" },
  { from: "Tipo de Movimentação", to: "Compra / venda" },
  { from: "Código de Negociação", to: "Ticker" },
  { from: "Quantidade", to: "Quantidade" },
  { from: "Preço Unitário", to: "Preço" },
  { from: "Instituição", to: "Corretora" },
];

export const DESTINATIONS: Record<ImportDestinationObject, DestinationMeta> = {
  TRADES: {
    object: "TRADES",
    label: "Ativos · Negócios",
    route: ROUTES.trades,
    source: "B3_TRADES",
    parser: "B3 — negociação (xlsx)",
    supported: true,
    changes: [
      { object: "Ativos · Negócios", what: "as linhas válidas entram no livro de negócios" },
      { object: "Ativos · Posições", what: "a posição e o preço médio do ticker são recalculados" },
    ],
    mapping: TRADES_MAPPING,
  },
  POSITIONS: {
    object: "POSITIONS",
    label: "Ativos · Posições",
    route: ROUTES.positions,
    source: "B3_POSITION",
    parser: "B3 — posição em custódia (xlsx)",
    supported: false,
    changes: [],
    mapping: [],
  },
  INCOME: {
    object: "INCOME",
    label: "Análise · Proventos",
    route: ROUTES.income,
    source: "B3_MOVEMENTS",
    parser: "B3 — proventos (xlsx)",
    supported: false,
    changes: [],
    mapping: [],
  },
  STRATEGY_EDITION: {
    object: "STRATEGY_EDITION",
    label: "Estratégias · Edições",
    route: ROUTES.strategies,
    source: "XP_REPORT",
    parser: "XP — relatório de carteira (csv)",
    supported: false,
    changes: [],
    mapping: [],
  },
  CORPORATE_ACTIONS: {
    object: "CORPORATE_ACTIONS",
    label: "Importações · Ações corporativas",
    route: ROUTES.corporateActions,
    source: "B3_MOVEMENTS",
    parser: "B3 — movimentação (xlsx)",
    supported: false,
    changes: [],
    mapping: [],
  },
};

export const DEFAULT_DESTINATION = DESTINATIONS.TRADES;

// The URL context names a destination; an absent or unknown value is the global upload, which
// defaults to the trades assistant rather than guessing.
export function resolveDestination(raw: string | null): DestinationMeta {
  return raw !== null && Object.hasOwn(DESTINATIONS, raw)
    ? DESTINATIONS[raw as ImportDestinationObject]
    : DEFAULT_DESTINATION;
}

export function destinationRoute(object: ImportDestinationObject): string {
  return DESTINATIONS[object].route;
}

export function assistantPath(object: ImportDestinationObject): string {
  return `${ROUTES.importAssistant}?object=${object}`;
}

export function sessionPath(importId: number): string {
  return `${ROUTES.importAssistant}/${importId}`;
}

// A parked session opens on the material it has left to review; a reviewed one opens on the apply
// decision. A fresh upload starts at step 1.
export function sessionStep(hasSession: boolean, status: ImportStatus | undefined): number {
  if (!hasSession) return 1;
  return status === "STAGED" ? 3 : 4;
}
