import type { ImportDestinationObject, ImportSource, ImportStatus } from "../api/types.ts";
import { ROUTES } from "./routes.ts";

// One recognised layout for a destination. Trades has two — the B3 workbook and the Rico broker
// note PDF — so the assistant lets the owner pick the layout and stages the matching source. A
// destination with one layout keeps its `source`/`parser`/`mapping` and omits `variants`.
export interface ImportVariant {
  source: ImportSource;
  parser: string;
  accept: string;
  mapping: readonly { from: string; to: string }[];
}

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
  // The file type the single-layout upload accepts; a destination with `variants` carries one per
  // layout instead.
  accept: string;
  supported: boolean;
  changes: readonly { object: string; what: string }[];
  mapping: readonly { from: string; to: string }[];
  variants?: readonly ImportVariant[];
}

const TRADES_MAPPING = [
  { from: "Data", to: "Data do negócio" },
  { from: "Tipo de Movimentação", to: "Compra / venda" },
  { from: "Código de Negociação", to: "Ticker" },
  { from: "Quantidade", to: "Quantidade" },
  { from: "Preço Unitário", to: "Preço" },
  { from: "Instituição", to: "Corretora" },
];

// The Rico broker note prints a description instead of a ticker; the parser keeps the raw
// description and the alias lookup resolves it, which the review shows as description → ticker.
const RICO_MAPPING = [
  { from: "Data pregão", to: "Data do negócio" },
  { from: "Mercado", to: "Vista / fracionário" },
  { from: "Especificação do título", to: "Descrição → ticker" },
  { from: "Quantidade", to: "Quantidade" },
  { from: "Preço / Ajuste", to: "Preço" },
  { from: "D/C", to: "Compra / venda" },
];

const EDITION_MAPPING = [
  { from: "ticker", to: "Código" },
  { from: "peso", to: "Peso alvo" },
  { from: "preço-alvo", to: "Preço-alvo" },
  { from: "rating", to: "Recomendação" },
  { from: "não operar", to: "Não operar" },
];

const CUSTODY_MAPPING = [
  { from: "Código de Negociação", to: "Ticker" },
  { from: "Quantidade", to: "Quantidade em custódia" },
];

export const DESTINATIONS: Record<ImportDestinationObject, DestinationMeta> = {
  TRADES: {
    object: "TRADES",
    label: "Ativos · Negócios",
    route: ROUTES.trades,
    source: "B3_TRADES",
    parser: "B3 — negociação (xlsx)",
    accept: ".xlsx",
    supported: true,
    changes: [
      { object: "Ativos · Negócios", what: "as linhas válidas entram no livro de negócios" },
      { object: "Ativos · Posições", what: "a posição e o preço médio do ticker são recalculados" },
    ],
    mapping: TRADES_MAPPING,
    variants: [
      {
        source: "B3_TRADES",
        parser: "B3 — negociação (xlsx)",
        accept: ".xlsx",
        mapping: TRADES_MAPPING,
      },
      {
        source: "RICO_NOTES",
        parser: "Rico — nota de corretagem (pdf)",
        accept: ".pdf",
        mapping: RICO_MAPPING,
      },
    ],
  },
  POSITIONS: {
    object: "POSITIONS",
    label: "Ativos · Posições",
    route: ROUTES.positions,
    source: "B3_POSITION",
    parser: "B3 — posição em custódia (xlsx)",
    accept: ".xlsx",
    supported: true,
    changes: [
      {
        object: "Ativos · Posições",
        what: "a quantidade de cada ticker passa a ser a da custódia",
      },
      {
        object: "Ativos · Lacunas",
        what: "cada divergência abre uma lacuna; a venda faltante fica com resultado pendente",
      },
    ],
    mapping: CUSTODY_MAPPING,
  },
  INCOME: {
    object: "INCOME",
    label: "Análise · Proventos",
    route: ROUTES.income,
    source: "B3_MOVEMENTS",
    parser: "B3 — proventos (xlsx)",
    accept: ".xlsx",
    supported: false,
    changes: [],
    mapping: [],
  },
  STRATEGY_EDITION: {
    object: "STRATEGY_EDITION",
    label: "Estratégias · Edições",
    route: ROUTES.strategies,
    source: "XP_REPORT",
    // The backend's XP resolver reads the report PDF; the prototype's CSV wording is a re-export
    // narrative, not a second format this slice introduces.
    parser: "XP — relatório de carteira (pdf)",
    accept: ".pdf",
    supported: true,
    changes: [
      {
        object: "Estratégias · Edições",
        what: "a edição promovida substitui a edição ativa da estratégia",
      },
      { object: "Plano / Mês", what: "o plano usa os novos pesos no próximo recálculo" },
    ],
    mapping: EDITION_MAPPING,
  },
  CORPORATE_ACTIONS: {
    object: "CORPORATE_ACTIONS",
    label: "Importações · Ações corporativas",
    route: ROUTES.corporateActions,
    source: "B3_MOVEMENTS",
    parser: "B3 — movimentação (xlsx)",
    accept: ".xlsx",
    supported: false,
    changes: [],
    mapping: [],
  },
};

export const DEFAULT_DESTINATION = DESTINATIONS.TRADES;

// The layouts a destination accepts, in the order the picker offers them. A single-layout
// destination gets one entry built from its own source, so the upload path reads one shape.
export function importVariants(destination: DestinationMeta): readonly ImportVariant[] {
  return (
    destination.variants ?? [
      {
        source: destination.source,
        parser: destination.parser,
        accept: destination.accept,
        mapping: destination.mapping,
      },
    ]
  );
}

// The layout a staged record (or the current pick) names; an unknown source falls back to the
// destination's first layout rather than leaving the step without a mapping.
export function variantFor(
  destination: DestinationMeta,
  source: ImportSource | null,
): ImportVariant {
  const variants = importVariants(destination);
  return variants.find((variant) => variant.source === source) ?? variants[0];
}

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

// The XP variant stages against one Strategy's edition, so its context carries the strategy id
// beside the destination object.
export function editionAssistantPath(strategyId: number): string {
  return `${assistantPath("STRATEGY_EDITION")}&strategy=${strategyId}`;
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
