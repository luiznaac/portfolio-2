import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// The app is served under `/portfolio/` behind the unified dashboard reverse proxy,
// but runs at `/` in local dev and in the combined Docker image. Override with VITE_BASE.
export default defineConfig(({ mode }) => ({
  base: process.env.VITE_BASE ?? (mode === "production" ? "/portfolio/" : "/"),
  plugins: [react(), tailwindcss()],
  server: {
    port: 5273,
    proxy: {
      // Dev-only: forward API calls to the portfolio backend to dodge CORS.
      "/api": {
        target: process.env.VITE_API_TARGET ?? "http://localhost:8080",
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ""),
      },
    },
  },
}));
