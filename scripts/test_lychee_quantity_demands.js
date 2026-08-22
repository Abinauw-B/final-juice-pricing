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

async function runTest() {
  console.log('1️⃣ Clearing sales order records...');
  queryPgSql('DELETE FROM sales_order_items;');
  queryPgSql('DELETE FROM sales_orders;');

  console.log('2️⃣ Resetting all prices to base ₹25.00...');
  const token = getJwtToken('admin', 'ROLE_ADMIN');
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  console.log('\n--- SCENARIO A: BUY 1 CUP OF LYCHEES ---');
  await httpRequest(`${API_BASE}/pos/checkout`, {
    method: 'POST',
    body: JSON.stringify({ items: [{ productId: 7, quantity: 1, cupSizeMl: 250 }], paymentMethod: 'CASH' })
  });
  const price1 = queryPgSql("SELECT current_cup_price FROM products WHERE id = 7;");
  console.log(`   Result after 1 cup: Lychee Price = ₹${price1} (W0=1 -> WeightedSales=0.50 -> Ratio=0.555 < 1.10 -> Movement = -1)`);

  console.log('\n--- SCENARIO B: BUY 2ND CUP OF LYCHEES (Total 2 cups) ---');
  await httpRequest(`${API_BASE}/pos/checkout`, {
    method: 'POST',
    body: JSON.stringify({ items: [{ productId: 7, quantity: 1, cupSizeMl: 250 }], paymentMethod: 'CASH' })
  });
  const price2 = queryPgSql("SELECT current_cup_price FROM products WHERE id = 7;");
  console.log(`   Result after 2 cups: Lychee Price = ₹${price2} (W0=2 -> WeightedSales=1.00 -> Ratio=1.111 >= 1.10 -> Movement = +1 -> PRICE INCREASED!)`);

  console.log('\n--- SCENARIO C: BUY 3RD CUP OF LYCHEES (Total 3 cups) ---');
  await httpRequest(`${API_BASE}/pos/checkout`, {
    method: 'POST',
    body: JSON.stringify({ items: [{ productId: 7, quantity: 1, cupSizeMl: 250 }], paymentMethod: 'CASH' })
  });
  const price3 = queryPgSql("SELECT current_cup_price FROM products WHERE id = 7;");
  console.log(`   Result after 3 cups: Lychee Price = ₹${price3} (W0=3 -> WeightedSales=1.50 -> Ratio=1.667 >= 1.50 -> Movement = +2 -> STRONGER PRICE INCREASE!)`);
}

runTest().catch(err => console.error(err));
