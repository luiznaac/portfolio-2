// Pure fixed-income form and table helpers — no React imports here (see AGENTS.md). The RF slice is
// the only place the SPA turns a lançamento into a create payload, so the mapping is unit-tested
// here rather than inside the page.

import type {
  FixedIncomeProductInput,
  FixedIncomeProductKind,
  FixedIncomeProductRef,
} from "../api/queries.ts";
import type {
  Bond,
  BondPosition,
  CheckingAccount,
  FixedIncomeSubClass,
  IndexId,
} from "../api/types.ts";

// How the form names a product: a fixed-rate bond, a floating-rate bond, or a checking account.
export type FixedIncomeKind = "FIXED_BOND" | "FLOATING_BOND" | "ACCOUNT";

// The indexador choice: "PRE" is a fixed rate, not a BACEN series, so it never reaches the API.
export type FixedIncomeIndexChoice = IndexId | "PRE";

// A checking account's maturity is a whole period the backend parses as an ISO-8601 duration.
export const MATURITY_DURATIONS: { value: string; label: string }[] = [
  { value: "P3M", label: "3 meses" },
  { value: "P6M", label: "6 meses" },
  { value: "P1Y", label: "1 ano" },
  { value: "P2Y", label: "2 anos" },
  { value: "P3Y", label: "3 anos" },
  { value: "P5Y", label: "5 anos" },
];

export interface FixedIncomeFormState {
  kind: FixedIncomeKind;
  name: string;
  rate: string;
  index: FixedIncomeIndexChoice;
  maturityDate: string;
  maturityDuration: string;
}

export function emptyFixedIncomeForm(kind: FixedIncomeKind = "FIXED_BOND"): FixedIncomeFormState {
  return {
    kind,
    name: "",
    rate: "",
    index: kind === "FIXED_BOND" ? "PRE" : "CDI",
    maturityDate: "",
    maturityDuration: "P1Y",
  };
}

// Switching the kind must not leave an index the new kind cannot carry: a fixed bond has none, and a
// floating product needs a real series.
export function withKind(form: FixedIncomeFormState, kind: FixedIncomeKind): FixedIncomeFormState {
  if (kind === "FIXED_BOND") return { ...form, kind, index: "PRE" };
  return { ...form, kind, index: form.index === "PRE" ? "CDI" : form.index };
}

const SUB_CLASS_LABELS: Record<FixedIncomeSubClass, string> = {
  FIXED_RATE: "Pré-fixado",
  FLOATING_RATE: "Pós-fixado",
  INFLATION_LINKED: "Inflação",
};

export function subClassLabel(subClass: FixedIncomeSubClass): string {
  return SUB_CLASS_LABELS[subClass];
}

// A fixed bond carries no index, so the Indexador column reads PRÉ for it.
export function productIndexLabel(product: Bond | CheckingAccount): string {
  return product.index_id ?? "PRÉ";
}

export function productKindLabel(product: Bond | CheckingAccount): "Título" | "Conta" {
  return "maturity_date" in product ? "Título" : "Conta";
}

// A maturity the account form offers reads as its pt-BR label; an unrecognised period stays as-is.
export function durationLabel(period: string): string {
  return MATURITY_DURATIONS.find((duration) => duration.value === period)?.label ?? period;
}

export function productKey(kind: FixedIncomeProductKind, id: number): string {
  return `${kind}-${id}`;
}

export function fixedIncomeProductRefs(
  bonds: Bond[],
  accounts: CheckingAccount[],
): FixedIncomeProductRef[] {
  return [
    ...bonds.map((bond) => ({
      key: productKey("bond", bond.id),
      kind: "bond" as const,
      id: bond.id,
    })),
    ...accounts.map((account) => ({
      key: productKey("account", account.id),
      kind: "account" as const,
      id: account.id,
    })),
  ];
}

// Joins each product to the position read that shares its key. The reads arrive in the order the
// hook was called with; the map is keyed so a row can never read another product's value.
export function positionsByProductKey(
  products: readonly FixedIncomeProductRef[],
  reads: readonly (readonly BondPosition[] | undefined)[],
): Map<string, readonly BondPosition[]> {
  return new Map(products.map((product, index) => [product.key, reads[index] ?? []]));
}

// A product's value is its latest consolidated position's principal plus yield — the accrual the
// allocation read values it at. The date wins, not the array order (the API's ordering is not part
// of the contract's promise).
function latestPosition(positions: readonly BondPosition[]): BondPosition | undefined {
  return positions.reduce<BondPosition | undefined>(
    (latest, candidate) =>
      latest === undefined || candidate.date > latest.date ? candidate : latest,
    undefined,
  );
}

// Undefined while the product has no consolidated position — unknown, never R$ 0,00.
export function productValue(positions: readonly BondPosition[]): number | undefined {
  const latest = latestPosition(positions);
  return latest === undefined ? undefined : latest.principal + latest.yield;
}

export interface FixedIncomeRow {
  key: string;
  name: string;
  kindLabel: string;
  rate: number;
  indexLabel: string;
  subClass: FixedIncomeSubClass;
  subClassLabel: string;
  maturity: string;
  value?: number;
}

// The Products table shows both kinds in one list: bonds carry a maturity date, accounts a maturity
// duration, and either reads as a plain string here. The value joins each product to its own accrual
// read, keyed by the row key.
export function fixedIncomeRows(
  bonds: Bond[],
  accounts: CheckingAccount[],
  positionsByKey: ReadonlyMap<string, readonly BondPosition[]>,
): FixedIncomeRow[] {
  const rows = [
    ...bonds.map((bond) => productRow("bond", bond, bond.maturity_date, positionsByKey)),
    ...accounts.map((account) =>
      productRow("account", account, durationLabel(account.maturity_duration), positionsByKey),
    ),
  ];

  return rows.sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
}

function productRow(
  kind: FixedIncomeProductKind,
  product: Bond | CheckingAccount,
  maturity: string,
  positionsByKey: ReadonlyMap<string, readonly BondPosition[]>,
): FixedIncomeRow {
  const key = productKey(kind, product.id);
  return {
    key,
    name: product.name,
    kindLabel: productKindLabel(product),
    rate: product.rate,
    indexLabel: productIndexLabel(product),
    subClass: product.sub_class,
    subClassLabel: subClassLabel(product.sub_class),
    maturity,
    value: productValue(positionsByKey.get(key) ?? []),
  };
}

export interface FixedIncomeTotal {
  // Undefined when none of the rows is priced — distinct from a real zero.
  value: number | undefined;
  // True while at least one product has no consolidated position, so the total covers only part.
  partial: boolean;
}

export function fixedIncomeTotal(rows: readonly FixedIncomeRow[]): FixedIncomeTotal {
  let value = 0;
  let priced = 0;
  let unpriced = 0;
  for (const row of rows) {
    if (row.value === undefined) {
      unpriced += 1;
    } else {
      value += row.value;
      priced += 1;
    }
  }
  return {
    value: priced === 0 ? (rows.length === 0 ? 0 : undefined) : value,
    partial: unpriced > 0,
  };
}

// Builds the create payload, or null while the form is incomplete — the page keeps submit disabled on
// null and never posts half a produto.
export function toProductInput(form: FixedIncomeFormState): FixedIncomeProductInput | null {
  const rate = Number(form.rate);
  if (form.name.trim().length === 0 || !Number.isFinite(rate) || rate <= 0) return null;

  switch (form.kind) {
    case "FIXED_BOND":
      if (form.maturityDate.length === 0) return null;
      return {
        kind: "FIXED_BOND",
        creation: { name: form.name, rate, maturity_date: form.maturityDate },
      };
    case "FLOATING_BOND":
      if (form.index === "PRE" || form.maturityDate.length === 0) return null;
      return {
        kind: "FLOATING_BOND",
        creation: {
          name: form.name,
          rate,
          maturity_date: form.maturityDate,
          index_id: form.index,
        },
      };
    case "ACCOUNT":
      if (form.index === "PRE" || form.maturityDuration.length === 0) return null;
      return {
        kind: "ACCOUNT",
        creation: {
          name: form.name,
          rate,
          index_id: form.index,
          maturity_duration: form.maturityDuration,
        },
      };
  }
}
