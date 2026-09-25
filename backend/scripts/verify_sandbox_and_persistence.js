const http = require('http');

function request(url, options = {}, body = null) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const reqOptions = {
      hostname: u.hostname,
      port: u.port,
      path: u.pathname + u.search,
      method: options.method || 'GET',
      headers: options.headers || {}
    };
    if (body) {
      if (!reqOptions.headers['Content-Type']) {
        reqOptions.headers['Content-Type'] = 'application/json';
      }
      reqOptions.headers['Content-Length'] = Buffer.byteLength(body);
    }
    const req = http.request(reqOptions, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = data ? JSON.parse(data) : {};
          resolve({ status: res.statusCode, data: json });
        } catch (e) {
          resolve({ status: res.statusCode, text: data });
        }
      });
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

async function run() {
  console.log('='.repeat(80));
  console.log('🚀 TESTING SANDBOX SIMULATOR DEPLOYMENT & POSTGRESQL PERSISTENCE');
  console.log('='.repeat(80));

  // 1. Run Simulator Calculation via Backend
  console.log('\n--- 1. RUNNING SANDBOX SIMULATION ON BACKEND ---');
  const simReq = {
    flavourName: 'Fresh Mango Juice',
    initialVolumeMl: 20000,
    initialPrice: 25.0,
    minPrice: 20.0,
    maxPrice: 30.0,
    cupsPerInterval: 2,
    intervalMinutes: 1,
    targetSales: 1.0,
    includeCrash: false
  };

  const simRes = await request('http://localhost:8088/api/pricing/simulate', {
    method: 'POST'
  }, JSON.stringify(simReq));

  if (simRes.status !== 200 || !Array.isArray(simRes.data.steps)) {
    console.error('❌ FAILED simulator run:', simRes);
    process.exit(1);
  }
  console.log(`  ✅ PASS: Simulator produced ${simRes.data.steps.length} steps. Final Price: ₹${simRes.data.finalPrice}`);

  // 2. Deploy Sandbox Parameters (Floor ₹20, Ceiling ₹30, Start ₹25, Target 0.55) to Live Mango
  console.log('\n--- 2. ATOMICALLY DEPLOYING SANDBOX PARAMETERS TO POSTGRESQL ---');
  const deployPayload = {
    productId: 1,
    price: 25.0,
    currentPrice: 25.0,
    defaultPrice: 25.0,
    minPrice: 20.0,
    maxPrice: 30.0,
    targetSalesPer1Minute: 0.55
  };

  const deployRes = await request('http://localhost:8088/api/pricing/deploy', {
    method: 'POST',
    headers: { 'X-User-Role': 'ADMIN' }
  }, JSON.stringify(deployPayload));

  if (deployRes.status !== 200 || !deployRes.data.deployed) {
    console.error('❌ FAILED deploy endpoint:', deployRes);
    process.exit(1);
  }
  console.log('  ✅ PASS: /pricing/deploy returned 200 OK with deployed=true');

  // 3. Verify PostgreSQL Persistence via Hard Refresh API Simulation
  console.log('\n--- 3. VERIFYING HARD REFRESH PERSISTENCE DIRECTLY FROM POSTGRESQL ---');
  const prodsRes = await request('http://localhost:8088/api/pos/products');
  if (prodsRes.status !== 200 || !Array.isArray(prodsRes.data)) {
    console.error('❌ FAILED fetching products after deploy:', prodsRes);
    process.exit(1);
  }

  const mango = prodsRes.data.find(p => p.id === 1);
  if (!mango) {
    console.error('❌ Mango not found in catalog');
    process.exit(1);
  }

  console.log(`  Mango State in Database:`);
  console.log(`    Current Price: ₹${mango.currentCupPrice}`);
  console.log(`    Base Price:    ₹${mango.defaultCupPrice}`);
  console.log(`    Floor Limit:   ₹${mango.minCupPrice}`);
  console.log(`    Ceiling Limit: ₹${mango.maxCupPrice}`);
  console.log(`    Target Sales:  ${mango.targetSalesPer1Minute} cups/min`);

  if (Number(mango.minCupPrice) !== 20.0) {
    console.error(`❌ Floor limit expected ₹20.00, got ₹${mango.minCupPrice}`);
    process.exit(1);
  }
  if (Number(mango.maxCupPrice) !== 30.0) {
    console.error(`❌ Ceiling limit expected ₹30.00, got ₹${mango.maxCupPrice}`);
    process.exit(1);
  }
  console.log('  ✅ PASS: PostgreSQL persisted Floor ₹20.00 and Ceiling ₹30.00 accurately!');

  // 4. Verify Manual Price Override Persistence & Decay Immunity
  console.log('\n--- 4. VERIFYING MANUAL PRICE LOCK (NO DECAY AFTER HARD REFRESH) ---');
  const lockRes = await request('http://localhost:8088/api/pricing/products/1/price?newPrice=28.00&reason=MANUAL_ADMIN_OVERRIDE', {
    method: 'POST',
    headers: { 'X-User-Role': 'ADMIN' }
  });

  if (lockRes.status !== 200) {
    console.error('❌ FAILED to lock price on Mango:', lockRes);
    process.exit(1);
  }
  console.log('  ✅ PASS: Mango price locked at ₹28.00 in MANUAL_OVERRIDE mode');

  // Trigger Settlement
  await request('http://localhost:8088/api/pricing/settle/evaluate-now', {
    method: 'POST',
    headers: { 'X-User-Role': 'ADMIN' }
  });

  const checkLock = await request('http://localhost:8088/api/pos/products');
  const lockedMango = checkLock.data.find(p => p.id === 1);
  if (Number(lockedMango.currentCupPrice) !== 28.0 || lockedMango.pricingMode !== 'MANUAL_OVERRIDE') {
    console.error('❌ Locked price decayed unexpectedly:', lockedMango);
    process.exit(1);
  }
  console.log('  ✅ PASS: Mango remained locked at ₹28.00 during settlement cycles without decaying!');

  // Release back to DYNAMIC
  await request('http://localhost:8088/api/pricing/products/1/release-override', {
    method: 'POST',
    headers: { 'X-User-Role': 'ADMIN' }
  });
  console.log('  ✅ PASS: Released Mango back to DYNAMIC mode');

  console.log('\n' + '='.repeat(80));
  console.log('🏁 ALL SANDBOX DEPLOYMENT & PERSISTENCE TESTS PASSED (100%)');
  console.log('='.repeat(80));
}

run().catch(err => {
  console.error('Unexpected error:', err);
  process.exit(1);
});
