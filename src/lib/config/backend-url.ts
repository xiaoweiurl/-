// Re-export from backend-proxy for backward compatibility
// This file exists to resolve module references from HMR cache
export { getBackendStaticUrl, getBackendUrl } from '../backend-proxy';

// Fixed static URL for image resources
export function getStaticUrl(): string {
  return 'http://localhost:8080';
}

export function getApiBaseUrl(): string {
  return 'http://localhost:8080/api';
}
