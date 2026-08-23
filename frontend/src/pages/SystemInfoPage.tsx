import { useQuery } from '@tanstack/react-query';
import { getSystemInfoOptions } from '../api/generated/@tanstack/react-query.gen';

/**
 * Renders `/api/system/info` from the backend.
 *
 * The query options, the response type and the URL all come from code generated off the
 * backend's OpenAPI document - nothing here is hand-written against the API. Change the
 * shape of `SystemInfo` in Java, re-run `npm run generate:api`, and this file stops
 * compiling until it is updated.
 */
export default function SystemInfoPage() {
  const { data, isPending, error } = useQuery(getSystemInfoOptions());

  return (
    <section className="mc-card">
      <h2>Backend status</h2>

      {isPending && <p className="mc-subtitle">Contacting backend…</p>}

      {error && (
        <p className="mc-error">
          Could not reach the backend: {error.message}. Is it running on{' '}
          <code>http://localhost:8080</code>?
        </p>
      )}

      {data && (
        <dl className="mc-facts">
          <dt>Application</dt>
          <dd>{data.name}</dd>

          <dt>Version</dt>
          <dd>{data.version}</dd>

          <dt>Active profiles</dt>
          <dd>{data.activeProfiles.length > 0 ? data.activeProfiles.join(', ') : 'none'}</dd>

          <dt>Server time</dt>
          <dd>{new Date(data.serverTime).toLocaleString()}</dd>
        </dl>
      )}
    </section>
  );
}
