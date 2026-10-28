import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// The health dashboard's read of the mirrored DTO (issue #206). The DTO's own wire shape is covered
// for the whole mirror by dtoMirrorGuard.test.ts (issue #216); this file keeps the check that the
// dashboard reads the mirrored field rather than a drifted local name. Like theme.test.ts it reads a
// source file, so it is a build-time invariant guard, not component-render coverage (see AGENTS.md
// on the testing scope).

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..");
const read = (relative: string) => readFileSync(join(SRC, relative), "utf8");

const dashboard = read("pages/Dashboard.tsx");

describe("health dashboard", () => {
  it("renders the mirrored service_name as the row label", () => {
    expect(dashboard).toContain("check.service_name");
  });

  it("keys each row by the mirrored service_name", () => {
    expect(dashboard).toMatch(/key=\{check\.service_name\}/);
  });
});
