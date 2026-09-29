// Default (development) environment.
// This file is swapped for environment.prod.ts during a production build
// via the "fileReplacements" entry in angular.json — see the "production"
// configuration for /src/environments/environment.ts.
export const environment = {
  production: false,
  httpBase: 'http://localhost:8080',
  wsUrl: 'http://localhost:8080/ws',
  connectTimeoutMs: 3000,
};
