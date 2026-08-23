import { defineConfig } from '@hey-api/openapi-ts';

/**
 * Generates the typed API client from the backend's live OpenAPI document.
 *
 * Run `npm run generate:api` with the backend up. The output is committed, so the
 * frontend builds without a running backend - and a backend contract change shows
 * up as a TypeScript error at every affected call site rather than a runtime 404.
 */
export default defineConfig({
  input: process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs',
  output: {
    path: 'src/api/generated',
    postProcess: ['prettier'],
  },
  plugins: [
    {
      name: '@hey-api/client-fetch',
      runtimeConfigPath: './src/api/runtimeConfig.ts',
    },
    '@tanstack/react-query',
  ],
});
