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

async function debug() {
  console.log('1️⃣ Resetting DB state & prices to ₹25.00...');
  queryPgSql('DELETE FROM sales_order_items;');
  queryPgSql('DELETE FROM sales_orders;');
  queryPgSql('DELETE FROM price_history;');
  const token = getJwtToken('admin', 'ROLE_ADMIN');
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  console.log('\nInitial DB Prices (All ₹25.00):');
  console.log(queryPgSql("SELECT id, name, current_cup_price FROM products ORDER BY id;"));

  console.log('\n🛒 Purchasing 1 cup of Orange (productId = 4)...');
  const checkoutRes = await httpRequest(`${API_BASE}/pos/checkout`, {
    method: 'POST',
    body: JSON.stringify({ items: [{ productId: 4, quantity: 1, cupSizeMl: 250 }], paymentMethod: 'CASH' })
  });

  console.log('Checkout Response:', checkoutRes.json);

  console.log('\nDB Prices after Purchasing Orange:');
  console.log(queryPgSql("SELECT id, name, current_cup_price FROM products ORDER BY id;"));

  console.log('\nSales Order Items in DB:');
  console.log(queryPgSql("SELECT id, sales_order_id, product_id, product_name, quantity FROM sales_order_items;"));

  console.log('\nPrice History Records Logged:');
  console.log(queryPgSql("SELECT product_id, old_price, new_price, price_change, reason, explanation FROM price_history ORDER BY id DESC LIMIT 10;"));
}

debug().catch(err => console.error(err));
