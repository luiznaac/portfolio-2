import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import {
  type BackendTypeMap,
  type KotlinClass,
  backendDtoIndex,
  mirrorDrift,
  parseKotlinClasses,
  parseMirrorInterfaces,
} from "./dtoMirror";

// The DTO-mirror guard (issue #216): `frontend/src/api/types.ts` is a hand-maintained mirror of the
// DTOs `http-api/.../controller/` serialises, and the backend applies Jackson `SNAKE_CASE` to every
// property (`targetPrice` -> `target_price`). This guard reads both sides from source and fails when
// a mirrored field name drifts, in either direction, from what its backend DTO serialises.
//
// It covers *every* interface the mirror exports — not a hand-listed subset. When a mirror interface
// has no backend DTO of the same name, add it to BACKEND_TYPES below with the backend type that
// serialises it. A new DTO therefore has one obvious home for its contract check. Like theme.test.ts
// this reads source files as a build-time invariant, not component-render coverage.

const SRC = dirname(fileURLToPath(import.meta.url));
const REPO = join(SRC, "..", "..", "..");

const MIRROR = join(REPO, "frontend", "src", "api", "types.ts");

const BACKEND_ROOTS = [
  join(REPO, "backend", "usecase", "src", "main", "kotlin"),
  join(REPO, "backend", "http-api", "src", "main", "kotlin"),
];

// A mirror name whose backend DTO is named differently. Field-name mismatches are never excepted.
const BACKEND_TYPES: BackendTypeMap = {
  // The read shape is `EditionDetail`; the mirror keeps the name the UI uses.
  Edition: "EditionDetail",
  // The read shape is `AttributionSummary`; `Attribution` is the stored slice's name.
  Attribution: "AttributionSummary",
  // The blocked-close 409 body is built by DomainExceptionHandling from its private `ApiError`.
  PendingTransfersError: "ApiError",
};

function kotlinFiles(directory: string): string[] {
  return readdirSync(directory)
    .sort()
    .flatMap((entry) => {
      const path = join(directory, entry);
      if (statSync(path).isDirectory()) return kotlinFiles(path);
      return path.endsWith(".kt") ? [path] : [];
    });
}

const mirror = parseMirrorInterfaces(readFileSync(MIRROR, "utf8"));
const backend = backendDtoIndex(
  BACKEND_ROOTS.flatMap((root) =>
    kotlinFiles(root).flatMap((path): KotlinClass[] =>
      parseKotlinClasses(readFileSync(path, "utf8")),
    ),
  ),
);

describe("frontend DTO mirror", () => {
  it("is read from the whole mirror, not a hand-picked subset", () => {
    expect(mirror.length).toBeGreaterThan(100);
  });

  it.each([
    "HealthCheckResult",
    "Income",
    "Trade",
    "Bond",
    "Allocation",
    "OrderPlan",
    "CloseState",
    "PerformanceSeries",
  ])("covers the %s DTO", (name) => {
    expect(mirror.map((entry) => entry.name)).toContain(name);
  });

  it("declares exactly the wire fields its backend DTO serialises", () => {
    const drifts = mirrorDrift(mirror, backend, BACKEND_TYPES).map(
      (drift) => `${drift.type} [${drift.mirror}] ${drift.detail}`,
    );

    expect(drifts).toEqual([]);
  });
});
