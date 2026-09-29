// Production environment.
// Replace the placeholder URLs below with your deployed backend's real
// origin before shipping a production build. If the backend origin isn't
// known until deploy time (e.g. it's injected by your host), swap these
// for a runtime-config fetch instead of a build-time constant.
export const environment = {
  production: true,
  httpBase: 'https://your-production-domain.example',
  wsUrl: 'https://your-production-domain.example/ws',
  connectTimeoutMs: 3000,
};
