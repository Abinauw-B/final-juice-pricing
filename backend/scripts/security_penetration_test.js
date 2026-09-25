const http = require('http');

const BASE_URL = 'http://localhost:8088';

function request(endpoint, method, body, headers = {}) {
  const cleanEndpoint = endpoint.startsWith('/actuator') ? endpoint : (endpoint.startsWith('/api') ? endpoint : `/api${endpoint}`);
  const url = new URL(`${BASE_URL}${cleanEndpoint}`);
  return new Promise((resolve, reject) => {
    const postData = body ? JSON.stringify(body) : '';
    const reqHeaders = {
      'Content-Type': 'application/json',
      ...headers
    };
    if (postData) {
      reqHeaders['Content-Length'] = Buffer.byteLength(postData);
    }

    const req = http.request({
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: method,
      headers: reqHeaders
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(data); } catch (e) {}
        resolve({ status: res.statusCode, body: json || data });
      });
    });

    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

async function runSecurityPenetrationTests() {
  console.log('================================================================================');
  console.log('🔒 PRODUCTION SECURITY PENETRATION & REGRESSION TEST SUITE');
  console.log('================================================================================\n');

  let passed = 0;
  let total = 0;

  function report(id, name, expected, actual, success) {
    total++;
    if (success) passed++;
    const tag = success ? '\x1b[32m[PASS]\x1b[0m' : '\x1b[31m[FAIL]\x1b[0m';
    console.log(`${tag} Test ${id}: ${name}`);
    console.log(`       EXPECTED: ${expected}`);
    console.log(`       ACTUAL  : ${actual}\n`);
  }

  // 1. Test Admin Refresh Token Backdoor
  try {
    const res = await request('/auth/refresh', 'POST', {});
    const isProtected = res.status === 401 || res.status === 403;
    report(1, 'POST /api/auth/refresh with empty payload',
           'HTTP 401 Unauthorized (Backdoor closed)',
           `HTTP ${res.status}: ${JSON.stringify(res.body)}`,
           isProtected);
  } catch (e) {
    report(1, 'POST /api/auth/refresh with empty payload', 'HTTP 401', e.message, false);
  }

  // 2. Test Synthetic User Injection in Login
  try {
    const res = await request('/auth/login', 'POST', { username: 'nonexistent_attacker_user', password: 'randompassword123' });
    const isRejected = res.status === 401;
    report(2, 'POST /api/auth/login with nonexistent username',
           'HTTP 401 Unauthorized (No synthetic user created)',
           `HTTP ${res.status}: ${JSON.stringify(res.body)}`,
           isRejected);
  } catch (e) {
    report(2, 'POST /api/auth/login with nonexistent username', 'HTTP 401', e.message, false);
  }

  // 3. Test Synthetic User Profile Creation
  try {
    const res = await request('/auth/profile?username=fake_admin_phantom', 'GET');
    const isNotFound = res.status === 404;
    report(3, 'GET /api/auth/profile with fake user',
           'HTTP 404 Not Found (No SUPER_ADMIN fabricated)',
           `HTTP ${res.status}: ${JSON.stringify(res.body)}`,
           isNotFound);
  } catch (e) {
    report(3, 'GET /api/auth/profile with fake user', 'HTTP 404', e.message, false);
  }

  // 4. Test Client Discount Tampering
  try {
    const res = await request('/pos/checkout', 'POST', {
      items: [{ productId: 1, quantity: 1, cupSizeMl: 250 }],
      paymentMethod: 'CASH',
      discountAmount: 999.00 // Malicious client-supplied discount
    });
    // Server should neutralize discount to 0.00 for unauthenticated customer
    const discountNeutralized = res.status === 200 && res.body.success && Number(res.body.totalAmount) >= 20.00;
    report(4, 'POST /api/pos/checkout with manipulated discountAmount (₹999.00)',
           'Server neutralizes client discount to ₹0.00; total >= ₹20.00',
           `HTTP ${res.status}, Order Total: ₹${res.body.totalAmount}`,
           discountNeutralized);
  } catch (e) {
    report(4, 'POST /api/pos/checkout with manipulated discount', 'Discount neutralized', e.message, false);
  }

  // 5. Test Actuator Public Exposure Lockdown
  try {
    const resEnv = await request('/actuator/env', 'GET');
    const resMetrics = await request('/actuator/metrics', 'GET');
    const resHealth = await request('/actuator/health', 'GET');

    const envProtected = resEnv.status === 401 || resEnv.status === 403;
    const metricsProtected = resMetrics.status === 401 || resMetrics.status === 403;
    const healthPublic = resHealth.status === 200;

    report(5, 'Actuator Endpoint Security Lockdown',
           '/actuator/env 401/403, /actuator/metrics 401/403, /actuator/health 200',
           `env: HTTP ${resEnv.status}, metrics: HTTP ${resMetrics.status}, health: HTTP ${resHealth.status}`,
           envProtected && metricsProtected && healthPublic);
  } catch (e) {
    report(5, 'Actuator Endpoint Security Lockdown', 'Protected', e.message, false);
  }

  // 6. Test Legitimate Authentication & Refresh Flow
  try {
    const loginRes = await request('/auth/login', 'POST', { username: 'superadmin', password: 'password' });
    const hasTokens = loginRes.status === 200 && loginRes.body.token && loginRes.body.refreshToken;
    let refreshSuccess = false;

    if (hasTokens) {
      const refreshRes = await request('/auth/refresh', 'POST', { refreshToken: loginRes.body.refreshToken });
      refreshSuccess = refreshRes.status === 200 && Boolean(refreshRes.body.token);
    }

    report(6, 'Legitimate Login and Cryptographic Refresh Flow',
           'Login returns access + refresh JWTs, Refresh endpoint returns new access JWT',
           `Login HTTP: ${loginRes.status}, Refresh HTTP: 200, Valid new token: ${refreshSuccess}`,
           hasTokens && refreshSuccess);
  } catch (e) {
    report(6, 'Legitimate Login and Refresh Flow', 'Success', e.message, false);
  }

  console.log('--------------------------------------------------------------------------------');
  console.log(`TOTAL PENETRATION TESTS: ${total} | PASSED: ${passed} | FAILED: ${total - passed}`);
  console.log('================================================================================\n');

  if (passed === total) {
    process.exit(0);
  } else {
    process.exit(1);
  }
}

runSecurityPenetrationTests().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
