import type {
  Bond,
  CheckingAccount,
  CheckingAccountCreation,
  FixedRateBondCreation,
  FloatingRateBondCreation,
  HealthCheckResult,
  Import,
  ImportRow,
  ImportSource,
  PositionDetail,
  Quote,
  Trade,
} from "./types.ts";

const BASE = (import.meta.env.VITE_API_BASE ?? "/api").replace(/\/$/, "");

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let detail = res.statusText;
    try {
      const body = await res.json();
      detail = body.detail ?? detail;
    } catch {
      /* non-JSON body */
    }
    throw new ApiError(res.status, detail);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  return unwrap<T>(
    await fetch(`${BASE}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    }),
  );
}

// Multipart uploads must not set Content-Type: the browser writes the multipart boundary itself.
async function upload<T>(path: string, form: FormData): Promise<T> {
  return unwrap<T>(await fetch(`${BASE}${path}`, { method: "POST", body: form }));
}

export const api = {
  getHealth(): Promise<HealthCheckResult[]> {
    return request("/health");
  },

  stageImport(source: ImportSource, file: File): Promise<Import> {
    const form = new FormData();
    form.append("source", source);
    form.append("file", file);
    return upload("/imports", form);
  },

  getImportRows(importId: number): Promise<ImportRow[]> {
    return request(`/imports/${importId}/rows`);
  },

  reviewImport(importId: number): Promise<Import> {
    return request(`/imports/${importId}/review`, { method: "POST" });
  },

  applyImport(importId: number): Promise<Import> {
    return request(`/imports/${importId}/apply`, { method: "POST" });
  },

  getTrades(): Promise<Trade[]> {
    return request("/trades");
  },

  getPosition(ticker: string): Promise<PositionDetail> {
    return request(`/positions/${encodeURIComponent(ticker)}`);
  },

  getTickerQuotes(ticker: string): Promise<Quote[]> {
    return request(`/tickers/${encodeURIComponent(ticker)}/quotes`);
  },

  getBonds(): Promise<Bond[]> {
    return request("/fixed-income/bonds");
  },

  getCheckingAccounts(): Promise<CheckingAccount[]> {
    return request("/fixed-income/accounts");
  },

  createFixedRateBond(creation: FixedRateBondCreation): Promise<Bond> {
    return request("/fixed-income/bonds/fixed", {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },

  createFloatingRateBond(creation: FloatingRateBondCreation): Promise<Bond> {
    return request("/fixed-income/bonds/floating", {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },

  createCheckingAccount(creation: CheckingAccountCreation): Promise<CheckingAccount> {
    return request("/fixed-income/accounts", {
      method: "POST",
      body: JSON.stringify(creation),
    });
  },
};
