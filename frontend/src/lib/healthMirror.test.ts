import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// Wire-contract guard from issue #206, covering the frontend side of the health DTO contract: the
// hand-maintained mirror in api/types.ts must declare the shape the backend serialises (Jackson
// SNAKE_CASE over HealthCheckResult: serviceName -> service_name, isHealthy -> is_healthy,
// timestamp -> timestamp), and the dashboard must read the mirrored field rather than a drifted
// `name`. Like theme.test.ts this reads source files, so it is a build-time invariant guard, not
// component-render coverage (see AGENTS.md on the testing scope); the backend half of the contract
// is authoritative in backend/usecase/.../health/HealthCheckResult.kt and is not read here.

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (relative: string) => readFileSync(join(SRC, relative), "utf8");

const types = read("api/types.ts");
const dashboard = read("pages/Dashboard.tsx");

function interfaceBody(source: string, name: string): string | null {
  const marker = `interface ${name} {`;
  const start = source.indexOf(marker);
  if (start < 0) return null;
  const rest = source.slice(start + marker.length);
  return rest.slice(0, rest.indexOf("}"));
}

describe("HealthCheckResult mirror", () => {
  const body = interfaceBody(types, "HealthCheckResult");

  it("is declared in the DTO mirror", () => {
    expect(body).not.toBeNull();
  });

  it.each(["service_name: string", "is_healthy: boolean", "timestamp: string"])(
    "declares %s to match the backend edge DTO",
    (field) => {
      expect(body).toContain(field);
    },
  );

  it("does not declare the drifted `name` field the backend never sends", () => {
    expect(body).not.toMatch(/\bname:\s*string/);
  });
});

describe("health dashboard", () => {
  it("renders the mirrored service_name as the row label", () => {
    expect(dashboard).toContain("check.service_name");
  });

  it("keys each row by the mirrored service_name", () => {
    expect(dashboard).toMatch(/key=\{check\.service_name\}/);
  });
});
