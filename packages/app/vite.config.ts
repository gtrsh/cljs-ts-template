import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    fs: {
      // разрешаем dev-серверу отдавать файлы из соседнего пакета через симлинк
      allow: [".."],
    },
  },
  optimizeDeps: {
    // shadow-cljs выдаёт пачку мелких чанков в dist/cljs_runtime/*.
    // Vite 8 использует Rolldown для pre-bundle, и на этих чанках он давится;
    // отключаем — чанки уходят нативно как ESM.
    exclude: ["@myapp/core"],
  },
});
