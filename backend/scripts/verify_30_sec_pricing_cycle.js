const assert = require('assert');

const API_BASE = 'http://localhost:8088/api';

async function api(path, options = {}) {
  const url = `${API_BASE}${path}`;
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  const data = await res.json().catch(() => null);
  return { status: res.status, ok: res.ok, data };
}

async function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}

async function runTest() {
  console.log('================================================================================');
  console.log('🚀 TESTING 30-SECOND DYNAMIC PRICING SETTLEMENT ENGINE');
  console.log('================================================================================\n');

  // 1. Set Settlement Interval to 30s
  console.log('--- 1. SETTING SETTLEMENT INTERVAL TO 30 SECONDS ---');
  const timingRes = await api('/admin/pricing/timing', {
    method: 'PUT',
    body: JSON.stringify({ intervalSeconds: 30 })
  });
  assert(timingRes.ok, `Failed to set 30s timing: ${JSON.stringify(timingRes.data)}`);
  assert(timingRes.data.intervalSeconds === 30, `Interval should be 30s, got ${timingRes.data.intervalSeconds}`);
  console.log('  ✅ PASS: Set settlement interval to 30 seconds');

  // Verify GET /pricing/timing
  const getTiming = await api('/pricing/timing');
  assert(getTiming.ok && getTiming.data.intervalSeconds === 30, `GET /pricing/timing confirms 30s (got ${getTiming.data?.intervalSeconds})`);
  console.log(`  ✅ PASS: GET /pricing/timing returns intervalSeconds=30, label="${getTiming.data.label}", nextSettlementAt=${getTiming.data.nextSettlementAt}`);

  // 2. Set Product Floor Limit (Mango Floor: ₹20.00, Ceiling: ₹30.00, Base: ₹25.00)
  console.log('\n--- 2. VERIFYING PRODUCT-SPECIFIC FLOOR LIMIT PROTECTION (MANGO FLOOR = ₹20.00) ---');
  const prods = await api('/pos/products');
  const mango = prods.data.find(p => p.flavour === 'MANGO' || p.name.includes('Mango') || p.id === 1);
  assert(mango, 'Mango product found');

  // Update Mango with Floor ₹20.00, Base ₹25.00, Current ₹25.00
  const updateMango = await api(`/admin/pricing/products/${mango.id}/config`, {
    method: 'PUT',
    body: JSON.stringify({
      minCupPrice: 20.00,
      maxCupPrice: 30.00,
      defaultCupPrice: 25.00,
      currentCupPrice: 25.00,
      targetSales: 0.55
    })
  });
  assert(updateMango.ok, 'Mango config updated');
  console.log('  ✅ PASS: Mango configured with Floor ₹20.00, Base ₹25.00, Ceiling ₹30.00');

  // 3. Trigger 30-Second Consecutive Settlement Cycles
  console.log('\n--- 3. EXECUTING MULTIPLE 30-SECOND SETTLEMENT CYCLES ---');
  const cycle1 = await api('/pricing/evaluate', { method: 'POST' });
  assert(cycle1.ok, 'Cycle 1 executed');
  console.log(`  ✅ PASS: Cycle 1 executed: Evaluated ${cycle1.data.length || cycle1.data.evaluatedProductsCount} products`);

  const mangoAfterC1 = await api(`/pos/products/${mango.id}`);
  console.log(`  Cycle 1 Mango price: ₹${mangoAfterC1.data.currentCupPrice} (Floor is ₹${mangoAfterC1.data.minCupPrice})`);

  // Wait 1 second and execute Cycle 2 (at T+1s / sub-minute timestamp)
  await sleep(1000);
  const cycle2 = await api('/pricing/evaluate', { method: 'POST' });
  assert(cycle2.ok, 'Cycle 2 executed without duplicate window collision');
  console.log(`  ✅ PASS: Cycle 2 executed successfully`);

  // Execute multiple decay rounds until floor is reached
  console.log('\n--- 4. TESTING FLOOR CLAMPING: ZERO SALES MUST STOP AT PRODUCT FLOOR ₹20.00 ---');
  for (let i = 1; i <= 6; i++) {
    await sleep(200);
    await api('/pricing/evaluate', { method: 'POST' });
  }

  const mangoFinal = await api(`/pos/products/${mango.id}`);
  console.log(`  Mango final price after multiple zero-demand settlements: ₹${mangoFinal.data.currentCupPrice}`);
  assert(Number(mangoFinal.data.currentCupPrice) === 20.00, `Mango price must be clamped at ₹20.00 (got ₹${mangoFinal.data.currentCupPrice})`);
  assert(Number(mangoFinal.data.currentCupPrice) >= Number(mangoFinal.data.minCupPrice), `Price ₹${mangoFinal.data.currentCupPrice} must be >= minCupPrice ₹${mangoFinal.data.minCupPrice}`);
  console.log('  ✅ PASS: Mango price strictly clamped at product floor ₹20.00 (never drops to ₹18.00)');

  // 5. Test High Demand on 30-second interval (+₹1 step)
  console.log('\n--- 5. TESTING 30-SECOND HIGH DEMAND SURGE (+₹1 STEP) ---');
  // Record sales in 30s window: 2 cups
  const orderRes = await api('/pos/orders', {
    method: 'POST',
    body: JSON.stringify({
      cashierId: 1,
      items: [{ productId: mango.id, quantity: 2, unitPrice: 20.00 }]
    })
  });
  assert(orderRes.ok, 'Order placed');
  console.log('  Purchased 2 cups of Mango');

  const surgeCycle = await api('/pricing/evaluate', { method: 'POST' });
  assert(surgeCycle.ok, 'Surge cycle evaluated');

  const mangoSurged = await api(`/pos/products/${mango.id}`);
  console.log(`  Mango price after surge: ₹${mangoSurged.data.currentCupPrice}`);
  assert(Number(mangoSurged.data.currentCupPrice) === 21.00, `Price should surge by +₹1.00 to ₹21.00 (got ₹${mangoSurged.data.currentCupPrice})`);
  console.log('  ✅ PASS: High demand in 30-second cycle increases price by exactly +₹1.00 (₹20.00 -> ₹21.00)');

  console.log('\n================================================================================');
  console.log('🏁 ALL 30-SECOND SETTLEMENT & FLOOR PROTECTION TESTS PASSED!');
  console.log('================================================================================\n');
}

runTest().catch(err => {
  console.error('❌ Test failed:', err);
  process.exit(1);
});
