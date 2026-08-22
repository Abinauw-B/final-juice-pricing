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

async function testMango() {
  console.log('1️⃣ Resetting DB state & prices to ₹18.00 floor for testing...');
  queryPgSql('DELETE FROM sales_order_items;');
  queryPgSql('DELETE FROM sales_orders;');
  queryPgSql('DELETE FROM price_history;');
  const token = getJwtToken('admin', 'ROLE_ADMIN');
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  // Set all prices to ₹18.00 to replicate user state
  queryPgSql("UPDATE products SET current_cup_price = 18.00;");

  console.log('\nInitial DB Prices (All set to ₹18.00):');
  console.log(queryPgSql("SELECT id, name, current_cup_price, target_sales_per_2_minute FROM products WHERE id IN (1, 3, 4, 23) ORDER BY id;"));

  console.log('\n🛒 Single Purchase: Buying 1 cup of Mango (productId = 1)...');
  await httpRequest(`${API_BASE}/pos/checkout`, {
    method: 'POST',
    body: JSON.stringify({ items: [{ productId: 1, quantity: 1, cupSizeMl: 250 }], paymentMethod: 'CASH' })
  });

  console.log('\nDB Prices after Buying 1 cup of Mango:');
  console.log(queryPgSql("SELECT id, name, current_cup_price FROM products WHERE id IN (1, 3, 4, 23) ORDER BY id;"));

  console.log('\nPrice History for Mango after 1 cup:');
  console.log(queryPgSql("SELECT product_id, old_price, new_price, weighted_sales, target_sales, demand_ratio, reason FROM price_history WHERE product_id = 1 ORDER BY id DESC LIMIT 1;"));

  console.log('\n🛒 Second Purchase: Buying another 1 cup of Mango (Total 2 cups in W0)...');
  await httpRequest(`${API_BASE}/pos/checkout`, {
    method: 'POST',
    body: JSON.stringify({ items: [{ productId: 1, quantity: 1, cupSizeMl: 250 }], paymentMethod: 'CASH' })
  });

  console.log('\nDB Prices after Buying 2nd cup of Mango:');
  console.log(queryPgSql("SELECT id, name, current_cup_price FROM products WHERE id IN (1, 3, 4, 23) ORDER BY id;"));

  console.log('\nPrice History for Mango after 2 cups:');
  console.log(queryPgSql("SELECT product_id, old_price, new_price, weighted_sales, target_sales, demand_ratio, reason FROM price_history WHERE product_id = 1 ORDER BY id DESC LIMIT 1;"));
}

testMango().catch(err => console.error(err));
