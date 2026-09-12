import { defineConfig } from "vitest/config";

// Only src/lib/** is under test — pure logic, no React rendering. Component/render tests are not
// set up here yet; see AGENTS.md.
export default defineConfig({
  test: {
    include: ["src/lib/**/*.test.ts"],
    environment: "node",
  },
});
