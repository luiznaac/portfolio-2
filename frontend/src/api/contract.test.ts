import { afterEach, describe, expect, it, vi } from "vitest";
import goldenError from "../../../contracts/error-response.json";
import { ApiError, api } from "./client.ts";
import type { ApiErrorResponse } from "./types.ts";

// Compile-time half of the contract: if the committed fixture stops matching the mirrored type,
// `tsc` fails here. The backend's DomainExceptionContractTest asserts the other half (that the
// API serialises exactly this body). See the plan's Fase 4.
const contract: ApiErrorResponse = goldenError;

describe("error response contract", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps the golden fixture aligned with ApiErrorResponse", () => {
    expect(Object.keys(contract).sort()).toEqual(["detail", "error", "message"]);
  });

  it("maps the golden body onto ApiError when a request fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(contract), {
          status: 404,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    const thrown = await api.listStrategies().then(
      () => {
        throw new Error("expected the request to reject");
      },
      (error: unknown) => error,
    );

    expect(thrown).toBeInstanceOf(ApiError);
    expect(thrown).toMatchObject({
      status: 404,
      code: contract.error,
      detail: contract.detail,
      message: contract.message,
    });
  });
});
