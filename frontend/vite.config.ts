import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Inside Compose this points at the backend service; on the host it defaults to
// the published port. Either way the browser only ever talks to the Vite origin,
// so there is no CORS involved in the normal workflow.
const proxyTarget = process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080';

// Only needed if you run this container with a plain bind mount instead of
// `docker compose up --watch`. Watch mode syncs files into the container, which
// produces real inotify events, so polling is off by default.
const usePolling = process.env.CHOKIDAR_USEPOLLING === 'true';

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    watch: usePolling ? { usePolling: true, interval: 300 } : undefined,
    proxy: {
      '/api': { target: proxyTarget, changeOrigin: true },
      // Proxied so `npm run generate:api` can reach the spec from inside this container.
      '/v3/api-docs': { target: proxyTarget, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // Registers @testing-library/jest-dom's matchers. Without it `toBeInTheDocument()` and
    // friends are not defined.
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
