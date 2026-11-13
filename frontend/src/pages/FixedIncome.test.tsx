// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Bond, BondPosition, CheckingAccount } from "../api/types.ts";
import { FixedIncome } from "./FixedIncome.tsx";

const mock = vi.hoisted(() => ({
  bonds: [] as Bond[],
  accounts: [] as CheckingAccount[],
  bondPositions: new Map<number, BondPosition[]>(),
  accountPositions: new Map<number, BondPosition[]>(),
}));

vi.mock("../api/client.ts", () => ({
  api: {
    getBonds: () => Promise.resolve(mock.bonds),
    getCheckingAccounts: () => Promise.resolve(mock.accounts),
    getBondPositions: (bondId: number) => Promise.resolve(mock.bondPositions.get(bondId) ?? []),
    getCheckingAccountPositions: (accountId: number) =>
      Promise.resolve(mock.accountPositions.get(accountId) ?? []),
    createFixedRateBond: (creation: { name: string; rate: number; maturity_date: string }) => {
      const created: Bond = {
        id: 99,
        name: creation.name,
        rate: creation.rate,
        maturity_date: creation.maturity_date,
        sub_class: "FIXED_RATE",
      };
      mock.bonds = [...mock.bonds, created];
      return Promise.resolve(created);
    },
    createFloatingRateBond: vi.fn(),
    createCheckingAccount: (creation: {
      name: string;
      rate: number;
      index_id: "CDI" | "SELIC" | "IPCA";
      maturity_duration: string;
    }) => {
      const created: CheckingAccount = {
        id: 98,
        ...creation,
        sub_class: "FLOATING_RATE",
      };
      mock.accounts = [...mock.accounts, created];
      return Promise.resolve(created);
    },
  },
}));

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

function position(overrides: Partial<BondPosition>): BondPosition {
  return { date: "2026-11-01", principal: 1000, yield: 20, taxes: 5, ...overrides };
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/ativos/fixed"]}>
        <FixedIncome />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function rowOf(name: string): HTMLElement {
  const row = screen.getByText(name).closest("tr");
  if (row === null) throw new Error(`no row for ${name}`);
  return row;
}

function cellsOf(row: HTMLElement): string[] {
  return within(row)
    .getAllByRole("cell")
    .map((cell) => cell.textContent ?? "");
}

afterEach(() => {
  cleanup();
  mock.bonds = [];
  mock.accounts = [];
  mock.bondPositions = new Map();
  mock.accountPositions = new Map();
});

describe("Ativos · Renda fixa", () => {
  it("lists each product with its indexer and value, and totals the priced products", async () => {
    mock.bonds = [prefixado];
    mock.accounts = [cdb];
    mock.bondPositions.set(1, [position({ principal: 1000, yield: 20 })]);
    mock.accountPositions.set(3, [position({ principal: 500, yield: 5 })]);
    renderPage();

    await screen.findByText("Tesouro Prefixado 2031");
    expect(cellsOf(rowOf("Tesouro Prefixado 2031"))).toEqual([
      "Tesouro Prefixado 2031",
      "Título",
      "PRÉ",
      "Pré-fixado",
      "12,5%",
      "2031-01-01",
      "R$ 1.020,00",
    ]);

    await screen.findByText("CDB 110% CDI");
    expect(cellsOf(rowOf("CDB 110% CDI"))).toEqual([
      "CDB 110% CDI",
      "Conta",
      "CDI",
      "Pós-fixado",
      "110%",
      "3 anos",
      "R$ 505,00",
    ]);

    expect((await screen.findByTestId("fixed-income-total-value")).textContent).toBe("R$ 1.525,00");
    expect(screen.getByTestId("fixed-income-total").textContent).not.toContain("parcial");
  });

  it("marks the total partial while a product has no consolidated position", async () => {
    mock.bonds = [prefixado, ipcaPlus];
    mock.bondPositions.set(1, [position({ principal: 1000, yield: 20 })]);
    renderPage();

    await screen.findByText("Tesouro Prefixado 2031");
    await screen.findByText("Tesouro IPCA+ 2035");

    expect(rowOf("Tesouro Prefixado 2031").textContent).toContain("R$ 1.020,00");
    expect(rowOf("Tesouro IPCA+ 2035").textContent).toContain("—");
    expect(screen.getByTestId("fixed-income-total").textContent).toContain("parcial");
    expect(screen.getByTestId("fixed-income-total-value").textContent).toBe("R$ 1.020,00");
  });

  it("creates a checking account and lists it without a reload", async () => {
    renderPage();

    await screen.findByText("Nenhum produto cadastrado.");
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText("Tipo"), "ACCOUNT");
    await user.type(screen.getByLabelText("Nome"), "Conta Corrente");
    await user.type(screen.getByLabelText("Taxa (%)"), "100");
    await user.click(screen.getByRole("button", { name: "Criar produto" }));

    expect(await screen.findByText("Conta Corrente")).toBeDefined();
    expect(screen.queryByText("Nenhum produto cadastrado.")).toBeNull();
  });

  it("creates a product and lists it without a reload", async () => {
    renderPage();

    await screen.findByText("Nenhum produto cadastrado.");
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Nome"), "Tesouro Selic 2031");
    await user.type(screen.getByLabelText("Taxa (%)"), "13.5");
    await user.type(screen.getByLabelText("Vencimento"), "2031-03-01");
    await user.click(screen.getByRole("button", { name: "Criar produto" }));

    expect(await screen.findByText("Tesouro Selic 2031")).toBeDefined();
    expect(screen.queryByText("Nenhum produto cadastrado.")).toBeNull();
  });

  it("offers no import-assistant entry point", async () => {
    mock.bonds = [prefixado];
    renderPage();

    await screen.findByText("Tesouro Prefixado 2031");

    const assistantLinks = screen
      .queryAllByRole("link")
      .filter((link) => link.getAttribute("href")?.startsWith("/imports/assistant"));
    expect(assistantLinks).toEqual([]);
  });
});
