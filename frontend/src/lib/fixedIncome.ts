// Pure fixed-income form and table helpers — no React imports here (see AGENTS.md). The RF slice is
// the only place the SPA turns a lançamento into a create payload, so the mapping is unit-tested
// here rather than inside the page.

import type { FixedIncomeProductInput } from "../api/queries.ts";
import type { Bond, CheckingAccount, FixedIncomeSubClass, IndexId } from "../api/types.ts";

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

export interface FixedIncomeRow {
  key: string;
  name: string;
  kindLabel: string;
  rate: number;
  indexLabel: string;
  subClass: FixedIncomeSubClass;
  subClassLabel: string;
  maturity: string;
}

// The Products table shows both kinds in one list: bonds carry a maturity date, accounts a maturity
// duration, and either reads as a plain string here.
export function fixedIncomeRows(bonds: Bond[], accounts: CheckingAccount[]): FixedIncomeRow[] {
  const rows = [
    ...bonds.map((bond) => productRow(bond, bond.maturity_date)),
    ...accounts.map((account) => productRow(account, durationLabel(account.maturity_duration))),
  ];

  return rows.sort((a, b) => a.name.localeCompare(b.name, "pt-BR"));
}

// An id is unique only within its kind, so the row key carries the kind too.
function productRow(product: Bond | CheckingAccount, maturity: string): FixedIncomeRow {
  return {
    key: `${"maturity_date" in product ? "bond" : "account"}-${product.id}`,
    name: product.name,
    kindLabel: productKindLabel(product),
    rate: product.rate,
    indexLabel: productIndexLabel(product),
    subClass: product.sub_class,
    subClassLabel: subClassLabel(product.sub_class),
    maturity,
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
