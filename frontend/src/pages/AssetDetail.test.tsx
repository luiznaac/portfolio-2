// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AssetClass, CatalogEntry } from "../api/types.ts";
import { AssetDetail } from "./AssetDetail.tsx";

const mock = vi.hoisted(() => ({
  catalogEntry: undefined as CatalogEntry | undefined,
  classified: [] as Array<[string, AssetClass]>,
  reverted: [] as string[],
}));

vi.mock("../api/client.ts", () => ({
  ApiError: class ApiError extends Error {
    constructor(readonly status: number) {
      super(`HTTP ${status}`);
    }
  },
  api: {
    getPosition: () =>
      Promise.resolve({
        position: { ticker: "ZZZZ9", quantity: 10, average_price: 5 },
        trades: [],
      }),
    getTickerQuotes: () => Promise.resolve([]),
    getTicker: () =>
      mock.catalogEntry === undefined
        ? Promise.reject(Object.assign(new Error("HTTP 404"), { status: 404 }))
        : Promise.resolve(mock.catalogEntry),
    getAttribution: () =>
      Promise.resolve({
        ticker: "ZZZZ9",
        custody_quantity: 10,
        attributed_quantity: 0,
        unattributed_quantity: 10,
        slices: [],
      }),
    getStrategies: () => Promise.resolve([]),
    setAttribution: () => Promise.resolve(null),
    removeAttribution: () => Promise.resolve(null),
    classifyTicker: (ticker: string, assetClass: AssetClass) => {
      mock.classified.push([ticker, assetClass]);
      return Promise.resolve({ ...catalogEntry(), asset_class_override: assetClass });
    },
    revertTickerClassification: (ticker: string) => {
      mock.reverted.push(ticker);
      return Promise.resolve(catalogEntry());
    },
    refreshCatalog: () => Promise.resolve({ catalog: 1, quotes: 1 }),
  },
}));

function catalogEntry(): CatalogEntry {
  return {
    ticker: "ZZZZ9",
    short_name: "ZZZZ",
    bdi_type: "??",
    asset_kind: "UNKNOWN",
    first_seen: "2026-10-01",
    last_seen: "2026-10-01",
  };
}

function renderAssetDetail() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/trades/ZZZZ9"]}>
        <Routes>
          <Route path="/trades/:ticker" element={<AssetDetail />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  mock.catalogEntry = undefined;
  mock.classified = [];
  mock.reverted = [];
});

describe("the Ativos detail classification", () => {
  it("offers classify only for an unclassified ticker and sends the picked class", async () => {
    mock.catalogEntry = catalogEntry();
    renderAssetDetail();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Classificar" }));
    await user.click(screen.getByRole("button", { name: "Ações" }));

    expect(mock.classified).toEqual([["ZZZZ9", "ACOES"]]);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("never offers classify on a ticker the catalog already attributes", async () => {
    mock.catalogEntry = { ...catalogEntry(), asset_class: "ACOES" };
    renderAssetDetail();

    await screen.findByText("Ações");
    expect(screen.queryByRole("button", { name: "Classificar" })).toBeNull();
  });

  it("keeps the manual class when the catalog disagrees and offers a revert", async () => {
    mock.catalogEntry = { ...catalogEntry(), asset_class: "FIIS", asset_class_override: "ACOES" };
    renderAssetDetail();
    const user = userEvent.setup();

    expect(
      await screen.findByText("classe manual: Ações — o catálogo agora diz FII"),
    ).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Reverter" }));

    expect(mock.reverted).toEqual(["ZZZZ9"]);
  });

  it("offers a revert even when the catalog still has no class of its own", async () => {
    mock.catalogEntry = { ...catalogEntry(), asset_class_override: "ACOES" };
    renderAssetDetail();
    const user = userEvent.setup();

    expect(await screen.findByText("classe manual: Ações")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Reverter" }));

    expect(mock.reverted).toEqual(["ZZZZ9"]);
  });
});
