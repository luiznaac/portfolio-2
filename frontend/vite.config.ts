import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";

// The app is served under `/portfolio/` behind the unified dashboard reverse proxy,
// but runs at `/` in local dev and in the combined Docker image. Override with VITE_BASE.
//
// `loadEnv(mode, cwd, "")` is what makes `.env` reach this file: Vite does not inject `.env` into
// `process.env`, so reading it directly silently ignored the file.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");

  return {
    base: env.VITE_BASE ?? (mode === "production" ? "/portfolio/" : "/"),
    plugins: [react(), tailwindcss()],
    server: {
      // portfolio-2's assigned frontend-dev role. Strict on purpose: a taken port fails loudly
      // instead of auto-incrementing, so the served URL cannot drift from the block.
      port: Number(env.FRONTEND_DEV_PORT ?? 8113),
      strictPort: true,
      proxy: {
        // Dev-only: forward API calls to portfolio-2's assigned api role to dodge CORS.
        "/api": {
          target: env.VITE_API_TARGET ?? "http://localhost:8110",
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/api/, ""),
        },
      },
    },
  };
});
