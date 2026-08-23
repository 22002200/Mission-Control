import type { CreateClientConfig } from './generated/client.gen';

/**
 * Runtime configuration for the generated API client.
 *
 * An empty baseUrl keeps every request same-origin and relative, so it flows through
 * the Vite dev-server proxy in development and the nginx proxy in production. The
 * generated paths already carry the `/api` prefix.
 */
export const createClientConfig: CreateClientConfig = (config) => ({
  ...config,
  baseUrl: '',
});
