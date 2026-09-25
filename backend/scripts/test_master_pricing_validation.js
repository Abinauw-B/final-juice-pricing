const http = require('http');
const crypto = require('crypto');
const { execSync } = require('child_process');

const API_BASE = 'http://localhost:8088/api';
const PG_BIN = '"D:\\New folder\\bin\\';
const env = { ...process.env, PGPASSWORD: 'postgres' };

function getJwtToken(username, role) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ sub: username, role: role, iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url');
  const secret = 'PubExchangeSuperSecretKeyForJWTAuth2026EnterpriseProductionEngine!';
  const signature = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${signature}`;
}

function queryPgSql(sql) {
  const cmd = `${PG_BIN}psql.exe" -U postgres -d retailposdb -t -c "${sql}"`;
  return execSync(cmd, { env, encoding: 'utf8' }).trim();
}

function httpRequest(urlStr, options = {}) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(urlStr);
    const reqOptions = {
      hostname: parsed.hostname,
      port: parsed.port,
      path: parsed.pathname + parsed.search,
      method: options.method || 'GET',
      headers: options.headers || {}
    };

    if (options.body) {
      reqOptions.headers['Content-Type'] = reqOptions.headers['Content-Type'] || 'application/json';
      reqOptions.headers['Content-Length'] = Buffer.byteLength(options.body);
    }

    const req = http.request(reqOptions, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(body); } catch (e) {}
        resolve({ status: res.statusCode, body, json });
      });
    });

    req.on('error', (err) => reject(err));
    if (options.body) req.write(options.body);
    req.end();
  });
}

async function runMasterValidation() {
  console.log('====================================================');
  console.log('🧪 RUNNING MASTER PRICING SPECIFICATION VALIDATION');
  console.log('====================================================\n');

  const token = getJwtToken('admin', 'ROLE_ADMIN');

  // 1. Reset Database State
  console.log('1️⃣ Clearing sales order history and resetting all prices to base ₹25.00...');
  queryPgSql('DELETE FROM sales_order_items;');
  queryPgSql('DELETE FROM sales_orders;');
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  // 2. Test Multi-Step Downward Pricing (Zero Demand Decay)
  console.log('\n--- 📉 TESTING ZERO DEMAND DECAY PROGRESSION (₹25 -> ₹23 -> ₹21 -> ₹19 -> ₹18) ---');
  let currentPrice = parseFloat(queryPgSql("SELECT current_cup_price FROM products WHERE id = 1;"));
  console.log(`Starting Mango Price: ₹${currentPrice.toFixed(2)}`);

  const expectedDecay = [23.0, 21.0, 19.0, 18.0, 18.0];
  for (let i = 0; i < expectedDecay.length; i++) {
    const res = await httpRequest(`${API_BASE}/pricing/evaluate`, { method: 'GET' });
    const dbPrice = parseFloat(queryPgSql("SELECT current_cup_price FROM products WHERE id = 1;"));
    const priceVersion = parseInt(queryPgSql("SELECT price_version FROM products WHERE id = 1;"));
    const targetExp = expectedDecay[i];
    
    const pass = (dbPrice === targetExp);
    console.log(`Cycle ${i + 1}: DB Price = ₹${dbPrice.toFixed(2)} | Target = ₹${targetExp.toFixed(2)} | Version = ${priceVersion} | ${pass ? '✅ [PASS]' : '❌ [FAIL]'}`);
    if (!pass) process.exit(1);
  }

  // 3. Test Multi-Step Upward Pricing (High Current Demand)
  console.log('\n--- 📈 TESTING HIGH CURRENT DEMAND PROGRESSION (₹25 -> ₹26 -> ₹27 -> ₹28 -> ₹29 -> ₹30) ---');
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  const expectedIncreases = [26.0, 27.0, 28.0, 29.0, 30.0];
  for (let i = 0; i < expectedIncreases.length; i++) {
    // Buy 1 cup of Thunder (productId = 23)
    await httpRequest(`${API_BASE}/pos/checkout`, {
      method: 'POST',
      body: JSON.stringify({ items: [{ productId: 23, quantity: 1, cupSizeMl: 250 }], paymentMethod: 'CASH' })
    });

    const dbPrice = parseFloat(queryPgSql("SELECT current_cup_price FROM products WHERE id = 23;"));
    const priceVersion = parseInt(queryPgSql("SELECT price_version FROM products WHERE id = 23;"));
    const targetExp = expectedIncreases[i];

    const pass = (dbPrice === targetExp);
    console.log(`Step ${i + 1}: Thunder Price = ₹${dbPrice.toFixed(2)} | Target = ₹${targetExp.toFixed(2)} | Version = ${priceVersion} | ${pass ? '✅ [PASS]' : '❌ [FAIL]'}`);
    if (!pass) process.exit(1);
  }

  // 4. Test Unpurchased Product Freeze (Zero W0 Sales)
  console.log('\n--- 🧊 TESTING UNPURCHASED PRODUCT FREEZE (Zero W0 Sales) ---');
  const lemonPrice = parseFloat(queryPgSql("SELECT current_cup_price FROM products WHERE id = 2;"));
  console.log(`Lemon Price (Unpurchased during Thunder buying): ₹${lemonPrice.toFixed(2)}`);
  const lemonPass = (lemonPrice <= 25.0); // Lemon must NOT increase above base when only Thunder is bought!
  console.log(`Lemon Unpurchased Freeze Status: ${lemonPass ? '✅ [PASS - Lemon did not increase]' : '❌ [FAIL - Lemon increased without purchases]'}`);
  if (!lemonPass) process.exit(1);

  // 5. Test Audit Price History Integrity
  console.log('\n--- 📜 TESTING PRICE HISTORY AUDIT TRAIL INTEGRITY ---');
  const auditCount = parseInt(queryPgSql("SELECT count(*) FROM price_history;"));
  console.log(`Total Price History Audit Records Logged: ${auditCount}`);
  const auditPass = auditCount > 0;
  console.log(`Audit Record Persistence: ${auditPass ? '✅ [PASS]' : '❌ [FAIL]'}`);

  // Clean Reset
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  console.log('\n====================================================');
  console.log('🎉 ALL MASTER PRICING SPECIFICATION TESTS PASSED!');
  console.log('====================================================');
}

runMasterValidation().catch(err => {
  console.error('Test execution error:', err);
  process.exit(1);
});
