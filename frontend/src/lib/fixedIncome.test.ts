import { describe, expect, it } from "vitest";
import type { Bond, CheckingAccount } from "../api/types.ts";
import {
  durationLabel,
  emptyFixedIncomeForm,
  fixedIncomeRows,
  productIndexLabel,
  productKindLabel,
  subClassLabel,
  toProductInput,
  withKind,
} from "./fixedIncome.ts";

const prefixado: Bond = {
  id: 1,
  name: "Tesouro Prefixado 2031",
  rate: 12.5,
  maturity_date: "2031-01-01",
  sub_class: "FIXED_RATE",
};

const ipcaPlus: Bond = {
  id: 2,
  name: "Tesouro IPCA+ 2035",
  rate: 6,
  maturity_date: "2035-05-15",
  index_id: "IPCA",
  sub_class: "INFLATION_LINKED",
};

const cdb: CheckingAccount = {
  id: 3,
  name: "CDB 110% CDI",
  rate: 110,
  index_id: "CDI",
  maturity_duration: "P3Y",
  sub_class: "FLOATING_RATE",
};

describe("subClassLabel", () => {
  it("renders each sub-class in pt-BR", () => {
    expect(subClassLabel("FIXED_RATE")).toBe("Pré-fixado");
    expect(subClassLabel("FLOATING_RATE")).toBe("Pós-fixado");
    expect(subClassLabel("INFLATION_LINKED")).toBe("Inflação");
  });
});

describe("productIndexLabel", () => {
  it("reads the index of a floating product", () => {
    expect(productIndexLabel(ipcaPlus)).toBe("IPCA");
    expect(productIndexLabel(cdb)).toBe("CDI");
  });

  it("falls back to PRÉ when the product carries no index", () => {
    expect(productIndexLabel(prefixado)).toBe("PRÉ");
  });
});

describe("productKindLabel", () => {
  it("tells a bond from an account", () => {
    expect(productKindLabel(prefixado)).toBe("Título");
    expect(productKindLabel(cdb)).toBe("Conta");
  });
});

describe("fixedIncomeRows", () => {
  it("merges bonds and accounts into one name-sorted table", () => {
    const rows = fixedIncomeRows([prefixado, ipcaPlus], [cdb]);

    expect(rows.map((row) => row.name)).toEqual([
      "CDB 110% CDI",
      "Tesouro IPCA+ 2035",
      "Tesouro Prefixado 2031",
    ]);
    expect(rows.map((row) => row.key)).toEqual(["account-3", "bond-2", "bond-1"]);
  });

  it("carries the derived labels and the backend sub-class", () => {
    const [row] = fixedIncomeRows([], [cdb]);

    expect(row).toEqual({
      key: "account-3",
      name: "CDB 110% CDI",
      kindLabel: "Conta",
      rate: 110,
      indexLabel: "CDI",
      subClass: "FLOATING_RATE",
      subClassLabel: "Pós-fixado",
      maturity: "3 anos",
    });
  });

  it("keeps a bond's maturity date as the ISO date it arrives with", () => {
    const [row] = fixedIncomeRows([prefixado], []);

    expect(row.maturity).toBe("2031-01-01");
  });

  it("is empty when there are no products", () => {
    expect(fixedIncomeRows([], [])).toEqual([]);
  });
});

describe("durationLabel", () => {
  it("renders a known period in pt-BR", () => {
    expect(durationLabel("P3Y")).toBe("3 anos");
    expect(durationLabel("P3M")).toBe("3 meses");
  });

  it("falls back to the raw period when it is not in the menu", () => {
    expect(durationLabel("P7Y")).toBe("P7Y");
  });
});

describe("toProductInput", () => {
  it("builds a fixed rate bond payload without an index", () => {
    const form = {
      ...emptyFixedIncomeForm("FIXED_BOND"),
      name: "Tesouro",
      rate: "12.5",
      maturityDate: "2031-01-01",
    };

    expect(toProductInput(form)).toEqual({
      kind: "FIXED_BOND",
      creation: { name: "Tesouro", rate: 12.5, maturity_date: "2031-01-01" },
    });
  });

  it("builds a floating rate bond payload with its index", () => {
    const form = {
      ...emptyFixedIncomeForm("FLOATING_BOND"),
      name: "Tesouro IPCA+",
      rate: "6",
      index: "IPCA" as const,
      maturityDate: "2035-05-15",
    };

    expect(toProductInput(form)).toEqual({
      kind: "FLOATING_BOND",
      creation: { name: "Tesouro IPCA+", rate: 6, maturity_date: "2035-05-15", index_id: "IPCA" },
    });
  });

  it("builds an account payload with its index and duration", () => {
    const form = {
      ...emptyFixedIncomeForm("ACCOUNT"),
      name: "CDB 110% CDI",
      rate: "110",
      index: "CDI" as const,
      maturityDate: "",
      maturityDuration: "P3Y",
    };

    expect(toProductInput(form)).toEqual({
      kind: "ACCOUNT",
      creation: { name: "CDB 110% CDI", rate: 110, index_id: "CDI", maturity_duration: "P3Y" },
    });
  });

  it("returns null while the name, rate or maturity is missing", () => {
    expect(toProductInput(emptyFixedIncomeForm("FIXED_BOND"))).toBeNull();
    expect(
      toProductInput({ ...emptyFixedIncomeForm("FIXED_BOND"), name: "X", rate: "12.5" }),
    ).toBeNull();
    expect(
      toProductInput({
        ...emptyFixedIncomeForm("FIXED_BOND"),
        name: "  ",
        rate: "12.5",
        maturityDate: "2031-01-01",
      }),
    ).toBeNull();
  });

  it("returns null for a non-positive or non-numeric rate", () => {
    const complete = {
      ...emptyFixedIncomeForm("FIXED_BOND"),
      name: "X",
      maturityDate: "2031-01-01",
    };

    expect(toProductInput({ ...complete, rate: "0" })).toBeNull();
    expect(toProductInput({ ...complete, rate: "-1" })).toBeNull();
    expect(toProductInput({ ...complete, rate: "abc" })).toBeNull();
  });

  it("returns null for a floating product left without an index", () => {
    const form = {
      ...emptyFixedIncomeForm("FLOATING_BOND"),
      name: "X",
      rate: "6",
      index: "PRE" as const,
      maturityDate: "2035-05-15",
    };

    expect(toProductInput(form)).toBeNull();
  });
});

describe("withKind", () => {
  it("resets a fixed bond's index to PRÉ", () => {
    const form = { ...emptyFixedIncomeForm("FLOATING_BOND"), index: "IPCA" as const };

    expect(withKind(form, "FIXED_BOND").index).toBe("PRE");
  });

  it("gives a floating product a real index when it arrives with PRÉ", () => {
    const form = emptyFixedIncomeForm("FIXED_BOND");

    expect(withKind(form, "ACCOUNT").index).toBe("CDI");
    expect(withKind(form, "FLOATING_BOND").index).toBe("CDI");
  });

  it("keeps a chosen index when the target kind allows it", () => {
    const form = { ...emptyFixedIncomeForm("FLOATING_BOND"), index: "SELIC" as const };

    expect(withKind(form, "ACCOUNT").index).toBe("SELIC");
  });

  it("keeps the other fields", () => {
    const form = { ...emptyFixedIncomeForm("FIXED_BOND"), name: "Tesouro", rate: "12.5" };

    expect(withKind(form, "ACCOUNT")).toMatchObject({
      name: "Tesouro",
      rate: "12.5",
      kind: "ACCOUNT",
    });
  });
});
