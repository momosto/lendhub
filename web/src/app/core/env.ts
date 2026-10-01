declare global {
  interface Window { __env?: { apiUrl?: string }; }
}

/** API base URL from public/env.js, written at container start so one image serves every environment. */
export function apiUrl(): string {
  return (window.__env?.apiUrl ?? 'http://localhost:8080').replace(/\/$/, '');
}
