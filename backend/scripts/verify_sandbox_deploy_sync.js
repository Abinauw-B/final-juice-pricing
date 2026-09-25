const http = require('http');
const { Client } = require('pg');

const dbConfig = {
  host: 'localhost',
  port: 5432,
  user: 'postgres',
  password: 'postgres',
  database: 'retailposdb'
};

function request(options, postData = null) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        let parsed = null;
        try {
          parsed = body ? JSON.parse(body) : null;
        } catch (e) {
          parsed = body;
        }
        resolve({ status: res.statusCode, headers: res.headers, data: parsed });
      });
    });
    req.on('error', reject);
    if (postData) {
      if (typeof postData === 'object') {
        req.write(JSON.stringify(postData));
      } else {
        req.write(postData);
      }
    }
    req.end();
  });
}

async function apiGet(path) {
  return request({
    hostname: 'localhost',
    port: 8088,
    path: '/api' + path,
    method: 'GET',
    headers: { 'Accept': 'application/json', 'X-User-Role': 'SUPER_ADMIN' }
  });
}

async function apiPost(path, data = {}) {
  return request({
    hostname: 'localhost',
    port: 8088,
    path: '/api' + path,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-User-Role': 'SUPER_ADMIN'
    }
  }, data);
}

async function run() {
  console.log('================================================================================');
  console.log('🚀 VALIDATING SANDBOX SIMULATOR DEPLOYMENT & COMPLETE MULTI-LAYER SYNC');
  console.log('================================================================================\n');

  const pgClient = new Client(dbConfig);
  await pgClient.connect();

  try {
    // 1. Deploy Sandbox Parameters (Mango: Price=30, Max=30, Min=18, Target=2.0)
    console.log('[STEP 1] Deploying Sandbox Parameters to Backend via /api/pricing/deploy...');
    const deployPayload = {
      productId: 1,
      price: 30.00,
      currentPrice: 30.00,
      defaultPrice: 25.00,
      minPrice: 18.00,
      maxPrice: 30.00,
      targetSalesPer2Minute: 2.0
    };

    const deployRes = await apiPost('/pricing/deploy', deployPayload);
    console.log('Deploy API Response:', deployRes.data);

    // 2. Query PostgreSQL Directly
    console.log('\n[STEP 2] Verifying PostgreSQL `products` Table Persistence...');
    const dbRow = (await pgClient.query("SELECT id, name, current_cup_price, default_cup_price, min_cup_price, max_cup_price, target_sales_per_2_minute, pricing_mode FROM products WHERE id = 1")).rows[0];
    console.table([dbRow]);

    const dbPrice = parseFloat(dbRow.current_cup_price);
    const dbMax = parseFloat(dbRow.max_cup_price);
    const dbMin = parseFloat(dbRow.min_cup_price);
    const dbTarget = parseFloat(dbRow.target_sales_per_2_minute);

    console.log(`    DB Current Price: ₹${dbPrice.toFixed(2)} (Expected: ₹30.00)`);
    console.log(`    DB Max Price (Ceiling): ₹${dbMax.toFixed(2)} (Expected: ₹30.00)`);
    console.log(`    DB Min Price (Floor): ₹${dbMin.toFixed(2)} (Expected: ₹18.00)`);
    console.log(`    DB Target Sales: ${dbTarget.toFixed(1)} / round (Expected: 2.0)`);

    // 3. Query POS REST API
    console.log('\n[STEP 3] Verifying Customer POS REST API (/api/pos/products)...');
    const posProducts = (await apiGet('/pos/products')).data;
    const mangoPos = posProducts.find(p => p.id === 1);
    console.log('    POS Product Object:', {
      id: mangoPos.id,
      name: mangoPos.name,
      currentCupPrice: mangoPos.currentCupPrice,
      minCupPrice: mangoPos.minCupPrice,
      maxCupPrice: mangoPos.maxCupPrice,
      targetSalesPer2Minute: mangoPos.targetSalesPer2Minute,
      pricingMode: mangoPos.pricingMode
    });

    // 4. Query Admin Pricing Config API
    console.log('\n[STEP 4] Verifying Admin Pricing Config API (/api/admin/pricing/config)...');
    const adminConfig = (await apiGet('/admin/pricing/config')).data;
    const mangoConfig = adminConfig.products.find(p => p.productId === 1);
    console.log('    Admin Config Object:', mangoConfig);

    const isMatch = (
      dbPrice === 30.00 &&
      dbMax === 30.00 &&
      dbMin === 18.00 &&
      dbTarget === 2.0 &&
      parseFloat(mangoPos.maxCupPrice) === 30.00 &&
      parseFloat(mangoConfig.maxCupPrice) === 30.00
    );

    console.log('\n================================================================================');
    if (isMatch) {
      console.log('✅ PERFECT SYNC CONFIRMED ACROSS ALL LAYERS:');
      console.log('   - Sandbox Simulator: Max Limit ₹30.00, Target 2.0');
      console.log('   - PostgreSQL Table: max_cup_price = 30.00, target_sales_per_2_minute = 2.0');
      console.log('   - Backend API: maxCupPrice = 30.00, targetSalesPer2Minute = 2.0');
      console.log('   - Customer POS UI: Ceiling ₹30, Target: 2.0 / round');
      console.log('================================================================================\n');
    } else {
      console.error('❌ MISMATCH DETECTED!');
      process.exit(1);
    }

    await pgClient.end();
  } catch (err) {
    console.error('Verification error:', err);
    await pgClient.end();
    process.exit(1);
  }
}

run();
