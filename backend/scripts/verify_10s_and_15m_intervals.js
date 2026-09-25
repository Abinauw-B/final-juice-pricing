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
  console.log('🚀 TESTING 10-SECOND AND 15-MINUTE SETTLEMENT INTERVALS');
  console.log('='.repeat(80));

  // 1. Set to 10 Seconds
  console.log('\n--- 1. SETTING INTERVAL TO 10 SECONDS ---');
  const res10 = await request('http://localhost:8088/api/admin/pricing/timing', {
    method: 'PUT',
    headers: { 'X-User-Role': 'ADMIN' }
  }, JSON.stringify({ intervalSeconds: 10 }));

  if (res10.status !== 200 || res10.data.intervalSeconds !== 10 || res10.data.label !== '10 Seconds') {
    console.error('❌ FAILED to set 10s interval:', res10);
    process.exit(1);
  }
  console.log('  ✅ PASS: PUT /admin/pricing/timing set intervalSeconds=10, label="10 Seconds"');

  const get10 = await request('http://localhost:8088/api/pricing/timing');
  if (get10.status !== 200 || get10.data.intervalSeconds !== 10 || get10.data.label !== '10 Seconds') {
    console.error('❌ FAILED GET 10s timing:', get10);
    process.exit(1);
  }
  console.log('  ✅ PASS: GET /pricing/timing confirmed intervalSeconds=10, label="10 Seconds"');

  // 2. Set to 15 Minutes (900 Seconds)
  console.log('\n--- 2. SETTING INTERVAL TO 15 MINUTES (900 SECONDS) ---');
  const res900 = await request('http://localhost:8088/api/admin/pricing/timing', {
    method: 'PUT',
    headers: { 'X-User-Role': 'ADMIN' }
  }, JSON.stringify({ intervalSeconds: 900 }));

  if (res900.status !== 200 || res900.data.intervalSeconds !== 900 || res900.data.label !== '15 Minutes') {
    console.error('❌ FAILED to set 15m interval:', res900);
    process.exit(1);
  }
  console.log('  ✅ PASS: PUT /admin/pricing/timing set intervalSeconds=900, label="15 Minutes"');

  const get900 = await request('http://localhost:8088/api/pricing/timing');
  if (get900.status !== 200 || get900.data.intervalSeconds !== 900 || get900.data.label !== '15 Minutes') {
    console.error('❌ FAILED GET 15m timing:', get900);
    process.exit(1);
  }
  console.log('  ✅ PASS: GET /pricing/timing confirmed intervalSeconds=900, label="15 Minutes"');

  // 3. Reset to 60 Seconds
  console.log('\n--- 3. RESETTING TO DEFAULT 60 SECONDS (1 MINUTE) ---');
  const res60 = await request('http://localhost:8088/api/admin/pricing/timing', {
    method: 'PUT',
    headers: { 'X-User-Role': 'ADMIN' }
  }, JSON.stringify({ intervalSeconds: 60 }));

  if (res60.status !== 200 || res60.data.intervalSeconds !== 60 || res60.data.label !== '1 Minute') {
    console.error('❌ FAILED to reset 60s interval:', res60);
    process.exit(1);
  }
  console.log('  ✅ PASS: Successfully reset to 1 Minute default');

  console.log('\n' + '='.repeat(80));
  console.log('🏁 10-SECOND AND 15-MINUTE INTERVAL VERIFICATION COMPLETE & PASSED!');
  console.log('='.repeat(80));
}

run().catch(err => {
  console.error('Unexpected error:', err);
  process.exit(1);
});
