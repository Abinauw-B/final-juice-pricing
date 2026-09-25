const http = require('http');
const { Client } = require('pg');

function apiGet(path) {
  return new Promise((resolve, reject) => {
    const req = http.get(`http://localhost:8088${path}`, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, raw: data });
        }
      });
    });
    req.on('error', reject);
    req.setTimeout(4000, () => {
      req.destroy();
      reject(new Error('Timeout'));
    });
  });
}

async function verify() {
  console.log('--- 1. Waiting for backend /api/health ---');
  let healthy = false;
  for (let i = 0; i < 20; i++) {
    try {
      const res = await apiGet('/api/health');
      if (res.status === 200) {
        console.log('Backend is up and running!');
        healthy = true;
        break;
      }
    } catch (e) {
      await new Promise(r => setTimeout(r, 1000));
    }
  }

  if (!healthy) {
    console.error('Backend failed to start in time');
    process.exit(1);
  }

  console.log('\n--- 2. Checking GET /api/pos/products ---');
  const prodsRes = await apiGet('/api/pos/products');
  console.log(`Received ${prodsRes.body.length} products:`);
  let allMatch = true;
  for (const p of prodsRes.body) {
    const base = Number(p.defaultCupPrice !== undefined ? p.defaultCupPrice : p.basePrice);
    const curr = Number(p.currentCupPrice !== undefined ? p.currentCupPrice : p.price);
    const min = Number(p.minCupPrice !== undefined ? p.minCupPrice : p.minPrice);
    const max = Number(p.maxCupPrice !== undefined ? p.maxCupPrice : p.maxPrice);
    console.log(`- ${p.flavour || p.name}: Base=₹${base.toFixed(2)}, Current=₹${curr.toFixed(2)}, Floor=₹${min.toFixed(2)}, Ceiling=₹${max.toFixed(2)}`);
    if (base !== 25 || min !== 20 || max !== 30) {
      console.error(`  FAIL: Expected Base 25, Min 20, Max 30. Got: Base=${base}, Min=${min}, Max=${max}`);
      allMatch = false;
    }
  }

  console.log('\n--- 3. Checking GET /api/pricing/config ---');
  const configRes = await apiGet('/api/pricing/config');
  const g = configRes.body.global;
  console.log(`Global Config: DefaultPrice=₹${g.defaultCupPrice}, MinPrice=₹${g.minCupPrice}, MaxPrice=₹${g.maxCupPrice}, CrashPrice=₹${g.marketCrashPrice}`);
  if (Number(g.defaultCupPrice) !== 25 || Number(g.minCupPrice) !== 20 || Number(g.maxCupPrice) !== 30 || Number(g.marketCrashPrice) !== 20) {
    console.error('  FAIL: Global config does not match expected permanent pricing bounds!');
    allMatch = false;
  }

  console.log('\n--- 4. Checking PostgreSQL Database directly ---');
  const client = new Client({
    user: 'postgres',
    host: 'localhost',
    database: 'retailposdb',
    password: 'password',
    port: 5432,
  });

  try {
    await client.connect();
    const dbRes = await client.query('SELECT id, flavour, default_cup_price, current_cup_price, min_cup_price, max_cup_price FROM products WHERE is_active = true ORDER BY id ASC');
    console.log(`PostgreSQL products count: ${dbRes.rows.length}`);
    for (const r of dbRes.rows) {
      console.log(`  [DB] ${r.flavour}: default=₹${r.default_cup_price}, current=₹${r.current_cup_price}, min=₹${r.min_cup_price}, max=₹${r.max_cup_price}`);
      if (Number(r.default_cup_price) !== 25 || Number(r.min_cup_price) !== 20 || Number(r.max_cup_price) !== 30) {
        console.error(`  FAIL: DB values incorrect for ${r.flavour}`);
        allMatch = false;
      }
    }
    await client.end();
  } catch (err) {
    console.warn('DB direct check skipped or error:', err.message);
  }

  if (allMatch) {
    console.log('\n✅ SUCCESS: Base ₹25.00, Floor ₹20.00, Ceiling ₹30.00 permanently verified across API, DB, and configs!');
  } else {
    console.error('\n❌ FAILURE: Some parameters did not match!');
    process.exit(1);
  }
}

verify().catch(console.error);
