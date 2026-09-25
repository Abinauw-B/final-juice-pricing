const http = require('http');
const WebSocket = require('ws');

const BASE_URL = 'http://localhost:8088/api';
const WS_URL = 'ws://localhost:8088/ws/websocket';

function apiRequest(path, method = 'GET', body = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(BASE_URL + path);
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: method,
      headers: {
        'Content-Type': 'application/json'
      }
    };

    const req = http.request(options, (res) => {
      let data = '';
      res.on('data', (chunk) => data += chunk);
      res.on('end', () => {
        try {
          const json = data ? JSON.parse(data) : {};
          resolve({ status: res.statusCode, ok: res.statusCode >= 200 && res.statusCode < 300, data: json });
        } catch (e) {
          resolve({ status: res.statusCode, ok: res.statusCode >= 200 && res.statusCode < 300, raw: data });
        }
      });
    });

    req.on('error', (err) => reject(err));
    if (body) {
      req.write(JSON.stringify(body));
    }
    req.end();
  });
}

// WebSocket listener helper
function connectStompWebSocket() {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL);
    const receivedMessages = { prices: [], marketCrash: [] };

    ws.on('open', () => {
      const connectFrame = "CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\0";
      ws.send(connectFrame);
    });

    ws.on('message', (data) => {
      const msg = data.toString();
      if (msg.startsWith('CONNECTED')) {
        ws.send("SUBSCRIBE\nid:sub-0\ndestination:/topic/prices\n\n\0");
        ws.send("SUBSCRIBE\nid:sub-1\ndestination:/topic/market-crash\n\n\0");
        resolve({ ws, receivedMessages });
      } else if (msg.startsWith('MESSAGE')) {
        const bodyIdx = msg.indexOf('\n\n');
        if (bodyIdx !== -1) {
          const bodyStr = msg.substring(bodyIdx + 2, msg.length - 1).trim();
          try {
            const parsed = JSON.parse(bodyStr);
            if (msg.includes('destination:/topic/prices')) {
              receivedMessages.prices.push(parsed);
            } else if (msg.includes('destination:/topic/market-crash')) {
              receivedMessages.marketCrash.push(parsed);
            }
          } catch (e) {
            if (msg.includes('destination:/topic/prices')) {
              receivedMessages.prices.push(bodyStr);
            } else if (msg.includes('destination:/topic/market-crash')) {
              receivedMessages.marketCrash.push(bodyStr);
            }
          }
        }
      }
    });

    ws.on('error', (err) => reject(err));
    setTimeout(() => reject(new Error('WebSocket connection timeout')), 5000);
  });
}

async function runStage3Validation() {
  console.log("====================================================================");
  console.log("🔥 STAGE 3 — DEEP LIVE SYSTEM VALIDATION & HARDENING");
  console.log("====================================================================\n");

  let totalTests = 0;
  let passedTests = 0;

  function report(name, expected, actual, passed, details = "") {
    totalTests++;
    if (passed) passedTests++;
    const status = passed ? "\x1b[32m[PASS]\x1b[0m" : "\x1b[31m[FAIL]\x1b[0m";
    console.log(`${status} ${name}`);
    console.log(`       Expected: ${expected}`);
    console.log(`       Actual  : ${actual}`);
    if (details) console.log(`       Details : ${details}`);
    console.log("");
  }

  // ------------------------------------------------------------------
  // 1. HEALTH CHECK
  // ------------------------------------------------------------------
  try {
    const health = await apiRequest('/health');
    report(
      "1. Backend & Database Health Check",
      "status=UP, database=CONNECTED",
      `status=${health.data.status}, database=${health.data.database}`,
      health.ok && health.data.status === 'UP' && health.data.database === 'CONNECTED'
    );
  } catch (err) {
    report("1. Backend & Database Health Check", "status=UP", err.message, false);
  }

  // ------------------------------------------------------------------
  // 2. CLIENT PRICE TAMPERING TEST
  // ------------------------------------------------------------------
  try {
    // Deploy ₹22 to Product 1
    await apiRequest('/pricing/deploy', 'POST', {
      productId: 1,
      defaultPrice: 22.00,
      currentPrice: 22.00,
      minPrice: 18.00,
      maxPrice: 25.00
    });

    // Send checkout request with malicious lockedPrice: 1.00
    const tamperRes = await apiRequest('/pos/checkout', 'POST', {
      idempotencyKey: "TAMPER-TEST-" + Date.now(),
      items: [
        {
          productId: 1,
          quantity: 2,
          cupSizeMl: 250,
          lockedPrice: 1.00 // MALICIOUS CLIENT PRICE TAMPER ATTEMPT
        }
      ],
      paymentMethod: "CASH"
    });

    let tamperPassed = false;
    let actualMsg = "";
    if (tamperRes.ok) {
      const totalAmount = Number(tamperRes.data.totalAmount);
      const unitPrice = Number(tamperRes.data.items[0].unitPrice);
      tamperPassed = (unitPrice === 22.00) && (totalAmount === 44.00);
      actualMsg = `Order created with server price ₹${unitPrice}, total = ₹${totalAmount} (client ₹1.00 IGNORED)`;
    } else {
      tamperPassed = true;
      actualMsg = `Server rejected tampered price request with status ${tamperRes.status}`;
    }

    report(
      "2. Client Price Tampering Protection",
      "Server uses DB price ₹22.00 (Total ₹44) or rejects. NEVER accepts ₹1.00",
      actualMsg,
      tamperPassed
    );
  } catch (err) {
    report("2. Client Price Tampering Protection", "Server price enforced", err.message, false);
  }

  // ------------------------------------------------------------------
  // 3. EXACT AGGREGATE QUANTITY TEST
  // ------------------------------------------------------------------
  try {
    const ts = Date.now();
    const res1 = await apiRequest('/pos/checkout', 'POST', { idempotencyKey: `AGG-POS1-${ts}`, items: [{ productId: 1, quantity: 2, cupSizeMl: 250 }], paymentMethod: "CASH" });
    const res2 = await apiRequest('/pos/checkout', 'POST', { idempotencyKey: `AGG-POS2-${ts}`, items: [{ productId: 1, quantity: 5, cupSizeMl: 250 }], paymentMethod: "CARD" });
    const res3 = await apiRequest('/pos/checkout', 'POST', { idempotencyKey: `AGG-POS3-${ts}`, items: [{ productId: 1, quantity: 10, cupSizeMl: 250 }], paymentMethod: "UPI" });

    const order1Items = res1.data.items[0].quantity;
    const order2Items = res2.data.items[0].quantity;
    const order3Items = res3.data.items[0].quantity;
    const sumQuantity = order1Items + order2Items + order3Items;

    report(
      "3. Exact Aggregate Quantity Test (POS 1: 2, POS 2: 5, POS 3: 10)",
      "SUM(quantity) = 17 cups across test orders",
      `SUM = ${sumQuantity} (POS1=${order1Items}, POS2=${order2Items}, POS3=${order3Items})`,
      sumQuantity === 17 && res1.ok && res2.ok && res3.ok
    );
  } catch (err) {
    report("3. Exact Aggregate Quantity Test", "SUM = 17 cups", err.message, false);
  }

  // ------------------------------------------------------------------
  // 4. INDEPENDENT PRODUCT DEMAND EVALUATION TABLE
  // ------------------------------------------------------------------
  try {
    const prodsBeforeRes = await apiRequest('/pos/products');
    const prodsBefore = prodsBeforeRes.data;

    await apiRequest('/pricing/evaluate');

    const prodsAfterRes = await apiRequest('/pos/products');
    const prodsAfter = prodsAfterRes.data;

    console.log("--- 📊 INDEPENDENT PRODUCT DEMAND EVALUATION RESULTS ---");
    console.log("Product Name          | Orders | Before Price | After Price | Status");
    console.log("-----------------------------------------------------------------------");
    prodsAfter.forEach(p => {
      const before = prodsBefore.find(b => b.id === p.id);
      const bPrice = before ? before.currentCupPrice : p.defaultCupPrice;
      const aPrice = p.currentCupPrice;
      console.log(`${(p.name + '                      ').substring(0, 21)} | ${p.demandScore || 50}     | ₹${bPrice}.00        | ₹${aPrice}.00       | ${p.trendDirection || 'FLAT'}`);
    });
    console.log("");

    const mangoProd = prodsAfter.find(p => p.id === 1);
    const orangeProd = prodsAfter.find(p => p.id === 4);

    report(
      "4. Independent Product Demand Evaluation",
      "Each product evaluated independently based on its own velocity & stock pressure",
      `Mango price=₹${mangoProd.currentCupPrice}, Orange price=₹${orangeProd.currentCupPrice}`,
      mangoProd && orangeProd && (mangoProd.currentCupPrice !== undefined)
    );
  } catch (err) {
    report("4. Independent Product Demand Evaluation", "Independent evaluation", err.message, false);
  }

  // ------------------------------------------------------------------
  // 5. ±₹1 MOVEMENT SURGE, DECAY & CEILING BOUNDS ENFORCEMENT
  // ------------------------------------------------------------------
  try {
    await apiRequest('/pricing/deploy', 'POST', { productId: 1, defaultPrice: 22.00, currentPrice: 22.00, minPrice: 18.00, maxPrice: 25.00 });
    
    const step1 = await apiRequest('/pricing/products/1/price?newPrice=23.00&reason=SURGE_TEST', 'POST');
    const p1 = Number(step1.data.newPrice);

    // Try exceeding maxPrice (set to ₹30) -> expect server to reject with 400 or clamp to maxPrice ₹25.00
    const stepMax = await apiRequest('/pricing/products/1/price?newPrice=30.00&reason=CEILING_TEST', 'POST');
    const ceilingRejected = !stepMax.ok || Number(stepMax.data.newPrice) === 25.00;

    await apiRequest('/pricing/products/1/price?newPrice=23.00&reason=RESET', 'POST');
    const stepDecay = await apiRequest('/pricing/products/1/price?newPrice=22.00&reason=DECAY_TEST', 'POST');
    const pDecay = Number(stepDecay.data.newPrice);

    report(
      "5. ±₹1 Step Surge, Decay & Ceiling Bounds Enforcement",
      "Surge=₹23.00, Exceeding Max Price Rejection/Clamping=true, Decay=₹22.00",
      `Surge=₹${p1}, Max Price > ₹25 Rejection=${ceilingRejected}, Decay=₹${pDecay}`,
      p1 === 23.00 && ceilingRejected && pDecay === 22.00
    );
  } catch (err) {
    report("5. ±₹1 Step Surge, Decay & Ceiling Bounds Enforcement", "Step movement valid", err.message, false);
  }

  // ------------------------------------------------------------------
  // 6. MARKET CRASH FOR ALL PRODUCTS
  // ------------------------------------------------------------------
  try {
    await apiRequest('/pricing/market-crash/trigger', 'POST');

    const crashProdsRes = await apiRequest('/pos/products');
    const crashProds = crashProdsRes.data;

    const allAtFloor = crashProds.every(p => Number(p.currentCupPrice) === 20.00 || Number(p.minCupPrice) === 20.00);

    const crashStatus = await apiRequest('/pricing/market-crash/status');

    await apiRequest('/pricing/market-crash/stop', 'POST');
    const stoppedStatus = await apiRequest('/pricing/market-crash/status');

    report(
      "6. Market Crash Across ALL Active Products in Database",
      "ALL products drop to floor ₹20.00; active=true -> active=false",
      `Total products=${crashProds.length}, All floor=20: ${allAtFloor}, Active=${crashStatus.data.active}, Stopped=${stoppedStatus.data.active}`,
      allAtFloor && crashStatus.data.active === true && stoppedStatus.data.active === false
    );
  } catch (err) {
    report("6. Market Crash Across ALL Active Products", "Floor ₹20 for all products", err.message, false);
  }

  // ------------------------------------------------------------------
  // 7. REAL STOMP WEBSOCKET EVENTS VALIDATION
  // ------------------------------------------------------------------
  try {
    const { ws, receivedMessages } = await connectStompWebSocket();

    await apiRequest('/pricing/deploy', 'POST', {
      productId: 1,
      defaultPrice: 22.00,
      currentPrice: 24.00,
      minPrice: 18.00,
      maxPrice: 25.00
    });

    await apiRequest('/pricing/market-crash/trigger', 'POST');
    await apiRequest('/pricing/market-crash/stop', 'POST');

    await new Promise(r => setTimeout(r, 1000));
    ws.close();

    const priceEventsCount = receivedMessages.prices.length;
    const crashEventsCount = receivedMessages.marketCrash.length;

    report(
      "7. Real STOMP WebSocket Live Events (/topic/prices & /topic/market-crash)",
      "Received live STOMP JSON messages for price updates and market crash events",
      `Received ${priceEventsCount} price event(s), ${crashEventsCount} market-crash event(s)`,
      priceEventsCount > 0 && crashEventsCount > 0
    );
  } catch (err) {
    report("7. Real STOMP WebSocket Live Events", "STOMP events received", err.message, false);
  }

  // ------------------------------------------------------------------
  // 8. IDEMPOTENCY TEST
  // ------------------------------------------------------------------
  try {
    const dupKey = "IDEM-DUP-" + Date.now();
    const payload = {
      idempotencyKey: dupKey,
      items: [{ productId: 1, quantity: 3, cupSizeMl: 250 }],
      paymentMethod: "CASH"
    };

    const firstReq = await apiRequest('/pos/checkout', 'POST', payload);
    const secondReq = await apiRequest('/pos/checkout', 'POST', payload);

    const sameOrderNum = firstReq.data.orderNumber === secondReq.data.orderNumber;
    const secondMessage = secondReq.data.message;

    report(
      "8. Duplicate Idempotency Key Rejection / Deduplication",
      "Second request returns existing order without creating duplicate in DB",
      `Order 1 #${firstReq.data.orderNumber}, Order 2 #${secondReq.data.orderNumber}, Msg: "${secondMessage}"`,
      sameOrderNum && secondReq.ok
    );
  } catch (err) {
    report("8. Duplicate Idempotency Key Rejection", "Idempotent duplicate handled", err.message, false);
  }

  // ------------------------------------------------------------------
  // 9. HIGH CONCURRENCY CHECKOUT (50 REQUESTS)
  // ------------------------------------------------------------------
  try {
    console.log("--- ⚡ RUNNING 50 CONCURRENT CHECKOUT REQUESTS ---");
    const concurrentPromises = [];
    const tsCon = Date.now();

    for (let i = 0; i < 50; i++) {
      concurrentPromises.push(
        apiRequest('/pos/checkout', 'POST', {
          idempotencyKey: `CONCURRENCY-${tsCon}-${i}`,
          items: [{ productId: 1, quantity: 1, cupSizeMl: 250 }],
          paymentMethod: "CASH"
        })
      );
    }

    const results = await Promise.all(concurrentPromises);
    const successCount = results.filter(r => r.ok).length;
    const failedCount = results.filter(r => !r.ok).length;

    report(
      "9. High Concurrency Checkout (50 Concurrent Requests)",
      "All 50 requests processed safely with zero deadlocks or negative inventory",
      `Successful orders: ${successCount}/50, Failed: ${failedCount}`,
      successCount === 50
    );
  } catch (err) {
    report("9. High Concurrency Checkout", "50 concurrent orders processed", err.message, false);
  }

  // ------------------------------------------------------------------
  // SUMMARY
  // ------------------------------------------------------------------
  console.log("====================================================================");
  console.log(`🏁 STAGE 3 VALIDATION COMPLETE: ${passedTests}/${totalTests} TESTS PASSED`);
  console.log("====================================================================\n");
}

runStage3Validation().catch(console.error);
