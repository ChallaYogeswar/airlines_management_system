import { environment } from '../../environments/environment';

// Re-exported as `backendConfig` (rather than importing `environment`
// directly everywhere) so call sites don't change if more non-backend
// settings get added to the environment files later.
export const backendConfig = {
  httpBase: environment.httpBase,
  wsUrl: environment.wsUrl,
  connectTimeoutMs: environment.connectTimeoutMs,
};
