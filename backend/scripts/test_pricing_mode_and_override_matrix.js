/**
 * TEST MATRIX: PRICING MODE, MANUAL OVERRIDE, DYNAMIC DWMA SETTLEMENT, AND ADMIN CONTROLS
 */

const http = require('http');
const { Client } = require('pg');

const dbConfig = {
  host: 'localhost',
  port: 5432,
  user: 'postgres',
  password: 'postgres',
  database: 'retailposdb'
};

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
    headers: { 'Accept': 'application/json', 'X-User-Role': 'SUPER_ADMIN', ...headers }
  });
}

async function apiPost(path, data = {}, headers = {}) {
  return request({
    hostname: 'localhost',
    port: 8088,
    path: '/api' + path,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-User-Role': 'SUPER_ADMIN',
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
      'X-User-Role': 'SUPER_ADMIN',
      ...headers
    }
  }, data);
}

async function runMatrix() {
  console.log('================================================================================');
  console.log('🎯 COMPREHENSIVE PRICING MODE & MANUAL OVERRIDE VALIDATION SUITE');
  console.log('================================================================================\n');

  const pgClient = new Client(dbConfig);
  await pgClient.connect();

  const results = [];

  try {
    // -------------------------------------------------------------------------
    // SETUP: Clean Reset to Baseline ₹25.00
    // -------------------------------------------------------------------------
    console.log('[SETUP] Resetting market to ₹25.00 default baseline in DYNAMIC mode...');
    await apiPost('/pricing/reset-all');
    await pgClient.query("DELETE FROM sales_order_items WHERE created_at >= NOW() - INTERVAL '10 minutes'");

    // -------------------------------------------------------------------------
    // TEST A: Dynamic mode + zero sales (Decay -₹2.00)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST A: Dynamic Mode + Zero Sales ---');
    const evalA = await apiPost('/pricing/evaluate');
    const mangoA = evalA.data?.updatedPrices?.find(p => p.beverageId === 1 || p.id === 1);
    const dbMangoA = (await pgClient.query("SELECT current_cup_price, pricing_mode FROM products WHERE id = 1")).rows[0];
    
    console.log(`    Evaluated Mango Price: ₹${mangoA?.currentPrice}, Mode: ${mangoA?.pricingMode}`);
    console.log(`    PostgreSQL DB Price:   ₹${parseFloat(dbMangoA.current_cup_price).toFixed(2)}, Mode: ${dbMangoA.pricing_mode}`);
    const passA = (parseFloat(dbMangoA.current_cup_price) === 23.00 && dbMangoA.pricing_mode === 'DYNAMIC');
    results.push({ test: 'A. Dynamic Mode + Zero Sales', expected: '₹23.00 (Decay -₹2)', actual: `₹${parseFloat(dbMangoA.current_cup_price).toFixed(2)}`, status: passA ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST B: Dynamic mode + high sales (Surge +₹1.00)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST B: Dynamic Mode + High Sales (Surge) ---');
    // Set Mango Target to 2.00
    await apiPut('/admin/pricing/products/1/config', { targetSales: 2.00 });
    // Create sales order of 4 cups
    await apiPost('/pos/checkout', {
      items: [{ productId: 1, quantity: 4, unitPrice: 23.00 }],
      paymentMethod: 'CASH'
    });
    const evalB = await apiPost('/pricing/evaluate');
    const dbMangoB = (await pgClient.query("SELECT current_cup_price, pricing_mode FROM products WHERE id = 1")).rows[0];
    console.log(`    PostgreSQL DB Price after 4 sales: ₹${parseFloat(dbMangoB.current_cup_price).toFixed(2)}`);
    const passB = (parseFloat(dbMangoB.current_cup_price) === 24.00); // 23 + 1 = 24
    results.push({ test: 'B. Dynamic Mode + High Sales', expected: '₹24.00 (+₹1 Surge)', actual: `₹${parseFloat(dbMangoB.current_cup_price).toFixed(2)}`, status: passB ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST E: Manual Price Override (Lock Mango at ₹30.00)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST E: Admin Manual Price Override (Lock at ₹30.00) ---');
    const lockRes = await apiPost('/pricing/products/1/price?newPrice=30.00&reason=TEST_MANUAL_LOCK');
    const dbMangoE = (await pgClient.query("SELECT current_cup_price, pricing_mode FROM products WHERE id = 1")).rows[0];
    const posMangoE = (await apiGet('/pos/products')).data?.find(p => p.id === 1);
    
    console.log(`    PostgreSQL DB Price: ₹${parseFloat(dbMangoE.current_cup_price).toFixed(2)}, Mode: ${dbMangoE.pricing_mode}`);
    console.log(`    POS REST API Price:  ₹${parseFloat(posMangoE.currentCupPrice).toFixed(2)}, Mode: ${posMangoE.pricingMode}`);

    // Now run a DWMA settlement cycle with ZERO sales to PROVE MANUAL LOCK DOES NOT DECAY!
    console.log('    Running 120s Settlement with 0 sales while locked in MANUAL_OVERRIDE...');
    await pgClient.query("DELETE FROM sales_order_items WHERE created_at >= NOW() - INTERVAL '10 minutes'");
    const evalLockCycle = await apiPost('/pricing/evaluate');
    const dbMangoEAfterCycle = (await pgClient.query("SELECT current_cup_price, pricing_mode FROM products WHERE id = 1")).rows[0];
    console.log(`    PostgreSQL Price after Zero-Demand Cycle: ₹${parseFloat(dbMangoEAfterCycle.current_cup_price).toFixed(2)} (Held Constant)`);

    const passE = (parseFloat(dbMangoEAfterCycle.current_cup_price) === 30.00 && dbMangoEAfterCycle.pricing_mode === 'MANUAL_OVERRIDE');
    results.push({ test: 'E. Manual Price Override Lock', expected: '₹30.00 Held Constant (No Decay)', actual: `₹${parseFloat(dbMangoEAfterCycle.current_cup_price).toFixed(2)} (${dbMangoEAfterCycle.pricing_mode})`, status: passE ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST F: Release Manual Override (Resumes Dynamic DWMA from ₹30.00)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST F: Release Manual Override ---');
    const releaseRes = await apiPost('/pricing/products/1/release-override');
    const dbMangoF1 = (await pgClient.query("SELECT current_cup_price, pricing_mode FROM products WHERE id = 1")).rows[0];
    console.log(`    PostgreSQL DB Mode after Release: ${dbMangoF1.pricing_mode}, Starting Price: ₹${parseFloat(dbMangoF1.current_cup_price).toFixed(2)}`);

    // Run next settlement cycle with zero sales -> now it SHOULD decay dynamically from ₹30.00 to ₹28.00!
    console.log('    Running next Dynamic Settlement cycle with 0 sales...');
    const evalReleaseCycle = await apiPost('/pricing/evaluate');
    const dbMangoF2 = (await pgClient.query("SELECT current_cup_price, pricing_mode FROM products WHERE id = 1")).rows[0];
    console.log(`    PostgreSQL DB Price after Dynamic Cycle: ₹${parseFloat(dbMangoF2.current_cup_price).toFixed(2)} (Decayed ₹30 -> ₹28)`);

    const passF = (parseFloat(dbMangoF2.current_cup_price) === 28.00 && dbMangoF2.pricing_mode === 'DYNAMIC');
    results.push({ test: 'F. Release Override & Resume Dynamic', expected: '₹28.00 (Decayed from ₹30)', actual: `₹${parseFloat(dbMangoF2.current_cup_price).toFixed(2)} (${dbMangoF2.pricing_mode})`, status: passF ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST G: Admin Target Sales Change (2.00 vs 4.00)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST G: Admin Target Sales Tuning (2.00 vs 4.00) ---');
    await apiPut('/admin/pricing/products/1/config', { targetSales: 4.00 });
    const cfgG = (await apiGet('/admin/pricing/config')).data;
    const prodCfgG = cfgG.products.find(p => p.productId === 1);
    console.log(`    Active Mango Target Sales in PostgreSQL/Config: ${prodCfgG.targetSales}`);
    const passG = (prodCfgG.targetSales === 4.00);
    results.push({ test: 'G. Admin Target Sales Tuning', expected: 'Target Sales = 4.00', actual: `Target Sales = ${prodCfgG.targetSales}`, status: passG ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST I: Floor & Ceiling Price Bounds Enforcement
    // -------------------------------------------------------------------------
    console.log('\n--- TEST I: Admin Floor (₹20) & Ceiling (₹32) Enforcement ---');
    await apiPut('/admin/pricing/config', { minCupPrice: 20.00, maxCupPrice: 32.00 });
    // Decay Mango down to floor
    for (let k = 0; k < 6; k++) {
      await apiPost('/pricing/evaluate');
    }
    const dbMangoFloor = (await pgClient.query("SELECT current_cup_price FROM products WHERE id = 1")).rows[0];
    console.log(`    Decayed Price at Minimum Floor: ₹${parseFloat(dbMangoFloor.current_cup_price).toFixed(2)} (Clamped at ₹20.00)`);
    const passI = (parseFloat(dbMangoFloor.current_cup_price) === 20.00);
    results.push({ test: 'I. Price Floor Hard-Clamp', expected: '₹20.00 Floor Clamp', actual: `₹${parseFloat(dbMangoFloor.current_cup_price).toFixed(2)}`, status: passI ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST J: Admin Reset All Products
    // -------------------------------------------------------------------------
    console.log('\n--- TEST J: Admin Reset All Products ---');
    await apiPut('/admin/pricing/config', { defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await apiPost('/pricing/reset-all');
    const dbResetRows = (await pgClient.query("SELECT id, current_cup_price, pricing_mode FROM products WHERE is_active = true")).rows;
    const allReset25 = dbResetRows.every(r => parseFloat(r.current_cup_price) === 25.00 && r.pricing_mode === 'DYNAMIC');
    console.log(`    All 8 Products Reset to ₹25.00 in DYNAMIC mode: ${allReset25}`);
    results.push({ test: 'J. Admin Reset to Default', expected: '8 Products @ ₹25.00 DYNAMIC', actual: `${dbResetRows.length} Products @ ₹25.00`, status: allReset25 ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST K: Market Crash Trigger & Stop
    // -------------------------------------------------------------------------
    console.log('\n--- TEST K: Market Crash Trigger & Stop ---');
    await apiPost('/pricing/market-crash/trigger?durationMinutes=2');
    const dbCrashRows = (await pgClient.query("SELECT current_cup_price FROM products WHERE is_active = true")).rows;
    console.log(`    Crash Price in DB: ₹${parseFloat(dbCrashRows[0].current_cup_price).toFixed(2)}`);
    await apiPost('/pricing/market-crash/stop');
    await apiPost('/pricing/reset-all');
    const passK = (parseFloat(dbCrashRows[0].current_cup_price) <= 20.00);
    results.push({ test: 'K. Market Crash Control', expected: 'Crash Price <= ₹20.00', actual: `₹${parseFloat(dbCrashRows[0].current_cup_price).toFixed(2)}`, status: passK ? 'PASS' : 'FAIL' });

    // -------------------------------------------------------------------------
    // TEST L: Sandbox Parameter Deployment
    // -------------------------------------------------------------------------
    console.log('\n--- TEST L: Sandbox Parameter Deployment ---');
    const deployRes = await apiPost('/pricing/deploy', {
      productId: 1,
      currentPrice: 30.00,
      minPrice: 18.00,
      maxPrice: 35.00,
      targetSalesPer2Minute: 2.0
    });
    const dbMangoL = (await pgClient.query("SELECT current_cup_price, target_sales_per_2_minute FROM products WHERE id = 1")).rows[0];
    console.log(`    Deployed Mango Price: ₹${parseFloat(dbMangoL.current_cup_price).toFixed(2)}, Target: ${dbMangoL.target_sales_per_2_minute}`);
    const passL = (parseFloat(dbMangoL.current_cup_price) === 30.00 && dbMangoL.target_sales_per_2_minute === 2.0);
    results.push({ test: 'L. Sandbox Live Deployment', expected: '₹30.00 & Target 2.0', actual: `₹${parseFloat(dbMangoL.current_cup_price).toFixed(2)} & Target ${dbMangoL.target_sales_per_2_minute}`, status: passL ? 'PASS' : 'FAIL' });

    // Clean reset back to baseline
    await apiPost('/pricing/reset-all');

    await pgClient.end();

    console.log('\n================================================================================');
    console.log('📊 FINAL VERIFICATION MATRIX RESULTS');
    console.log('================================================================================');
    console.table(results);

    const allPassed = results.every(r => r.status === 'PASS');
    if (allPassed) {
      console.log('🎉 ALL MATRIX TESTS PASSED WITH 100% SUCCESS!');
      process.exit(0);
    } else {
      console.log('❌ SOME TESTS FAILED');
      process.exit(1);
    }
  } catch (err) {
    console.error('Matrix execution error:', err);
    await pgClient.end();
    process.exit(1);
  }
}

runMatrix();
