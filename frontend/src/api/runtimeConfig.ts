import type { CreateClientConfig } from './generated/client.gen';
import { getAuthToken } from './authToken';

/**
 * Runtime configuration for the generated API client.
 *
 * An empty baseUrl keeps every request same-origin and relative, so it flows through the Vite
 * dev-server proxy in development and the nginx proxy in production. The generated paths already
 * carry the `/api` prefix.
 *
 * `auth` supplies the bearer token. The client only consults it for operations the OpenAPI
 * document marks as secured, which is why `POST /api/auth/login` never receives a stale
 * `Authorization` header - see `@SecurityRequirements` on the backend's login method.
 */
export const createClientConfig: CreateClientConfig = (config) => ({
  ...config,
  baseUrl: '',
  auth: () => getAuthToken() ?? undefined,
});
