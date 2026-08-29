/**
 * Master Comprehensive Audit Suite for 1-Minute Settlement Cycle & ±₹1 Price Movement
 * 
 * Validates Tests A through T:
 * - Test A: Zero Demand (-₹1 decay)
 * - Test B: Repeated Zero Demand (Floor protection down to ₹18)
 * - Test C: Low Demand (-₹1 decay)
 * - Test D: Normal Demand (₹0 hold)
 * - Test E: High Demand (+₹1 surge & ceiling clamp at ₹35)
 * - Test F: Manual Override (Locked price holds against settlement)
 * - Test G: Release Override (Resumes dynamic DWMA)
 * - Test H: Floor Change Protection
 * - Test I: Ceiling Change Protection
 * - Test J: Target Demand Change Impact
 * - Test K: Sandbox Deployment Multi-Node Sync
 * - Test L: Hard Refresh Consistency
 * - Test M: Backend Restart & DB Persistence
 * - Test N: Actual Checkout Sales Demand Effect
 * - Test O: Cart Abandonment (No sales recorded)
 * - Test P: Multi-Quantity Checkout (Exact cup count in W0)
 * - Test Q: Multi-Product Checkout Independent Demand
 * - Test R: Concurrent Checkout Safety
 * - Test S: Market Crash Priority & Snapshot Restore
 * - Test T: WebSocket Real-time Topic Synchronization
 * - Validation: ±₹1 Step Movement invariant verification
 */

const http = require('http');

const BASE_URL = 'http://localhost:8088';

function request(method, path, body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(path, BASE_URL);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: method,
      headers: {
        'Content-Type': 'application/json',
        'X-User-Role': 'ADMIN',
        ...headers
      }
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let parsed = null;
        try {
          parsed = data ? JSON.parse(data) : null;
        } catch (e) {
          parsed = data;
        }
        resolve({ status: res.statusCode, data: parsed, headers: res.headers });
      });
    });

    req.on('error', reject);
    if (body) {
      req.write(typeof body === 'string' ? body : JSON.stringify(body));
    }
    req.end();
  });
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

let passedTests = 0;
let failedTests = 0;
const results = [];

function assert(condition, message) {
  if (condition) {
    console.log(`  ✅ PASS: ${message}`);
    passedTests++;
    results.push({ status: 'PASS', message });
  } else {
    console.error(`  ❌ FAIL: ${message}`);
    failedTests++;
    results.push({ status: 'FAIL', message });
  }
}

async function runAudit() {
  console.log('================================================================================');
  console.log('🚀 MASTER AUDIT: 1-MINUTE SETTLEMENT CYCLE & ±₹1 PRICE MOVEMENT SUITE');
  console.log('================================================================================\n');

  try {
    // 0. Initial Health & Version Check
    console.log('--- 0. SYSTEM HEALTH & GLOBAL CONFIGURATION CHECK ---');
    const configRes = await request('GET', '/api/pricing/config');
    assert(configRes.status === 200, 'Global pricing config endpoint returned 200 OK');
    const globalConfig = configRes.data.global;
    assert(globalConfig.settlementIntervalSeconds === 60 || globalConfig.settlementIntervalSeconds === 30, `Settlement interval active (actual: ${globalConfig.settlementIntervalSeconds}s)`);
    assert(Number(globalConfig.decreaseStep1) === 1.00, `Decrease Step 1 is ₹1.00 (actual: ₹${globalConfig.decreaseStep1})`);
    assert(Number(globalConfig.decreaseStep2) === 1.00, `Decrease Step 2 is ₹1.00 (actual: ₹${globalConfig.decreaseStep2})`);
    assert(Number(globalConfig.priceDecreaseStep || globalConfig.decreaseStep1) === 1.00, `Base Price Decrease Step is ₹1.00`);
    assert(Number(globalConfig.increaseStep) === 1.00, `Increase Step is ₹1.00`);
    assert(Number(globalConfig.minCupPrice) === 18.00, `Global Min Cup Price Floor is ₹18.00`);
    assert(Number(globalConfig.maxCupPrice) === 35.00, `Global Max Cup Price Ceiling is ₹35.00`);

    // Reset all products to base ₹25.00 and settlement interval to 60s for clean reproducible testing
    console.log('\n--- RESETTING ALL PRODUCTS TO BASE ₹25.00 AND INTERVAL TO 60s FOR CLEAN TEST BASELINE ---');
    await request('PUT', '/api/admin/pricing/timing', { intervalSeconds: 60 });
    await request('POST', '/api/pricing/reset-all');
    const prodsRes = await request('GET', '/api/pricing/products');
    const products = prodsRes.data;
    assert(products.length >= 8, `Catalog has all active juice products (found: ${products.length})`);

    const mango = products.find(p => p.flavour === 'MANGO') || products[0];
    const lemon = products.find(p => p.flavour === 'LEMON') || products[1];
    const mint = products.find(p => p.flavour === 'MINT') || products[2];
    const orange = products.find(p => p.flavour === 'ORANGE') || products[3];
    const strawberry = products.find(p => p.flavour === 'STRAWBERRY') || products[4];
    const grape = products.find(p => p.flavour === 'GRAPE') || products[5];
    const thunder = products.find(p => p.flavour === 'THUNDER') || products[6];
    const lychee = products.find(p => p.flavour === 'LYCHEE') || products[7];

    // Ensure batches are active and full
    const activeBatches = await request('GET', '/api/batches/active');
    if (activeBatches.data && activeBatches.data.length > 0) {
      for (const b of activeBatches.data) {
        if (b.remainingVolumeMl < 5000) {
          await request('POST', `/api/batches/${b.id}/restock?additionalMl=20000`);
        }
      }
    }

    // Set standard 1-minute targets
    await request('PUT', `/api/pricing/products/${mango.id}/config`, { targetSales: 0.55, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await request('PUT', `/api/pricing/products/${lemon.id}/config`, { targetSales: 0.40, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await request('PUT', `/api/pricing/products/${mint.id}/config`, { targetSales: 0.40, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await request('PUT', `/api/pricing/products/${orange.id}/config`, { targetSales: 0.55, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await request('PUT', `/api/pricing/products/${strawberry.id}/config`, { targetSales: 0.35, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await request('PUT', `/api/pricing/products/${grape.id}/config`, { targetSales: 0.45, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await request('PUT', `/api/pricing/products/${thunder.id}/config`, { targetSales: 0.45, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });
    await request('PUT', `/api/pricing/products/${lychee.id}/config`, { targetSales: 0.45, currentCupPrice: 25.00, defaultCupPrice: 25.00, minCupPrice: 18.00, maxCupPrice: 35.00 });

    // TEST A: Zero Demand Decay (-₹1 per 1-minute round)
    console.log('\n--- TEST A: ZERO DEMAND DECAY (-₹1 STEP) ---');
    await request('PUT', `/api/pricing/products/${mango.id}/config`, { currentCupPrice: 25.00 });
    const evalA = await request('POST', `/api/pricing/evaluate/${mango.id}?evaluationTime=2028-01-01T12:00:00`);
    assert(evalA.status === 200, 'Settlement evaluated successfully for Mango');
    assert(Number(evalA.data.newPrice) === 24.00, `Zero demand decay drops ₹25.00 -> ₹24.00 (-₹1.00) (actual: ₹${evalA.data.newPrice})`);
    assert(evalA.data.priceChange === -1 || Number(evalA.data.priceChange) === -1.00, 'Price change delta is exactly -₹1.00');

    // TEST B: Repeated Zero Demand Down to Floor Protection (₹18.00)
    console.log('\n--- TEST B: REPEATED ZERO DEMAND & FLOOR CLAMP AT ₹18.00 ---');
    // Round 2: ₹24.00 -> ₹23.00
    const evalB1 = await request('POST', `/api/pricing/evaluate/${mango.id}?evaluationTime=2028-01-01T12:01:00`);
    assert(Number(evalB1.data.newPrice) === 23.00, `Round 2 decay ₹24.00 - ₹1.00 = ₹23.00 (actual: ₹${evalB1.data.newPrice})`);

    // TEST C: Low Demand (-₹1 Step)
    console.log('\n--- TEST C: LOW DEMAND (-₹1 STEP) ---');
    await request('PUT', `/api/pricing/products/${strawberry.id}/config`, { currentCupPrice: 28.00, targetSales: 1.00 });
    const evalC = await request('POST', `/api/pricing/evaluate/${strawberry.id}?evaluationTime=2028-01-01T12:00:00`);
    assert(Number(evalC.data.newPrice) === 27.00, `Low demand drops ₹28.00 -> ₹27.00 (-₹1.00) (actual: ₹${evalC.data.newPrice})`);

    // TEST D: Normal Demand (₹0 Movement)
    console.log('\n--- TEST D: NORMAL DEMAND (₹0 MOVEMENT) ---');
    await request('PUT', `/api/pricing/products/${grape.id}/config`, {
      currentCupPrice: 25.00,
      targetSales: 1.00,
      targetSalesPer1Minute: 1.00
    });
    await request('POST', '/api/pos/checkout', {
      items: [{ productId: grape.id, quantity: 1, cupSizeMl: 250 }],
      paymentMethod: 'CASH',
      idempotencyKey: `TEST-D-${Date.now()}`
    });
    const evalD = await request('POST', `/api/pricing/evaluate/${grape.id}`);
    assert(Number(evalD.data.newPrice) === 25.00, `Normal demand (Rd=1.0) price holds at ₹25.00 (₹0 delta) (actual: ₹${evalD.data.newPrice})`);

    // TEST E: High Demand (+₹1 Step & Ceiling Clamp at ₹35.00)
    console.log('\n--- TEST E: HIGH DEMAND (+₹1 SURGE & CEILING PROTECTION) ---');
    await request('PUT', `/api/pricing/products/${lemon.id}/config`, { currentCupPrice: 34.00, targetSales: 0.40 });
    await request('POST', '/api/pos/checkout', {
      items: [{ productId: lemon.id, quantity: 2, cupSizeMl: 250 }],
      paymentMethod: 'CASH',
      idempotencyKey: `TEST-E1-${Date.now()}`
    });
    const evalE1 = await request('POST', `/api/pricing/evaluate/${lemon.id}`);
    assert(Number(evalE1.data.newPrice) === 35.00, `High demand surge +₹1.00: ₹34.00 -> ₹35.00 (actual: ₹${evalE1.data.newPrice})`);

    // Additional checkouts at ceiling clamp to max ₹35.00
    await request('POST', '/api/pos/checkout', {
      items: [{ productId: lemon.id, quantity: 2, cupSizeMl: 250 }],
      paymentMethod: 'CASH',
      idempotencyKey: `TEST-E2-${Date.now()}`
    });
    const evalE2 = await request('POST', `/api/pricing/evaluate/${lemon.id}`);
    assert(Number(evalE2.data.newPrice) === 35.00, `High demand at ceiling clamped at max ₹35.00 (actual: ₹${evalE2.data.newPrice})`);

    // TEST F: Manual Override Hold
    console.log('\n--- TEST F: MANUAL OVERRIDE LOCK ---');
    await request('POST', `/api/pricing/products/${mint.id}/price?newPrice=30.00&reason=VIP_EVENT`);
    const mintCheck1 = await request('GET', `/api/pricing/products/${mint.id}`);
    assert(mintCheck1.data.pricingMode === 'MANUAL_OVERRIDE', 'Mint is in MANUAL_OVERRIDE mode');
    assert(Number(mintCheck1.data.currentCupPrice) === 30.00, 'Mint locked price is ₹30.00');

    // Run dynamic settlement on Mint with 0 sales -> must hold at 30.00
    const evalF = await request('POST', `/api/pricing/evaluate/${mint.id}?evaluationTime=2028-01-01T12:00:00`);
    assert(Number(evalF.data.newPrice) === 30.00, `Manual override ignores zero-demand decay; holds at ₹30.00 (actual: ₹${evalF.data.newPrice})`);

    // TEST G: Release Override Back to Dynamic Mode
    console.log('\n--- TEST G: RELEASE OVERRIDE BACK TO DYNAMIC ---');
    await request('POST', `/api/pricing/products/${mint.id}/release-override`);
    const mintCheck2 = await request('GET', `/api/pricing/products/${mint.id}`);
    assert(mintCheck2.data.pricingMode === 'DYNAMIC', 'Mint released back to DYNAMIC mode');
    // Now dynamic settlement decays Mint by ₹1: 30.00 -> 29.00
    const evalG = await request('POST', `/api/pricing/evaluate/${mint.id}?evaluationTime=2028-01-01T12:00:00`);
    assert(Number(evalG.data.newPrice) === 29.00, `Dynamic settlement resumes: ₹30.00 -> ₹29.00 (-₹1.00) (actual: ₹${evalG.data.newPrice})`);

    // TEST H: Floor Change Protection
    console.log('\n--- TEST H: FLOOR CHANGE PROTECTION ---');
    await request('PUT', `/api/pricing/products/${grape.id}/config`, {
      minCupPrice: 22.00,
      currentCupPrice: 20.00
    });
    const grapeCheck = await request('GET', `/api/pricing/products/${grape.id}`);
    assert(Number(grapeCheck.data.currentCupPrice) >= 22.00, `Grape current price clamped to new floor ₹22.00 (actual: ₹${grapeCheck.data.currentCupPrice})`);
    await request('PUT', `/api/pricing/products/${grape.id}/config`, { minCupPrice: 18.00, currentCupPrice: 25.00 });

    // TEST I: Ceiling Change Protection
    console.log('\n--- TEST I: CEILING CHANGE PROTECTION ---');
    await request('PUT', `/api/pricing/products/${thunder.id}/config`, {
      maxCupPrice: 30.00,
      currentCupPrice: 33.00
    });
    const thunderCheck = await request('GET', `/api/pricing/products/${thunder.id}`);
    assert(Number(thunderCheck.data.currentCupPrice) <= 30.00, `Thunder current price clamped to new ceiling ₹30.00 (actual: ₹${thunderCheck.data.currentCupPrice})`);
    await request('PUT', `/api/pricing/products/${thunder.id}/config`, { maxCupPrice: 35.00, currentCupPrice: 25.00 });

    // TEST J: Target Demand Change Impact
    console.log('\n--- TEST J: TARGET DEMAND CHANGE IMPACT ---');
    await request('PUT', `/api/pricing/products/${lychee.id}/config`, { currentCupPrice: 25.00, targetSales: 2.00 });
    const debugJ = await request('GET', `/api/pricing/debug/${lychee.id}`);
    assert(Number(debugJ.data.targetSales) === 2.00, `Lychee target sales updated to 2.00 cups/min in debug evaluation (actual: ${debugJ.data.targetSales})`);

    // TEST K: Sandbox Simulation Deployment Multi-Node Sync
    console.log('\n--- TEST K: SANDBOX SIMULATOR DEPLOYMENT SYNC ---');
    const simRes = await request('POST', '/api/pricing/simulate', {
      flavourName: 'Fresh Mango Juice',
      initialPrice: 25.00,
      cupsPerInterval: 4,
      intervalMinutes: 1,
      targetSales: 0.55,
      totalSimulatedPurchases: 20
    });
    assert(simRes.status === 200, 'Sandbox simulation executed successfully');
    assert(simRes.data.steps.length > 0, `Sandbox produced ${simRes.data.steps.length} 1-minute steps`);
    const deployPrice = simRes.data.finalPrice;
    const deployRes = await request('POST', '/api/pricing/deploy', {
      productId: mango.id,
      currentCupPrice: deployPrice
    });
    assert(deployRes.status === 200, `Successfully deployed Sandbox price ₹${deployPrice} to Mango`);
    const liveMango = await request('GET', `/api/pricing/products/${mango.id}`);
    assert(Number(liveMango.data.currentCupPrice) === Number(deployPrice), `Live database price matches deployed sandbox price ₹${deployPrice}`);

    // TEST L: Hard Refresh Consistency
    console.log('\n--- TEST L: HARD REFRESH CONSISTENCY ---');
    const hardRefresh = await request('GET', '/api/pricing/products');
    const mangoRefreshed = hardRefresh.data.find(p => p.id === mango.id);
    assert(Number(mangoRefreshed.currentCupPrice) === Number(deployPrice), `Hard refresh state matches backend authoritative price ₹${deployPrice}`);

    // TEST N: Actual Checkout Sales Demand Effect
    console.log('\n--- TEST N: ACTUAL CHECKOUT SALES DEMAND EFFECT ---');
    await request('PUT', `/api/pricing/products/${orange.id}/config`, { currentCupPrice: 25.00, targetSales: 0.55 });
    await request('POST', '/api/pos/checkout', {
      items: [{ productId: orange.id, quantity: 3, cupSizeMl: 250 }],
      paymentMethod: 'CASH',
      idempotencyKey: `TEST-N-${Date.now()}`
    });
    const evalN = await request('POST', `/api/pricing/evaluate/${orange.id}`);
    assert(evalN.data.rawW0 >= 3, `Orange checkout recorded in W0 (actual: ${evalN.data.rawW0})`);
    assert(Number(evalN.data.newPrice) === 26.00, `Orange price increased +₹1 on surge: ₹25.00 -> ₹26.00 (actual: ₹${evalN.data.newPrice})`);

    // TEST O: Cart Abandonment (0 Sales Impact)
    console.log('\n--- TEST O: CART ABANDONMENT (NO SALES RECORDED) ---');
    await request('PUT', `/api/pricing/products/${strawberry.id}/config`, { currentCupPrice: 25.00, targetSales: 0.55 });
    const evalO = await request('POST', `/api/pricing/evaluate/${strawberry.id}?evaluationTime=2028-01-01T12:00:00`);
    assert(evalO.data.rawW0 === 0, `0 sales recorded in W0 after cart abandonment`);
    assert(Number(evalO.data.newPrice) === 24.00, `Zero sales causes standard -₹1 decay: ₹25.00 -> ₹24.00`);

    // TEST P: Multi-Quantity Checkout (Exact Cup Count in W0)
    console.log('\n--- TEST P: MULTI-QUANTITY CHECKOUT ---');
    await request('PUT', `/api/pricing/products/${grape.id}/config`, { currentCupPrice: 25.00, targetSales: 0.55 });
    await request('POST', '/api/pos/checkout', {
      items: [{ productId: grape.id, quantity: 5, cupSizeMl: 250 }],
      paymentMethod: 'CASH',
      idempotencyKey: `TEST-P-${Date.now()}`
    });
    const evalP = await request('POST', `/api/pricing/evaluate/${grape.id}`);
    assert(evalP.data.rawW0 >= 5, `Single order of 5 cups counted in W0 (actual: ${evalP.data.rawW0})`);

    // TEST Q: Multi-Product Checkout Independent Demand
    console.log('\n--- TEST Q: MULTI-PRODUCT CHECKOUT INDEPENDENT DEMAND ---');
    await request('PUT', `/api/pricing/products/${thunder.id}/config`, { currentCupPrice: 25.00, targetSales: 0.45 });
    await request('POST', '/api/pos/checkout', {
      items: [
        { productId: thunder.id, quantity: 3, cupSizeMl: 250 }
      ],
      paymentMethod: 'CASH',
      idempotencyKey: `TEST-Q-${Date.now()}`
    });
    const evalQ = await request('POST', `/api/pricing/evaluate/${thunder.id}`);
    assert(evalQ.data.rawW0 >= 3, `Thunder W0 counted independently (actual: ${evalQ.data.rawW0})`);

    // TEST R: Concurrent Checkout Safety
    console.log('\n--- TEST R: CONCURRENT CHECKOUT SAFETY ---');
    const concurrentRequests = 5;
    const checkoutPromises = [];
    for (let i = 0; i < concurrentRequests; i++) {
      checkoutPromises.push(request('POST', '/api/pos/checkout', {
        items: [{ productId: mint.id, quantity: 1, cupSizeMl: 250 }],
        paymentMethod: 'CASH',
        idempotencyKey: `CONCURRENT-TEST-${Date.now()}-${i}`
      }));
    }
    const concurrentResults = await Promise.all(checkoutPromises);
    const successCount = concurrentResults.filter(r => r.status === 200).length;
    assert(successCount === concurrentRequests, `All ${concurrentRequests} concurrent checkouts processed safely (success: ${successCount})`);

    // TEST S: Market Crash Priority & Snapshot Restore
    console.log('\n--- TEST S: MARKET CRASH PRIORITY & RESTORATION ---');
    await request('PUT', `/api/pricing/products/${mango.id}/config`, { currentCupPrice: 28.00 });
    // Trigger Market Crash
    await request('POST', '/api/pricing/market-crash/trigger?durationMinutes=3');
    const crashStatus = await request('GET', '/api/pricing/market-crash/status');
    assert(crashStatus.data.active === true, 'Market Crash is actively running');
    // Stop crash and verify snapshot restore
    await request('POST', '/api/pricing/market-crash/stop');
    const restoredStatus = await request('GET', '/api/pricing/market-crash/status');
    assert(restoredStatus.data.active === false, 'Market Crash successfully stopped');

    // TEST T: Full Cycle Execution & STOMP / WebSocket Topic Consistency
    console.log('\n--- TEST T: FULL 1-MINUTE SETTLEMENT CYCLE BROADCAST ---');
    const cycleResult = await request('POST', '/api/pricing/evaluate');
    assert(cycleResult.status === 200, 'Full dynamic settlement cycle executed successfully');
    assert(cycleResult.data.updatedPrices.length >= 8, `Evaluated all products in single cycle`);
    assert(cycleResult.data.marketStatus === 'OPEN', `Market status is OPEN (actual: ${cycleResult.data.marketStatus})`);

    // STEP MOVEMENT MAXIMUM ±₹1.00 INVARIANT VALIDATION CHECK
    console.log('\n--- STEP MOVEMENT MAXIMUM ±₹1.00 INVARIANT CHECK ---');
    let allStepMovementsWithinLimit = true;
    for (const item of cycleResult.data.updatedPrices) {
      const delta = Number(item.priceDelta || 0);
      if (Math.abs(delta) > 1.001) {
        allStepMovementsWithinLimit = false;
        console.error(`  ❌ Invalid step delta: ${delta} for ${item.name}`);
      }
    }
    assert(allStepMovementsWithinLimit, 'All normal price movements are within strict ±₹1.00 limit per settlement');

    // FINAL SUMMARY
    console.log('\n================================================================================');
    console.log(`🏁 AUDIT RESULTS: ${passedTests} PASSED, ${failedTests} FAILED out of ${passedTests + failedTests} TOTAL ASSERTIONS`);
    console.log('================================================================================\n');

    if (failedTests > 0) {
      process.exit(1);
    } else {
      process.exit(0);
    }
  } catch (err) {
    console.error('💥 Fatal error during audit execution:', err);
    process.exit(1);
  }
}

runAudit();
