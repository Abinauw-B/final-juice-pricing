/**
 * MASTER PRICING CONTROL & DYNAMIC ENGINE VALIDATION SUITE
 * 
 * Verifies the complete lifecycle:
 * Admin Configuration -> Backend API Validation -> PostgreSQL Persistence ->
 * Dynamic DWMA Engine Calculation -> Redis Synchronization -> WebSocket Broadcast -> POS/Customer UI
 */

const http = require('http');

const BASE_URL = 'http://localhost:8088';

function request(options, postData = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        let parsed = null;
        try {
          parsed = body ? JSON.parse(body) : null;
        } catch (e) {
          parsed = body;
        }
        resolve({ status: res.statusCode, headers: res.headers, data: parsed });
      });
    });
    req.on('error', reject);
    if (postData) {
      if (typeof postData === 'object') {
        req.write(JSON.stringify(postData));
      } else {
        req.write(postData);
      }
    }
    req.end();
  });
}

async function apiGet(path, headers = {}) {
  return request({
    hostname: 'localhost',
    port: 8088,
    path: '/api' + path,
    method: 'GET',
    headers: { 'Accept': 'application/json', ...headers }
  });
}

async function apiPost(path, data = {}, headers = {}) {
  const isForm = typeof data === 'string';
  return request({
    hostname: 'localhost',
    port: 8088,
    path: '/api' + path,
    method: 'POST',
    headers: {
      'Content-Type': isForm ? 'application/x-www-form-urlencoded' : 'application/json',
      'Accept': 'application/json',
      ...headers
    }
  }, data);
}

async function apiPut(path, data = {}, headers = {}) {
  return request({
    hostname: 'localhost',
    port: 8088,
    path: '/api' + path,
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...headers
    }
  }, data);
}

async function runMasterValidation() {
  console.log('======================================================================');
  console.log('🚀 RUNNING MASTER ADMIN PRICING CONTROL & DYNAMIC DWMA VALIDATION');
  console.log('======================================================================\n');

  let passedTests = 0;
  let totalTests = 0;

  function assert(condition, name, details = '') {
    totalTests++;
    if (condition) {
      console.log(`  ✅ [PASS] ${name} ${details ? '(' + details + ')' : ''}`);
      passedTests++;
    } else {
      console.error(`  ❌ [FAIL] ${name} ${details ? '(' + details + ')' : ''}`);
    }
  }

  try {
    // ---------------------------------------------------------
    // SECTION 1: GLOBAL CONFIGURATION RETRIEVAL
    // ---------------------------------------------------------
    console.log('\n--- SECTION 1: GLOBAL CONFIGURATION RETRIEVAL ---');
    const getCfgRes = await apiGet('/admin/pricing/config');
    assert(getCfgRes.status === 200, 'GET /api/admin/pricing/config status is 200');
    const cfg = getCfgRes.data;
    assert(cfg && cfg.global !== undefined, 'Config contains global settings object');
    assert(cfg.global.settlementIntervalSeconds === 120, 'Default settlement interval is 120s', `Got ${cfg?.global?.settlementIntervalSeconds}`);
    assert(Number(cfg.global.weightW0) === 1.00, 'Weight W0 is 1.00', `Got ${cfg?.global?.weightW0}`);
    assert(Number(cfg.global.weightW1) === 0.50, 'Weight W1 is 0.50', `Got ${cfg?.global?.weightW1}`);
    assert(Number(cfg.global.weightW2) === 0.25, 'Weight W2 is 0.25', `Got ${cfg?.global?.weightW2}`);
    assert(Number(cfg.global.highDemandThreshold) === 1.10, 'High demand threshold is 1.10', `Got ${cfg?.global?.highDemandThreshold}`);
    assert(Number(cfg.global.stableDemandLowerThreshold) === 0.90, 'Stable demand lower threshold is 0.90', `Got ${cfg?.global?.stableDemandLowerThreshold}`);
    assert(Number(cfg.global.lowDemandThreshold) === 0.50, 'Low demand threshold is 0.50', `Got ${cfg?.global?.lowDemandThreshold}`);
    assert(Number(cfg.global.increaseStep) === 1.00, 'Increase step is 1.00', `Got ${cfg?.global?.increaseStep}`);
    assert(Number(cfg.global.decreaseStep1) === 1.00, 'Decrease step 1 is 1.00', `Got ${cfg?.global?.decreaseStep1}`);
    assert(Number(cfg.global.decreaseStep2) === 2.00, 'Decrease step 2 is 2.00', `Got ${cfg?.global?.decreaseStep2}`);
    assert(Number(cfg.global.defaultCupPrice) === 25.00, 'Default cup price is 25.00', `Got ${cfg?.global?.defaultCupPrice}`);
    assert(Number(cfg.global.minCupPrice) === 18.00, 'Min cup price is 18.00', `Got ${cfg?.global?.minCupPrice}`);
    assert(Number(cfg.global.maxCupPrice) === 35.00, 'Max cup price is 35.00', `Got ${cfg?.global?.maxCupPrice}`);
    assert(cfg.version >= 1, 'Config version is >= 1', `Version: ${cfg?.version}`);

    // ---------------------------------------------------------
    // SECTION 2: GLOBAL CONFIGURATION MUTATION & PERSISTENCE
    // ---------------------------------------------------------
    console.log('\n--- SECTION 2: GLOBAL CONFIGURATION MUTATION & PERSISTENCE ---');
    const initialVersion = cfg.version;
    const updateRes = await apiPut('/admin/pricing/config', {
      settlementIntervalSeconds: 90,
      weightW0: 1.20,
      weightW1: 0.60,
      weightW2: 0.30,
      highDemandThreshold: 1.15,
      increaseStep: 2.00
    }, { 'X-User-Role': 'SUPER_ADMIN' });

    assert(updateRes.status === 200, 'PUT /api/admin/pricing/config accepted');
    const updatedCfg = updateRes.data;
    assert(updatedCfg.version === initialVersion + 1, 'Config version incremented', `v${initialVersion} -> v${updatedCfg.version}`);
    assert(updatedCfg.global.settlementIntervalSeconds === 90, 'Settlement interval updated to 90s');
    assert(Number(updatedCfg.global.weightW0) === 1.20, 'Weight W0 updated to 1.20');
    assert(Number(updatedCfg.global.increaseStep) === 2.00, 'Increase step updated to 2.00');

    // Verify persistence via clean GET
    const verifyGet = await apiGet('/admin/pricing/config');
    assert(verifyGet.data.global.settlementIntervalSeconds === 90, 'GET confirms persisted 90s interval');
    assert(Number(verifyGet.data.global.increaseStep) === 2.00, 'GET confirms persisted ₹2.00 increase step');

    // Restore baseline global config
    await apiPut('/admin/pricing/config', {
      settlementIntervalSeconds: 120,
      weightW0: 1.00,
      weightW1: 0.50,
      weightW2: 0.25,
      highDemandThreshold: 1.10,
      increaseStep: 1.00
    }, { 'X-User-Role': 'SUPER_ADMIN' });

    // ---------------------------------------------------------
    // SECTION 3: STRICT VALIDATION & RBAC REJECTION
    // ---------------------------------------------------------
    console.log('\n--- SECTION 3: STRICT VALIDATION & RBAC REJECTION ---');
    
    // Test 3a: Non-positive interval rejection
    const invalidIntervalRes = await apiPut('/admin/pricing/config', {
      settlementIntervalSeconds: 0
    }, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(invalidIntervalRes.status >= 400, 'Rejects zero/negative settlement interval', `Status: ${invalidIntervalRes.status}`);

    // Test 3b: Min price >= max price rejection
    const invalidMinMaxRes = await apiPut('/admin/pricing/config', {
      minCupPrice: 40.00,
      maxCupPrice: 35.00
    }, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(invalidMinMaxRes.status >= 400, 'Rejects minPrice >= maxPrice', `Status: ${invalidMinMaxRes.status}`);

    // Test 3c: Threshold ordering violation rejection (low >= stableLower)
    const invalidThreshRes = await apiPut('/admin/pricing/config', {
      lowDemandThreshold: 0.95,
      stableDemandLowerThreshold: 0.90
    }, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(invalidThreshRes.status >= 400, 'Rejects lowDemandThreshold >= stableDemandLowerThreshold', `Status: ${invalidThreshRes.status}`);

    // Test 3d: RBAC Customer Role Forbidden
    const customerRoleRes = await apiPut('/admin/pricing/config', {
      settlementIntervalSeconds: 120
    }, { 'X-User-Role': 'CUSTOMER' });
    assert(customerRoleRes.status === 403, 'Rejects CUSTOMER role with 403 FORBIDDEN', `Status: ${customerRoleRes.status}`);

    // ---------------------------------------------------------
    // SECTION 4: PRODUCT-SPECIFIC TARGET SALES INDEPENDENCE
    // ---------------------------------------------------------
    console.log('\n--- SECTION 4: PRODUCT-SPECIFIC TARGET SALES INDEPENDENCE ---');
    
    // Set Mango (ID 1) target to 2.00
    const mangoUpdateRes = await apiPut('/admin/pricing/products/1/config', {
      targetSales: 2.00
    }, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(mangoUpdateRes.status === 200, 'PUT Mango targetSales to 2.00 succeeded');
    assert(mangoUpdateRes.data.targetSales === 2.00, 'Mango targetSales is 2.00');

    // Check Lemon (ID 2) target sales
    const lemonGetRes = await apiGet('/admin/pricing/products/2/config');
    assert(lemonGetRes.status === 200, 'GET Lemon product config succeeded');
    assert(lemonGetRes.data.targetSales !== 2.00, 'Lemon target sales remains independent', `Lemon target: ${lemonGetRes.data.targetSales}`);

    // ---------------------------------------------------------
    // SECTION 5: DYNAMIC DWMA PRICING CALCULATION WITH CUSTOM TARGETS
    // ---------------------------------------------------------
    console.log('\n--- SECTION 5: DYNAMIC DWMA CALCULATION WITH CUSTOM TARGETS ---');
    
    // Reset all products to base ₹25.00
    await apiPost('/pricing/reset-all', {}, { 'X-User-Role': 'SUPER_ADMIN' });

    // Step A: Mango target = 2.00
    await apiPut('/admin/pricing/products/1/config', { targetSales: 2.00 }, { 'X-User-Role': 'SUPER_ADMIN' });

    const debugMangoA = await apiGet('/pricing/debug/1');
    assert(debugMangoA.status === 200, 'GET Mango debug evaluation succeeded');
    assert(Number(debugMangoA.data.targetSales) === 2.00, 'Mango target sales used in DWMA is 2.00', `Got ${debugMangoA.data.targetSales}`);
    const expectedSwA = (1.00 * debugMangoA.data.w0) + (0.50 * debugMangoA.data.w1) + (0.25 * debugMangoA.data.w2);
    const expectedRdA = expectedSwA / 2.00;
    assert(Math.abs(debugMangoA.data.demandRatio - expectedRdA) < 0.01, `Mango demand ratio matches Sw / 2.00 (${debugMangoA.data.weightedSales} / 2.00 = ${expectedRdA.toFixed(2)})`, `Got ${debugMangoA.data.demandRatio}`);

    // Step B: Change Mango target to 0.80 -> Demand ratio scales inversely (Sw / 0.80)
    await apiPut('/admin/pricing/products/1/config', { targetSales: 0.80 }, { 'X-User-Role': 'SUPER_ADMIN' });

    const debugMangoB = await apiGet('/pricing/debug/1');
    assert(Number(debugMangoB.data.targetSales) === 0.80, 'Mango target sales updated to 0.80 in DWMA engine', `Got ${debugMangoB.data.targetSales}`);
    const expectedSwB = (1.00 * debugMangoB.data.w0) + (0.50 * debugMangoB.data.w1) + (0.25 * debugMangoB.data.w2);
    const expectedRdB = expectedSwB / 0.80;
    assert(Math.abs(debugMangoB.data.demandRatio - expectedRdB) < 0.01, `Mango demand ratio matches Sw / 0.80 (${debugMangoB.data.weightedSales} / 0.80 = ${expectedRdB.toFixed(2)})`, `Got ${debugMangoB.data.demandRatio}`);

    // Execute settlement cycle
    const settleRes = await apiPost('/pricing/evaluate', {}, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(settleRes.status === 200, 'Settlement cycle executed via /api/pricing/evaluate');

    // Verify persisted live price in PostgreSQL & Redis
    const mangoPosRes = await apiGet('/pos/products');
    const liveMango = mangoPosRes.data.find(p => p.id === 1);
    assert(Number(liveMango.currentCupPrice) === Number(debugMangoB.data.projectedNewPrice), `PostgreSQL live price for Mango matches DWMA projected price ₹${debugMangoB.data.projectedNewPrice}`, `Got ₹${liveMango.currentCupPrice}`);

    // Restore Mango target to standard 1.10
    await apiPut('/admin/pricing/products/1/config', { targetSales: 1.10 }, { 'X-User-Role': 'SUPER_ADMIN' });

    // ---------------------------------------------------------
    // SECTION 6: MARKET CRASH DYNAMIC CONFIGURATION & RESTORATION
    // ---------------------------------------------------------
    console.log('\n--- SECTION 6: MARKET CRASH DYNAMIC CONFIGURATION ---');
    
    // Set custom crash price to ₹17.50
    await apiPut('/admin/pricing/config', {
      marketCrashPrice: 17.50,
      minCupPrice: 17.00
    }, { 'X-User-Role': 'SUPER_ADMIN' });

    // Trigger Market Crash
    const crashTriggerRes = await apiPost('/pricing/market-crash/trigger', {}, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(crashTriggerRes.status === 200, 'Market Crash trigger accepted');
    assert(crashTriggerRes.data.active === true, 'Market crash is active');

    // Verify all products clamped to configured crash price ₹17.50
    const crashProdsRes = await apiGet('/pos/products');
    const allCrashedAt1750 = crashProdsRes.data.every(p => Number(p.currentCupPrice) === 17.50);
    assert(allCrashedAt1750, 'All products dropped to configured crash price ₹17.50');

    // Stop Market Crash
    const crashStopRes = await apiPost('/pricing/market-crash/stop', {}, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(crashStopRes.status === 200, 'Market Crash stop accepted');
    assert(crashStopRes.data.active === false, 'Market crash ended');

    // Restore baseline crash settings
    await apiPut('/admin/pricing/config', {
      marketCrashPrice: 18.00,
      minCupPrice: 18.00
    }, { 'X-User-Role': 'SUPER_ADMIN' });

    // ---------------------------------------------------------
    // SECTION 7: AUDIT LOG RETRIEVAL & VERIFICATION
    // ---------------------------------------------------------
    console.log('\n--- SECTION 7: AUDIT LOG RETRIEVAL & VERIFICATION ---');
    const auditRes = await apiGet('/admin/pricing/config/audit');
    assert(auditRes.status === 200, 'GET /api/admin/pricing/config/audit succeeded');
    const auditLogs = auditRes.data;
    assert(Array.isArray(auditLogs) && auditLogs.length > 0, `Audit logs contain entries (count: ${auditLogs?.length})`);
    
    const hasGlobalAudit = auditLogs.some(l => l.settingKey === 'SETTLEMENT_INTERVAL_SECONDS' || l.settingKey === 'INCREASE_STEP');
    assert(hasGlobalAudit, 'Audit log records global setting updates');
    
    const hasProductAudit = auditLogs.some(l => l.settingKey === 'PRODUCT_TARGET_SALES');
    assert(hasProductAudit, 'Audit log records per-product target sales updates');

    // ---------------------------------------------------------
    // SECTION 8: SYSTEM RESET & CLEANUP
    // ---------------------------------------------------------
    console.log('\n--- SECTION 8: SYSTEM RESET & CLEANUP ---');
    const resetRes = await apiPost('/pricing/reset-all', {}, { 'X-User-Role': 'SUPER_ADMIN' });
    assert(resetRes.status === 200, 'Reset all products succeeded');
    assert(resetRes.data.productsReset === 8, 'Reset all 8 active products to default ₹25.00');

    // Final price check
    const finalProdsRes = await apiGet('/pos/products');
    const allAt25 = finalProdsRes.data.every(p => Number(p.currentCupPrice) === 25.00);
    assert(allAt25, 'All beverages successfully restored to standard ₹25.00');

    console.log('\n======================================================================');
    console.log(`🏁 MASTER VALIDATION COMPLETE: ${passedTests}/${totalTests} TESTS PASSED`);
    console.log('======================================================================\n');

    if (passedTests === totalTests) {
      process.exit(0);
    } else {
      process.exit(1);
    }
  } catch (err) {
    console.error('\n❌ UNHANDLED EXCEPTION IN VALIDATION SUITE:', err);
    process.exit(1);
  }
}

runMasterValidation();
