import { afterEach, describe, expect, it, vi } from "vitest";
import goldenError from "../../../contracts/error-response.json";
import { ApiError, api } from "./client.ts";

// Shape of the error body the backend serialises for domain exceptions. The backend's
// DomainExceptionContractTest asserts the API serialises exactly the committed golden fixture;
// this is the compile-time half: if the fixture stops matching this type, `tsc` fails here.
// (The full hand-mirrored types.ts contract is gone with the frontend reset — the scaffold
// client only surfaces `status` + `message` on ApiError.)
interface GoldenErrorBody {
  error: string;
  message: string;
  detail: string;
}

const contract: GoldenErrorBody = goldenError;

describe("error response contract", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps the golden fixture aligned with the error body shape", () => {
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

    const thrown = await api.getHealth().then(
      () => {
        throw new Error("expected the request to reject");
      },
      (error: unknown) => error,
    );

    expect(thrown).toBeInstanceOf(ApiError);
    expect(thrown).toMatchObject({
      status: 404,
      message: contract.detail,
    });
  });
});
