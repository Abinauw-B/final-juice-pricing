const http = require('http');
const { Client } = require('pg');

const BASE_URL = 'http://localhost:8088';
const AUTH_HEADER = 'Basic ' + Buffer.from('admin:password').toString('base64');

function apiRequest(path, method = 'GET', data = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(BASE_URL + path);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': AUTH_HEADER,
        ...headers
      }
    };

    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(body);
          resolve({ status: res.statusCode, data: parsed, headers: res.headers });
        } catch (e) {
          resolve({ status: res.statusCode, raw: body, headers: res.headers });
        }
      });
    });

    req.on('error', reject);
    if (data) {
      req.write(typeof data === 'string' ? data : JSON.stringify(data));
    }
    req.end();
  });
}

function getPgClient() {
  return new Client({
    user: 'postgres',
    host: 'localhost',
    database: 'retailposdb',
    password: 'password',
    port: 5432
  });
}

const tests = [];
function record(name, passed, details = '') {
  tests.push({ name, passed, details });
  console.log(`${passed ? '✅ PASS' : '❌ FAIL'}: ${name}`);
  if (details) console.log(`   -> ${details}`);
}

async function runVerification() {
  console.log('======================================================================');
  console.log('🧪 VERIFYING AUTHORITATIVE PRODUCT PRICING & ENGINE REQUIREMENTS');
  console.log('======================================================================\n');

  const pg = getPgClient();
  await pg.connect();

  try {
    // ------------------------------------------------------------------
    // TEST 1: Requirement 22 - Admin updates Mango to 21 / 27 / 34 / 1.20
    // ------------------------------------------------------------------
    console.log('\n--- Requirement 22: Admin Updates Mango Parameters ---');
    const updatePayload = {
      productId: 1,
      minCupPrice: 21.00,
      defaultCupPrice: 27.00,
      maxCupPrice: 34.00,
      targetSalesPer1Minute: 1.20,
      currentCupPrice: 27.00,
      // Also send aliases to test backward compatibility
      minPrice: 21.00,
      defaultPrice: 27.00,
      maxPrice: 34.00,
      targetSales: 1.20,
      startPrice: 27.00
    };

    const updateRes = await apiRequest('/api/admin/pricing/products/1/config', 'PUT', updatePayload);
    record(
      'Admin PUT /api/admin/pricing/products/1/config succeeds',
      updateRes.status === 200,
      `Status: ${updateRes.status}`
    );

    // Verify PostgreSQL persistence
    const pgRes = await pg.query('SELECT min_cup_price, default_cup_price, max_cup_price, target_sales_per_1_minute, current_cup_price FROM products WHERE id = 1');
    const dbMango = pgRes.rows[0];
    const minVal = parseFloat(dbMango.min_cup_price);
    const defVal = parseFloat(dbMango.default_cup_price);
    const maxVal = parseFloat(dbMango.max_cup_price);
    const tgtVal = parseFloat(dbMango.target_sales_per_1_minute);
    const currVal = parseFloat(dbMango.current_cup_price);

    record(
      'PostgreSQL contains authoritative Mango values (min=21, base=27, max=34, target=1.20)',
      minVal === 21 && defVal === 27 && maxVal === 34 && Math.abs(tgtVal - 1.20) < 0.001,
      `DB values: min=${minVal}, base=${defVal}, max=${maxVal}, target=${tgtVal}, current=${currVal}`
    );

    // Hard refresh simulation: GET /api/pos/products
    const posRes = await apiRequest('/api/pos/products', 'GET');
    const posMango = (posRes.data || []).find(p => p.id === 1);
    record(
      'Customer POS / Refresh returns exact updated Mango configuration',
      posMango && parseFloat(posMango.minCupPrice) === 21 && parseFloat(posMango.defaultCupPrice) === 27 && parseFloat(posMango.maxCupPrice) === 34 && Math.abs(parseFloat(posMango.targetSalesPer1Minute) - 1.20) < 0.001,
      `POS API: min=${posMango?.minCupPrice}, base=${posMango?.defaultCupPrice}, max=${posMango?.maxCupPrice}, target=${posMango?.targetSalesPer1Minute}`
    );

    // ------------------------------------------------------------------
    // TEST 2: Requirement 11 - Global configuration update DOES NOT overwrite product config
    // ------------------------------------------------------------------
    console.log('\n--- Requirement 11: Global Config Does Not Overwrite Product Config ---');
    const globalPayload = {
      settlementIntervalSeconds: 60,
      highDemandThreshold: 1.10,
      stableDemandLowerThreshold: 0.90,
      stableDemandUpperThreshold: 1.10,
      lowDemandThreshold: 0.50,
      weightW0: 1.0,
      weightW1: 0.5,
      weightW2: 0.25,
      increaseStep: 1.0,
      decreaseStep1: 1.0,
      decreaseStep2: 1.0,
      priceDecreaseStep: 1.0
    };
    const globalRes = await apiRequest('/api/pricing/config', 'PUT', globalPayload);
    record(
      'Save Global Engine Configuration succeeds',
      globalRes.status === 200,
      `Status: ${globalRes.status}`
    );

    const pgAfterGlobal = await pg.query('SELECT min_cup_price, default_cup_price, max_cup_price, target_sales_per_1_minute FROM products WHERE id = 1');
    const dbMangoAfter = pgAfterGlobal.rows[0];
    record(
      'Mango retains product-specific bounds after Global Config Save',
      parseFloat(dbMangoAfter.min_cup_price) === 21 && parseFloat(dbMangoAfter.default_cup_price) === 27 && parseFloat(dbMangoAfter.max_cup_price) === 34 && Math.abs(parseFloat(dbMangoAfter.target_sales_per_1_minute) - 1.20) < 0.001,
      `Post-global DB: min=${dbMangoAfter.min_cup_price}, base=${dbMangoAfter.default_cup_price}, max=${dbMangoAfter.max_cup_price}, target=${dbMangoAfter.target_sales_per_1_minute}`
    );

    // ------------------------------------------------------------------
    // TEST 3: Requirement 12 - Reset All restores each product to its OWN base price
    // ------------------------------------------------------------------
    console.log('\n--- Requirement 12: Reset All Restores Each Product Base Price ---');
    // Change current price of Mango to 31
    await pg.query('UPDATE products SET current_cup_price = 31.00 WHERE id = 1');
    const resetRes = await apiRequest('/api/pricing/reset-all', 'POST');
    record(
      'POST /api/pricing/reset-all succeeds',
      resetRes.status === 200,
      `Status: ${resetRes.status}`
    );

    const pgAfterReset = await pg.query('SELECT current_cup_price, default_cup_price, min_cup_price, max_cup_price, target_sales_per_1_minute FROM products WHERE id = 1');
    const dbMangoReset = pgAfterReset.rows[0];
    record(
      'Reset All restored Mango to its OWN base price ₹27.00 without destroying bounds or target',
      parseFloat(dbMangoReset.current_cup_price) === 27.00 && parseFloat(dbMangoReset.min_cup_price) === 21.00 && parseFloat(dbMangoReset.max_cup_price) === 34.00,
      `Post-reset DB: current=${dbMangoReset.current_cup_price}, base=${dbMangoReset.default_cup_price}, min=${dbMangoReset.min_cup_price}, max=${dbMangoReset.max_cup_price}`
    );

    // ------------------------------------------------------------------
    // TEST 4: Requirement 6, 9, 23 - Deterministic Pricing Movements (+1, 0, -1, NEVER -2)
    // ------------------------------------------------------------------
    console.log('\n--- Requirement 6, 9, 23: Deterministic Pricing Movements ---');
    // Set Product 1: Min=21, Base=27, Max=34, Target=1.0, Current=27
    await pg.query(`
      UPDATE products 
      SET min_cup_price = 21.00, default_cup_price = 27.00, max_cup_price = 34.00, 
          target_sales_per_1_minute = 1.0, current_cup_price = 27.00, pricing_mode = 'DYNAMIC'
      WHERE id = 1
    `);
    await pg.query('DELETE FROM sales_order_items WHERE product_id = 1');
    await pg.query('DELETE FROM sales_orders WHERE id NOT IN (SELECT DISTINCT order_id FROM sales_order_items)');

    // Scenario A: High demand surge -> +₹1.00 (27 -> 28)
    for (let i = 0; i < 5; i++) {
      await apiRequest('/api/pos/checkout', 'POST', {
        items: [{ productId: 1, quantity: 1, cupSizeMl: 250 }],
        paymentMethod: 'CASH',
        idempotencyKey: `VERIFY-SURGE-${i}-${Date.now()}`
      });
    }

    const settleA = await apiRequest('/api/pricing/evaluate', 'POST');
    const mangoA = settleA.data.updatedPrices.find(p => p.beverageId === 1);
    record(
      'Scenario A: High demand surge yields +₹1.00 (₹27 -> ₹28)',
      mangoA && mangoA.currentPrice === 28.00 && mangoA.priceDelta === 1.0,
      `Price: ₹${mangoA?.currentPrice}, Delta: +${mangoA?.priceDelta}, Rd: ${mangoA?.demandRatio}`
    );

    // Scenario B: Stable demand -> ₹0.00 (28 -> 28)
    // Put 1 cup in W1 (80s ago) with target=1.0 -> Sw=0.5, Target=1.0 -> Rd=0.50 (low), so let's put 2 cups in W1: Sw=1.0, Target=1.0 -> Rd=1.00 (stable!)
    await pg.query('DELETE FROM sales_order_items WHERE product_id = 1');
    const orderRes = await pg.query(
      `INSERT INTO sales_orders (order_number, total_amount, subtotal, payment_method, payment_status, created_at)
       VALUES ($1, 56.00, 56.00, 'CASH', 'COMPLETED', NOW() - INTERVAL '80 seconds') RETURNING id`,
      [`ORD-STABLE-${Date.now()}`]
    );
    await pg.query(
      `INSERT INTO sales_order_items (order_id, product_id, product_name, quantity, cup_size_ml, unit_price, total_price, locked_price, price_version, volume_deducted_ml, created_at)
       VALUES ($1, 1, 'Fresh Mango Juice', 2, 250, 28.00, 56.00, 28.00, 1, 500, NOW() - INTERVAL '80 seconds')`,
      [orderRes.rows[0].id]
    );

    const settleB = await apiRequest('/api/pricing/evaluate', 'POST');
    const mangoB = settleB.data.updatedPrices.find(p => p.beverageId === 1);
    record(
      'Scenario B: Stable demand yields ₹0.00 (₹28 -> ₹28)',
      mangoB && mangoB.currentPrice === 28.00 && mangoB.priceDelta === 0.0,
      `Price: ₹${mangoB?.currentPrice}, Delta: ${mangoB?.priceDelta}, Rd: ${mangoB?.demandRatio}`
    );

    // Scenario C: Moderate low demand -> -₹1.00 (28 -> 27)
    // 1 cup in W1 (80s ago) with target=1.0 -> Sw=0.5, Target=1.0 -> Rd=0.50 (low)
    await pg.query('DELETE FROM sales_order_items WHERE product_id = 1');
    const orderResC = await pg.query(
      `INSERT INTO sales_orders (order_number, total_amount, subtotal, payment_method, payment_status, created_at)
       VALUES ($1, 28.00, 28.00, 'CASH', 'COMPLETED', NOW() - INTERVAL '80 seconds') RETURNING id`,
      [`ORD-LOW-${Date.now()}`]
    );
    await pg.query(
      `INSERT INTO sales_order_items (order_id, product_id, product_name, quantity, cup_size_ml, unit_price, total_price, locked_price, price_version, volume_deducted_ml, created_at)
       VALUES ($1, 1, 'Fresh Mango Juice', 1, 250, 28.00, 28.00, 28.00, 1, 250, NOW() - INTERVAL '80 seconds')`,
      [orderResC.rows[0].id]
    );

    const settleC = await apiRequest('/api/pricing/evaluate', 'POST');
    const mangoC = settleC.data.updatedPrices.find(p => p.beverageId === 1);
    record(
      'Scenario C: Moderate low demand yields -₹1.00 (₹28 -> ₹27)',
      mangoC && mangoC.currentPrice === 27.00 && mangoC.priceDelta === -1.0,
      `Price: ₹${mangoC?.currentPrice}, Delta: ${mangoC?.priceDelta}, Rd: ${mangoC?.demandRatio}`
    );

    // Scenario D: Zero demand -> -₹1.00 (27 -> 26, NOT 25!)
    await pg.query('DELETE FROM sales_order_items WHERE product_id = 1');
    const settleD = await apiRequest('/api/pricing/evaluate', 'POST');
    const mangoD = settleD.data.updatedPrices.find(p => p.beverageId === 1);
    record(
      'Scenario D: Zero demand yields EXACTLY -₹1.00 (₹27 -> ₹26, NEVER -₹2.00)',
      mangoD && mangoD.currentPrice === 26.00 && mangoD.priceDelta === -1.0,
      `Price: ₹${mangoD?.currentPrice}, Delta: ${mangoD?.priceDelta}, Rd: ${mangoD?.demandRatio}`
    );

    // ------------------------------------------------------------------
    // TEST 5: Requirement 10 - Product-specific Clamping
    // ------------------------------------------------------------------
    console.log('\n--- Requirement 10: Product-Specific Clamping ---');
    // Set price to min=21 and trigger zero demand -> clamped to 21
    await pg.query('UPDATE products SET current_cup_price = 21.00 WHERE id = 1');
    const settleFloor = await apiRequest('/api/pricing/evaluate', 'POST');
    const mangoFloor = settleFloor.data.updatedPrices.find(p => p.beverageId === 1);
    record(
      'Floor clamping: ₹21.00 with zero demand stays at product min ₹21.00',
      mangoFloor && mangoFloor.currentPrice === 21.00,
      `Price: ₹${mangoFloor?.currentPrice}`
    );

    // Set price to max=34 and trigger high demand -> capped at 34
    await pg.query('UPDATE products SET current_cup_price = 34.00 WHERE id = 1');
    for (let i = 0; i < 5; i++) {
      await apiRequest('/api/pos/checkout', 'POST', {
        items: [{ productId: 1, quantity: 1, cupSizeMl: 250 }],
        paymentMethod: 'CASH',
        idempotencyKey: `VERIFY-CEIL-${i}-${Date.now()}`
      });
    }
    const settleCeil = await apiRequest('/api/pricing/evaluate', 'POST');
    const mangoCeil = settleCeil.data.updatedPrices.find(p => p.beverageId === 1);
    record(
      'Ceiling clamping: ₹34.00 with high demand stays at product max ₹34.00',
      mangoCeil && mangoCeil.currentPrice === 34.00,
      `Price: ₹${mangoCeil?.currentPrice}`
    );

    // ------------------------------------------------------------------
    // TEST 6: Requirement 15 - Server-side bounds validation
    // ------------------------------------------------------------------
    console.log('\n--- Requirement 15: Server-Side Validation ---');
    // Try min > max
    const invalidMinMax = await apiRequest('/api/admin/pricing/products/1/config', 'PUT', {
      productId: 1,
      minCupPrice: 40.00,
      maxCupPrice: 30.00,
      defaultCupPrice: 35.00
    });
    record(
      'Server rejects minCupPrice > maxCupPrice',
      invalidMinMax.status === 400,
      `Status: ${invalidMinMax.status}`
    );

    // Try base < min
    const invalidBaseMin = await apiRequest('/api/admin/pricing/products/1/config', 'PUT', {
      productId: 1,
      minCupPrice: 20.00,
      maxCupPrice: 30.00,
      defaultCupPrice: 15.00
    });
    record(
      'Server rejects defaultCupPrice < minCupPrice',
      invalidBaseMin.status === 400,
      `Status: ${invalidBaseMin.status}`
    );

    // Try base > max
    const invalidBaseMax = await apiRequest('/api/admin/pricing/products/1/config', 'PUT', {
      productId: 1,
      minCupPrice: 20.00,
      maxCupPrice: 30.00,
      defaultCupPrice: 35.00
    });
    record(
      'Server rejects defaultCupPrice > maxCupPrice',
      invalidBaseMax.status === 400,
      `Status: ${invalidBaseMax.status}`
    );

    // Reset Mango to clean state
    await pg.query('UPDATE products SET min_cup_price = 20.00, default_cup_price = 25.00, max_cup_price = 30.00, current_cup_price = 25.00, target_sales_per_1_minute = 0.55 WHERE id = 1');
    await apiRequest('/api/admin/pricing/reset-all', 'POST');

  } finally {
    await pg.end();
  }

  console.log('\n======================================================================');
  const passCount = tests.filter(t => t.passed).length;
  console.log(`🏁 VERIFICATION COMPLETE: ${passCount}/${tests.length} CHECKS PASSED`);
  console.log('======================================================================\n');

  if (passCount !== tests.length) {
    process.exit(1);
  }
}

runVerification().catch(err => {
  console.error('Verification failure:', err);
  process.exit(1);
});
