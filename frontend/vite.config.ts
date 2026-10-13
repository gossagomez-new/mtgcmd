import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": `http://127.0.0.1:${process.env.API_PORT || "8080"}`,
      "/ws": {
        target: `ws://127.0.0.1:${process.env.API_PORT || "8080"}`,
        ws: true,
      },
      "/__test": "http://127.0.0.1:8081",
    },
  },
});
