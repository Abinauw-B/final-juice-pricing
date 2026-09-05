/**
 * Noida Pub Exchange - Centralized Frontend API & WebSocket Configuration
 * Customer POS & LED Display Application
 *
 * Supported Environments:
 * - Local Development: Automatically detects localhost / 127.0.0.1 -> http://localhost:8088 / ws://localhost:8088/ws
 * - Production: Resolves to configured production backend domain via HTTPS and secure WebSocket (wss://)
 * - Dynamic Override: Allows runtime backend URL override via query param (?api_url=https://...) or localStorage
 */
(function (global) {
  'use strict';

  // 1. Detect if running locally or deployed on cloud/Vercel
  const isLocalhost = Boolean(
    global.location.hostname === 'localhost' ||
    global.location.hostname === '127.0.0.1' ||
    global.location.hostname === '[::1]' ||
    global.location.hostname === '' ||
    global.location.protocol === 'file:'
  );

  // 2. Check for runtime URL overrides via query param: e.g. ?api_url=https://your-backend.railway.app
  try {
    const searchParams = new URLSearchParams(global.location.search);
    const apiParam = searchParams.get('api_url') || searchParams.get('backend_url');
    if (apiParam) {
      localStorage.setItem('pubexchange_backend_url', apiParam.trim());
    }
  } catch (e) {
    // Ignore URL/Storage permission errors
  }

  // 3. Stored backend URL in browser localStorage
  let storedBackendUrl = null;
  try {
    storedBackendUrl = localStorage.getItem('pubexchange_backend_url');
  } catch (e) {}

  // 4. Injected environment override (e.g. window.__ENV__.API_BASE_URL)
  const envApiUrl = (global.__ENV__ && global.__ENV__.API_BASE_URL) || global.BACKEND_API_URL;

  // 5. Determine base backend URL
  let rawBaseUrl = (envApiUrl || storedBackendUrl || '').trim();

  if (!rawBaseUrl) {
    if (isLocalhost) {
      rawBaseUrl = 'http://localhost:8088';
    } else {
      // Production default fallback: In Vercel, can be updated via window.__ENV__ or query param
      rawBaseUrl = (global.__ENV__ && global.__ENV__.PROD_BACKEND_URL) || 'https://YOUR-BACKEND-URL';
    }
  }

  // Normalize: strip trailing slashes
  rawBaseUrl = rawBaseUrl.replace(/\/+$/, '');

  // Calculate API base (ends with /api)
  const apiBase = rawBaseUrl.endsWith('/api') ? rawBaseUrl : `${rawBaseUrl}/api`;
  const rootUrl = rawBaseUrl.replace(/\/api$/, '');

  // 6. Centralized WebSocket URL calculation:
  // Converts http:// -> ws:// and https:// -> wss:// to prevent browser mixed-content blocks
  function deriveWebSocketUrl(baseHttpUrl) {
    const clean = (baseHttpUrl || rootUrl).replace(/\/api$/, '').replace(/\/+$/, '');
    if (clean.startsWith('https://')) {
      return clean.replace(/^https:\/\//, 'wss://') + '/ws';
    } else if (clean.startsWith('http://')) {
      return clean.replace(/^http:\/\//, 'ws://') + '/ws';
    } else if (global.location.protocol === 'https:') {
      return 'wss://' + clean.replace(/^wss?:\/\//, '') + '/ws';
    } else {
      return 'ws://' + clean.replace(/^wss?:\/\//, '') + '/ws';
    }
  }

  // SockJS endpoint URL: must match HTTP/HTTPS of the backend
  const sockJsUrl = rootUrl + '/ws';
  const rawWsUrl = (global.__ENV__ && global.__ENV__.WS_URL) || deriveWebSocketUrl(rootUrl);

  // 7. Dynamic Cross-Panel URLs
  const posUrl = isLocalhost
    ? 'http://localhost:8000'
    : ((global.__ENV__ && global.__ENV__.POS_URL) || 'https://final-juice-pricing.vercel.app');

  const adminUrl = isLocalhost
    ? 'http://localhost:8001'
    : ((global.__ENV__ && global.__ENV__.ADMIN_URL) || 'https://final-juice-pricing-admin.vercel.app');

  const configObj = Object.freeze({
    API_BASE_URL: apiBase,
    API_ROOT_URL: rootUrl,
    WS_URL: sockJsUrl,
    RAW_WS_URL: rawWsUrl,
    SOCKJS_URL: sockJsUrl,
    POS_URL: posUrl,
    ADMIN_URL: adminUrl,
    IS_LOCAL: isLocalhost,
    deriveWebSocketUrl: deriveWebSocketUrl
  });

  // Expose global constants
  global.CONFIG = configObj;
  global.APP_CONFIG = configObj;
  global.API_BASE_URL = apiBase;
  global.WS_URL = sockJsUrl;

})(typeof window !== 'undefined' ? window : this);
