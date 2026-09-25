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

const productsToTest = [
  { id: 1, name: 'Mango' },
  { id: 2, name: 'Lemon' },
  { id: 3, name: 'Mint' },
  { id: 4, name: 'Orange' },
  { id: 5, name: 'Strawberry' },
  { id: 6, name: 'Grape' },
  { id: 7, name: 'Lychee' },
  { id: 23, name: 'Thunder' }
];

async function runIsolationMatrix() {
  console.log('===============================================================');
  console.log('🧪 RUNNING CROSS-PRODUCT DEMAND ISOLATION MATRIX TEST (ALL 8 JUICES)');
  console.log('===============================================================\n');

  const token = getJwtToken('admin', 'ROLE_ADMIN');

  for (const targetProduct of productsToTest) {
    console.log(`\n--- 🛒 Testing Purchase of [${targetProduct.name}] (ID: ${targetProduct.id}) ---`);

    // 1. Reset state
    queryPgSql('DELETE FROM sales_order_items;');
    queryPgSql('DELETE FROM sales_orders;');
    queryPgSql('DELETE FROM price_history;');
    await httpRequest(`${API_BASE}/pricing/reset-all`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });

    // Capture initial prices
    const initialPricesStr = queryPgSql("SELECT id, current_cup_price FROM products WHERE id IN (1,2,3,4,5,6,7,23);");
    const initialPrices = {};
    initialPricesStr.split('\n').forEach(line => {
      const parts = line.split('|').map(s => s.trim());
      if (parts.length === 2) initialPrices[parseInt(parts[0])] = parseFloat(parts[1]);
    });

    // 2. Perform Checkout ONLY for targetProduct
    await httpRequest(`${API_BASE}/pos/checkout`, {
      method: 'POST',
      body: JSON.stringify({ items: [{ productId: targetProduct.id, quantity: 2, cupSizeMl: 250 }], paymentMethod: 'CASH' })
    });

    // Capture post-checkout prices
    const postPricesStr = queryPgSql("SELECT id, current_cup_price FROM products WHERE id IN (1,2,3,4,5,6,7,23);");
    const postPrices = {};
    postPricesStr.split('\n').forEach(line => {
      const parts = line.split('|').map(s => s.trim());
      if (parts.length === 2) postPrices[parseInt(parts[0])] = parseFloat(parts[1]);
    });

    let matrixPass = true;
    for (const p of productsToTest) {
      const initP = initialPrices[p.id];
      const postP = postPrices[p.id];
      if (p.id === targetProduct.id) {
        // Target product can stay same or increase based on demand ratio
        console.log(`   👉 Purchased Item [${p.name}]: ₹${initP.toFixed(2)} -> ₹${postP.toFixed(2)}`);
      } else {
        // Other products MUST NOT INCREASE!
        const increased = postP > initP;
        if (increased) {
          console.error(`   ❌ [FAIL]: Non-purchased item [${p.name}] increased from ₹${initP.toFixed(2)} to ₹${postP.toFixed(2)}!`);
          matrixPass = false;
        } else {
          console.log(`   ✅ [PASS]: Non-purchased item [${p.name}]: ₹${initP.toFixed(2)} -> ₹${postP.toFixed(2)} (Did not increase)`);
        }
      }
    }

    if (!matrixPass) {
      console.error(`\n❌ CROSS-PRODUCT ISOLATION TEST FAILED FOR PURCHASING ${targetProduct.name}`);
      process.exit(1);
    }
  }

  // Clean reset
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  console.log('\n===============================================================');
  console.log('🎉 ALL 8 PRODUCTS PASSED PERFECT CROSS-PRODUCT DEMAND ISOLATION!');
  console.log('===============================================================');
}

runIsolationMatrix().catch(err => {
  console.error('Test execution error:', err);
  process.exit(1);
});
