const http = require('http');
const crypto = require('crypto');
const { execSync } = require('child_process');
const WebSocket = require('ws');

const API_BASE = 'http://localhost:8088/api';
const PG_BIN = '"D:\\New folder\\bin\\';
const env = { ...process.env, PGPASSWORD: 'postgres' };

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
        resolve({ status: res.statusCode, headers: res.headers, body, json });
      });
    });

    req.on('error', (err) => reject(err));
    if (options.body) req.write(options.body);
    req.end();
  });
}

function getJwtToken(username, role) {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    sub: username,
    role: role,
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600
  })).toString('base64url');
  
  const secret = 'PubExchangeSuperSecretKeyForJWTAuth2026EnterpriseProductionEngine!';
  const signature = crypto.createHmac('sha256', secret)
    .update(`${header}.${payload}`)
    .digest('base64url');

  return `${header}.${payload}.${signature}`;
}

function queryPgSql(sql) {
  const cmd = `${PG_BIN}psql.exe" -U postgres -d retailposdb -t -c "${sql}"`;
  return execSync(cmd, { env, encoding: 'utf8' }).trim();
}

async function runE2EValidation() {
  console.log('====================================================================');
  console.log('🔥 FRESH END-TO-END RUNTIME VALIDATION: POS PURCHASE → LIVE PRICE');
  console.log('====================================================================\n');

  const adminToken = getJwtToken('admin', 'ROLE_ADMIN');

  // Step 1: Reset all market prices to ₹25 base
  console.log('1️⃣ Step 1: Resetting all product prices to base state ₹25.00...');
  await httpRequest(`${API_BASE}/pricing/reset-all`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${adminToken}` }
  });

  // Query initial price and version from DB
  const initialDbPrice = queryPgSql("SELECT current_cup_price FROM products WHERE flavour = 'MANGO'");
  const initialDbVersion = queryPgSql("SELECT price_version FROM products WHERE flavour = 'MANGO'");
  console.log(`   - PostgreSQL Mango Initial Price: ₹${initialDbPrice}`);
  console.log(`   - PostgreSQL Mango Initial Version: ${initialDbVersion}`);

  // Step 2: Establish STOMP over raw WebSocket Connection
  console.log('\n2️⃣ Step 2: Connecting STOMP WebSocket subscriber to ws://localhost:8088/ws/websocket...');
  let stompReceivedPayload = null;

  const wsUrl = 'ws://localhost:8088/ws/websocket';
  const ws = new WebSocket(wsUrl);

  await new Promise((resolve, reject) => {
    ws.on('open', () => {
      console.log('   - WebSocket Connection Established');
      ws.send('CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\0');
    });

    ws.on('message', (data) => {
      const msgStr = data.toString();
      if (msgStr.startsWith('CONNECTED')) {
        console.log('   - STOMP Protocol Handshake Successful');
        ws.send('SUBSCRIBE\nid:sub-0\ndestination:/topic/prices\n\n\0');
        resolve();
      } else if (msgStr.startsWith('MESSAGE')) {
        const bodyStart = msgStr.indexOf('\n\n') + 2;
        const bodyEnd = msgStr.lastIndexOf('\0');
        const jsonBody = msgStr.substring(bodyStart, bodyEnd > bodyStart ? bodyEnd : msgStr.length).trim();
        try {
          stompReceivedPayload = JSON.parse(jsonBody);
          console.log('⚡ STOMP /topic/prices Event Received in Real-Time!');
        } catch (e) {}
      }
    });

    ws.on('error', (err) => reject(err));
  });

  // Step 3: Perform POS Checkout for 4 Mango cups (W0 = 4)
  console.log('\n3️⃣ Step 3: Submitting POS Checkout for Fresh Mango Juice x 4 cups...');
  const checkoutPayload = JSON.stringify({
    items: [{ productId: 1, quantity: 4, cupSizeMl: 250 }],
    paymentMethod: 'CASH',
    idempotencyKey: `E2E-LIVE-TEST-${Date.now()}`
  });

  const checkoutRes = await httpRequest(`${API_BASE}/pos/checkout`, {
    method: 'POST',
    body: checkoutPayload
  });

  if (checkoutRes.status !== 200 || !checkoutRes.json || !checkoutRes.json.success) {
    console.error('❌ Checkout request failed:', checkoutRes.body);
    process.exit(1);
  }

  const orderId = checkoutRes.json.orderId;
  console.log(`✅ POS Checkout Succeeded! Order ID: ${orderId}`);

  // Wait 1 second for post-commit trigger and STOMP message
  await new Promise(r => setTimeout(r, 1000));

  // Step 4: Verify PostgreSQL persistence
  console.log('\n4️⃣ Step 4: Verifying PostgreSQL database state immediately after checkout & settlement...');
  const postDbPrice = queryPgSql("SELECT current_cup_price FROM products WHERE flavour = 'MANGO'");
  const postDbVersion = queryPgSql("SELECT price_version FROM products WHERE flavour = 'MANGO'");
  const orderItemsQty = queryPgSql(`SELECT quantity FROM sales_order_items WHERE order_id = ${orderId} AND product_id = 1`);
  const historyCount = queryPgSql("SELECT COUNT(*) FROM price_history WHERE product_id = 1");

  console.log(`   - PostgreSQL Order Items Qty: ${orderItemsQty} cups`);
  console.log(`   - PostgreSQL Updated Mango Price: ₹${postDbPrice} (Was ₹${initialDbPrice})`);
  console.log(`   - PostgreSQL Updated Price Version: ${postDbVersion} (Was ${initialDbVersion})`);
  console.log(`   - PostgreSQL Price History Record Count: ${historyCount}`);

  // Step 5: Verify STOMP Broadcast Payload
  console.log('\n5️⃣ Step 5: Verifying STOMP Broadcast Payload Integrity...');
  const priceList = stompReceivedPayload?.updatedPrices || stompReceivedPayload?.changes || (Array.isArray(stompReceivedPayload) ? stompReceivedPayload : null);
  if (!priceList) {
    console.error('❌ STOMP payload not received or malformed!');
    process.exit(1);
  }

  const mangoUpdate = priceList.find(p => p.beverageId === 1 || p.id === 1 || p.flavour === 'MANGO');
  console.log(`   - STOMP Price Update: ₹${mangoUpdate.currentPrice || mangoUpdate.currentCupPrice}`);
  console.log(`   - STOMP Price Version: ${mangoUpdate.priceVersion}`);
  console.log(`   - STOMP Demand Category: ${mangoUpdate.demandLevelCategory}`);
  console.log(`   - STOMP Price Change: +₹${mangoUpdate.priceChange}`);

  // Step 6: Verify Customer POS API & Admin Live Prices API
  console.log('\n6️⃣ Step 6: Verifying Customer POS & Admin API State Synchronization...');
  const posProductsRes = await httpRequest(`${API_BASE}/pos/products`);
  const adminLiveRes = await httpRequest(`${API_BASE}/pricing/live`);

  const posMango = posProductsRes.json.find(p => p.id === 1);
  const adminMango = adminLiveRes.json.find(p => p.id === 1);

  console.log(`   - Customer POS API Price: ₹${posMango.currentCupPrice}`);
  console.log(`   - Admin Live API Price: ₹${adminMango.currentCupPrice}`);

  // Final Multi-Node Verification Matrix
  console.log('\n====================================================================');
  console.log('🎯 LIVE SYSTEM SYNCHRONIZATION MATRIX');
  console.log('====================================================================');
  console.log(`   POSTGRESQL DB PRICE  : ₹${postDbPrice}`);
  console.log(`   STOMP WEBSOCKET PRICE: ₹${mangoUpdate.currentPrice}`);
  console.log(`   CUSTOMER POS PRICE   : ₹${posMango.currentCupPrice}`);
  console.log(`   ADMIN PANEL PRICE    : ₹${adminMango.currentCupPrice}`);
  console.log(`   PAGE REFRESH REQUIRED: NO (STOMP Push Driven)`);
  console.log('====================================================================\n');

  if (
    parseFloat(postDbPrice) > 25.00 &&
    parseFloat(mangoUpdate.currentPrice) === parseFloat(postDbPrice) &&
    parseFloat(posMango.currentCupPrice) === parseFloat(postDbPrice) &&
    parseFloat(adminMango.currentCupPrice) === parseFloat(postDbPrice) &&
    parseInt(postDbVersion) > parseInt(initialDbVersion)
  ) {
    console.log('🚀 ALL CHECKS PASSED: POS BUY → POSTGRESQL → STOMP → POS UI PIPELINE VERIFIED!');
    ws.close();
    process.exit(0);
  } else {
    console.error('❌ Mismatch detected across system nodes!');
    ws.close();
    process.exit(1);
  }
}

runE2EValidation().catch(err => {
  console.error('E2E test error:', err);
  process.exit(1);
});
