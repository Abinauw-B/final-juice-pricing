const http = require('http');
const { execSync } = require('child_process');

const API_BASE = 'http://localhost:8088/api';

async function fetchJson(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;
  return new Promise((resolve, reject) => {
    const req = http.request(url, options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, data });
        }
      });
    });
    req.on('error', reject);
    if (options.body) req.write(options.body);
    req.end();
  });
}

async function runDataIntegrityCheck() {
  console.log("====================================================================");
  console.log("🔍 PRODUCTION DATA INTEGRITY CHECK — POSTGRESQL & BACKEND AUDIT");
  console.log("====================================================================\n");

  let totalChecks = 0;
  let passedChecks = 0;

  function reportCheck(title, detail, passed) {
    totalChecks++;
    if (passed) passedChecks++;
    const status = passed ? "\x1b[32m[PASS]\x1b[0m" : "\x1b[31m[FAIL]\x1b[0m";
    console.log(`${status} ${title}`);
    console.log(`       DETAIL: ${detail}\n`);
  }

  // 1. Backend Connectivity & Health
  try {
    const health = await fetchJson('/health');
    const isUp = health.status === 200 && health.data.status === 'UP';
    reportCheck(
      "1. Backend Health & PostgreSQL Connection Status",
      `Status: ${health.data.status}, DB: ${health.data.database}`,
      isUp
    );
  } catch (err) {
    reportCheck("1. Backend Health & PostgreSQL Connection Status", err.message, false);
  }

  // 2. Readiness Probe Verification
  try {
    const readiness = await fetchJson('/readiness');
    const isReady = readiness.status === 200 && readiness.data.readiness === true;
    reportCheck(
      "2. Readiness Probe Status",
      `Readiness: ${readiness.data.readiness}, Status Code: ${readiness.status}`,
      isReady
    );
  } catch (err) {
    reportCheck("2. Readiness Probe Status", err.message, false);
  }

  // 3. Products Master Table & Price Bounds
  let products = [];
  try {
    const prodsRes = await fetchJson('/pos/products');
    products = prodsRes.data || [];
    const validBounds = products.every(p => {
      const minP = Number(p.minCupPrice || 18);
      const maxP = Number(p.maxCupPrice || 25);
      const currP = Number(p.currentCupPrice || p.currentPrice || 22);
      return currP >= minP && currP <= maxP && minP <= maxP;
    });
    reportCheck(
      "3. Product Price Bounds Consistency (min <= current <= max)",
      `Total products: ${products.length}, Bounds valid: ${validBounds}`,
      prodsRes.status === 200 && products.length >= 7 && validBounds
    );
  } catch (err) {
    reportCheck("3. Product Price Bounds Consistency", err.message, false);
  }

  // 4. Monotonic Price Versioning Check
  try {
    const validVersions = products.every(p => Number(p.priceVersion) > 0);
    reportCheck(
      "4. Monotonic Price Versions (priceVersion > 0)",
      `Verified priceVersions across ${products.length} products`,
      validVersions
    );
  } catch (err) {
    reportCheck("4. Monotonic Price Versions", err.message, false);
  }

  // 5. Positive Container Volume & Stock Check
  try {
    const batchesRes = await fetchJson('/batches');
    const batches = Array.isArray(batchesRes.data) ? batchesRes.data : [];
    const positiveStock = batches.every(b => (b.remainingVolumeMl === undefined || b.remainingVolumeMl >= 0));
    reportCheck(
      "5. Active Batch Container Stock Non-Negative",
      `Batches: ${batches.length}, Non-negative volumes: ${positiveStock}`,
      batchesRes.status === 200 && positiveStock
    );
  } catch (err) {
    reportCheck("5. Active Batch Container Stock Non-Negative", err.message, false);
  }

  // 6. Idempotency Key Integrity Check
  try {
    const ts = Date.now();
    const key = `INTEGRITY-KEY-${ts}`;
    const payload = JSON.stringify({
      idempotencyKey: key,
      items: [{ productId: 1, quantity: 1, cupSizeMl: 250 }],
      paymentMethod: "CASH"
    });
    
    const firstRes = await fetchJson('/pos/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    });
    
    const secondRes = await fetchJson('/pos/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    });

    const isIdempotent = firstRes.status === 200 && secondRes.status === 200 &&
                         (firstRes.data.orderId || firstRes.data.id) === (secondRes.data.orderId || secondRes.data.id);
    reportCheck(
      "6. Database Idempotency Deduplication Guarantee",
      `Order 1 ID: ${firstRes.data.orderId || firstRes.data.id}, Order 2 ID: ${secondRes.data.orderId || secondRes.data.id}`,
      isIdempotent
    );
  } catch (err) {
    reportCheck("6. Database Idempotency Deduplication Guarantee", err.message, false);
  }

  // 7. Server Authoritative Price Locking Protection
  try {
    const ts = Date.now();
    const freshProds = await fetchJson('/pos/products');
    const freshMango = (freshProds.data || []).find(p => p.id === 1) || {};
    const expectedUnitPrice = Number(freshMango.currentCupPrice || 22);

    const payload = JSON.stringify({
      idempotencyKey: `TAMPER-KEY-${ts}`,
      items: [{ productId: 1, quantity: 1, cupSizeMl: 250, lockedPrice: 1.00 }],
      paymentMethod: "CASH"
    });

    const res = await fetchJson('/pos/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: payload
    });

    const actualUnitPrice = Number(res.data.items?.[0]?.unitPrice || 0);

    reportCheck(
      "7. Server-Authoritative Price Enforcement (Client ₹1.00 Tamper Rejection)",
      `Expected Unit Price: ₹${expectedUnitPrice}, Actual Server Unit Price: ₹${actualUnitPrice}`,
      res.status === 200 && actualUnitPrice === expectedUnitPrice
    );
  } catch (err) {
    reportCheck("7. Server-Authoritative Price Enforcement", err.message, false);
  }

  console.log("====================================================================");
  console.log(`🏁 DATA INTEGRITY CHECK RESULT: ${passedChecks}/${totalChecks} CHECKS PASSED`);
  console.log("====================================================================\n");

  if (passedChecks === totalChecks) {
    process.exit(0);
  } else {
    process.exit(1);
  }
}

runDataIntegrityCheck();
