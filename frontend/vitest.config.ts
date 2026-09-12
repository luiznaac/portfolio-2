import { defineConfig } from "vitest/config";

// src/lib/** is pure logic and src/api/** is the HTTP contract — neither renders React.
// Component/render tests are not set up here yet; see AGENTS.md.
export default defineConfig({
  test: {
    include: ["src/lib/**/*.test.ts", "src/api/**/*.test.ts"],
    environment: "node",
  },
});
