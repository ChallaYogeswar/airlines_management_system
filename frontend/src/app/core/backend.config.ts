import { environment } from '../../environments/environment';

declare global {
  interface Window {
    __AMS_CONFIG__?: {
      httpBase?: string;
      wsUrl?: string;
    };
  }
}

const runtime = typeof window !== 'undefined' ? window.__AMS_CONFIG__ : undefined;

const normalizeOrigin = (value: string): string => value.replace(/\/$/, '');

const httpBase = normalizeOrigin(runtime?.httpBase || environment.httpBase);
const wsUrl = runtime?.wsUrl ? normalizeOrigin(runtime.wsUrl) : normalizeOrigin(environment.wsUrl);

export const backendConfig = {
  httpBase,
  wsUrl,
  connectTimeoutMs: environment.connectTimeoutMs,
};
