import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "./client.ts";

describe("request error handling", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("preserves the backend error code and detail on ApiError", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              error: "invalid-parameter",
              message: "Invalid parameter",
              detail: "Invalid value for field transfer_id: abc",
            }),
            { status: 400, headers: { "Content-Type": "application/json" } },
          ),
      ),
    );

    const error = await api.transferSettings().catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe("invalid-parameter");
    expect(apiError.detail).toBe("Invalid value for field transfer_id: abc");
    expect(apiError.message).toBe("Invalid parameter");
  });
});
