  // vite.config.js
  import { defineConfig } from "vite";
  import react from "@vitejs/plugin-react";
  
  export default defineConfig({
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        // Any request starting with /api is forwarded to Tomcat
        "/api": {
          target: "http://localhost:8080",
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, "/api"),
        },
      },
    },
    build: {
      // On Vercel, use dist. Locally, keep Java webapp output for Tomcat.
      outDir: process.env.VERCEL ? "dist" : "../src/main/webapp/react-dist",
      emptyOutDir: true,
    },
  });
