import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Pure helpers run in node; a component test opts into jsdom per file with a
// `// @vitest-environment jsdom` docblock. See AGENTS.md.
export default defineConfig({
  plugins: [react()],
  test: {
    include: ["src/lib/**/*.test.ts", "src/**/*.test.tsx"],
    environment: "node",
  },
});
