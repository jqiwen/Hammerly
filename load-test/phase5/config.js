export const targetBaseUrl = (__ENV.HAMMERLY_LOAD_TEST_BASE_URL || 'http://localhost:5000')
  .replace(/\/$/, '');
export const endpoint = `${targetBaseUrl}/api/ai/support/chat/stream`;
export const mode = (__ENV.HAMMERLY_LOAD_TEST_MODE || 'smoke').toLowerCase();
export const providerMode = (__ENV.PROVIDER_MODE || '').toLowerCase();

export function assertLoadTestSafety() {
  const liveOverride = (__ENV.ALLOW_LIVE_PROVIDER_LOAD_TEST || '').toLowerCase() === 'true';
  if (mode === 'full' && providerMode !== 'loadtest' && !liveOverride) {
    throw new Error(
      'Full Phase 5 benchmark refused: set PROVIDER_MODE=loadtest. ' +
      'ALLOW_LIVE_PROVIDER_LOAD_TEST=true is the explicit paid-provider override.',
    );
  }
  if (mode === 'full' && providerMode !== 'loadtest' && liveOverride) {
    console.error('WARNING: FULL LOAD TEST IS TARGETING A NON-LOADTEST PROVIDER. COST AND QUOTA IMPACT ARE LIKELY.');
  }
}

export function requestHeaders() {
  const headers = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json',
  };
  if (__ENV.HAMMERLY_LOAD_TEST_TOKEN) {
    headers.Authorization = `Bearer ${__ENV.HAMMERLY_LOAD_TEST_TOKEN}`;
  }
  return headers;
}
