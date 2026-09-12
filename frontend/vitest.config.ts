import { defineConfig } from "vitest/config";

// src/lib/** (pure logic) and src/api/** (the hand-mirrored HTTP client) are under test — no React
// rendering. Component/render tests are not set up here yet; see AGENTS.md.
export default defineConfig({
  test: {
    include: ["src/lib/**/*.test.ts", "src/api/**/*.test.ts"],
    environment: "node",
  },
});
