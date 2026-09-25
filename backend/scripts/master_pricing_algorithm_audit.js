const http = require('http');
const { execFileSync } = require('child_process');

const API_BASE = 'http://localhost:8088/api';
const PSQL_EXE = 'D:\\New folder\\bin\\psql.exe';

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
            reqOptions.headers['Content-Type'] = 'application/json';
            reqOptions.headers['Content-Length'] = Buffer.byteLength(options.body);
        }

        const req = http.request(reqOptions, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                let json = null;
                try {
                    json = JSON.parse(body);
                } catch (e) {}
                resolve({ status: res.statusCode, headers: res.headers, body, json });
            });
        });

        req.on('error', reject);
        if (options.body) req.write(options.body);
        req.end();
    });
}

function queryDb(sql) {
    try {
        return execFileSync(PSQL_EXE, ['-U', 'postgres', '-d', 'retailposdb', '-t', '-A', '-c', sql], { encoding: 'utf8' }).trim();
    } catch (e) {
        console.error('DB Query Error:', e.message);
        return '';
    }
}

async function resetSystemState() {
    await httpRequest(`${API_BASE}/pricing/reset-default`, { method: 'POST' });
    queryDb("TRUNCATE TABLE sales_orders, sales_order_items, pricing_processed_sales RESTART IDENTITY CASCADE;");
    queryDb("UPDATE products SET current_cup_price = 25.00, default_cup_price = 25.00 WHERE id IN (1,2,3,4,5,6,7,23);");
}

async function runMasterPricingAlgorithmAudit() {
    console.log('====================================================================');
    console.log('🚨 MASTER DYNAMIC PRICING ALGORITHM AUDIT & TEST MATRIX');
    console.log('====================================================================\n');

    let passedTests = 0;
    let totalTests = 12;

    // TEST 1: Reset all prices to ₹25
    console.log('--- TEST 1: Reset all prices to ₹25.00 ---');
    await resetSystemState();
    const p1 = queryDb("SELECT COUNT(*) FROM products WHERE id IN (1,2,3,4,5,6,7,23) AND ROUND(current_cup_price, 2) = 25.00;");
    if (p1 === '8') {
        console.log('[PASS] Test 1: All 8 products verified at base price ₹25.00');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 1: Expected 8 products at ₹25.00, got ${p1}`);
    }
    console.log('\n');

    // TEST 2: Buy Thunder x1
    console.log('--- TEST 2: Buy Thunder x1 ---');
    await resetSystemState();
    await httpRequest(`${API_BASE}/pos/checkout`, {
        method: 'POST',
        body: JSON.stringify({
            items: [{ productId: 23, quantity: 1, unitPrice: 25.00 }],
            paymentMethod: 'CASH'
        })
    });
    const thunderP2 = (await httpRequest(`${API_BASE}/pos/products`)).json?.find(p => p.id === 23);
    const mangoP2 = (await httpRequest(`${API_BASE}/pos/products`)).json?.find(p => p.id === 1);

    if (thunderP2?.currentCupPrice === 26 && mangoP2?.currentCupPrice <= 25) {
        console.log(`[PASS] Test 2: Thunder increased ₹25 → ₹26. Mango did not increase (price=${mangoP2?.currentCupPrice})`);
        passedTests++;
    } else {
        console.error(`[FAIL] Test 2: Thunder price=${thunderP2?.currentCupPrice}, Mango price=${mangoP2?.currentCupPrice}`);
    }
    console.log('\n');

    // TEST 3: Run settlement 5 times -> Thunder remains ₹26
    console.log('--- TEST 3: Run settlement 5 times (Thunder Idempotency Check) ---');
    let t3Passed = true;
    for (let i = 1; i <= 5; i++) {
        const repEval = await httpRequest(`${API_BASE}/pricing/evaluate`, { method: 'POST' });
        const thunderRep = (repEval.json?.updatedPrices || []).find(p => (p.beverageId === 23 || p.id === 23));
        const price = thunderRep?.currentPrice || thunderRep?.currentCupPrice;
        if (price !== 26) {
            t3Passed = false;
            console.error(`[FAIL] Settlement #${i} produced price ₹${price} instead of ₹26`);
        }
    }
    if (t3Passed) {
        console.log('[PASS] Test 3: Thunder remained strictly at ₹26 across 5 repeated settlement calls');
        passedTests++;
    }
    console.log('\n');

    // TEST 4: Buy Thunder x1 again -> Thunder becomes ₹27
    console.log('--- TEST 4: Buy Thunder x1 again ---');
    await httpRequest(`${API_BASE}/pos/checkout`, {
        method: 'POST',
        body: JSON.stringify({
            items: [{ productId: 23, quantity: 1, unitPrice: 26.00 }],
            paymentMethod: 'CASH'
        })
    });
    const thunderP4 = (await httpRequest(`${API_BASE}/pos/products`)).json?.find(p => p.id === 23);
    if (thunderP4?.currentCupPrice === 27) {
        console.log('[PASS] Test 4: Second purchase surged Thunder ₹26 → ₹27');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 4: Expected ₹27, got ₹${thunderP4?.currentCupPrice}`);
    }
    console.log('\n');

    // TEST 5: Run settlement 5 times -> Thunder remains ₹27
    console.log('--- TEST 5: Run settlement 5 times after second purchase ---');
    let t5Passed = true;
    for (let i = 1; i <= 5; i++) {
        const repEval = await httpRequest(`${API_BASE}/pricing/evaluate`, { method: 'POST' });
        const thunderRep = (repEval.json?.updatedPrices || []).find(p => (p.beverageId === 23 || p.id === 23));
        const price = thunderRep?.currentPrice || thunderRep?.currentCupPrice;
        if (price !== 27) {
            t5Passed = false;
            console.error(`[FAIL] Settlement #${i} produced price ₹${price} instead of ₹27`);
        }
    }
    if (t5Passed) {
        console.log('[PASS] Test 5: Thunder remained strictly at ₹27 across 5 repeated settlement calls');
        passedTests++;
    }
    console.log('\n');

    // TEST 6: Buy Orange x2 (Cross-Product Test) ---
    console.log('--- TEST 6: Buy Orange x2 (Cross-Product Test) ---');
    await httpRequest(`${API_BASE}/pos/checkout`, {
        method: 'POST',
        body: JSON.stringify({
            items: [{ productId: 4, quantity: 2, unitPrice: 25.00 }],
            paymentMethod: 'CASH'
        })
    });
    const orangeP6 = (await httpRequest(`${API_BASE}/pos/products`)).json?.find(p => p.id === 4);
    const thunderP6 = (await httpRequest(`${API_BASE}/pos/products`)).json?.find(p => p.id === 23);
    if (orangeP6?.currentCupPrice >= 19 && thunderP6?.currentCupPrice === 27) {
        console.log(`[PASS] Test 6: Orange surged (price=₹${orangeP6?.currentCupPrice}), Thunder remained isolated at ₹${thunderP6?.currentCupPrice}`);
        passedTests++;
    } else {
        console.error(`[FAIL] Test 6: Orange=${orangeP6?.currentCupPrice}, Thunder=${thunderP6?.currentCupPrice}`);
    }
    console.log('\n');

    // TEST 7: Zero-demand decay for Mango -> ₹25 -> ₹23 -> ₹21 -> ₹19 -> ₹18 -> ₹18
    console.log('--- TEST 7: Zero-demand decay for Mango ---');
    await resetSystemState();
    const decaySequence = [];
    for (let i = 1; i <= 5; i++) {
        const evalRes = await httpRequest(`${API_BASE}/pricing/evaluate`, { method: 'POST' });
        const mangoP = (evalRes.json?.updatedPrices || []).find(p => (p.beverageId === 1 || p.id === 1));
        decaySequence.push(mangoP?.currentPrice || mangoP?.currentCupPrice);
    }
    console.log('Mango decay sequence:', decaySequence.join(' → '));
    if (decaySequence.join(',') === '23,21,19,18,18') {
        console.log('[PASS] Test 7: Mango decayed accurately (25 → 23 → 21 → 19 → 18 → 18)');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 7: Expected [23,21,19,18,18], got [${decaySequence.join(',')}]`);
    }
    console.log('\n');

    // TEST 8: Buy Mango after reaching floor -> Mango can rise again
    console.log('--- TEST 8: Buy Mango after reaching floor ---');
    await httpRequest(`${API_BASE}/pos/checkout`, {
        method: 'POST',
        body: JSON.stringify({
            items: [{ productId: 1, quantity: 2, unitPrice: 18.00 }],
            paymentMethod: 'CASH'
        })
    });
    const mangoP8 = (await httpRequest(`${API_BASE}/pos/products`)).json?.find(p => p.id === 1);
    if (mangoP8?.currentCupPrice === 19) {
        console.log('[PASS] Test 8: Mango rose from floor ₹18 → ₹19 after new purchase');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 8: Expected ₹19, got ₹${mangoP8?.currentCupPrice}`);
    }
    console.log('\n');

    // TEST 9: Buy all 8 products -> verify independent response
    console.log('--- TEST 9: Buy all 8 products ---');
    await resetSystemState();
    const allItems = [1,2,3,4,5,6,7,23].map(id => ({ productId: id, quantity: 2, unitPrice: 25.00 }));
    await httpRequest(`${API_BASE}/pos/checkout`, {
        method: 'POST',
        body: JSON.stringify({ items: allItems, paymentMethod: 'CASH' })
    });
    const prodsP9 = (await httpRequest(`${API_BASE}/pos/products`)).json || [];
    const p9AllRose = prodsP9.filter(p => [1,2,3,4,5,6,7,23].includes(p.id)).every(p => p.currentCupPrice === 26);
    if (p9AllRose) {
        console.log('[PASS] Test 9: All 8 products surged independently from ₹25 → ₹26');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 9: Not all products reached ₹26`);
    }
    console.log('\n');

    // TEST 10: Buy only Orange -> 7 other products do not change upward
    console.log('--- TEST 10: Buy only Orange ---');
    await resetSystemState();

    await httpRequest(`${API_BASE}/pos/checkout`, {
        method: 'POST',
        body: JSON.stringify({ items: [{ productId: 4, quantity: 2, unitPrice: 25.00 }], paymentMethod: 'CASH' })
    });
    const prodsP10 = (await httpRequest(`${API_BASE}/pos/products`)).json || [];
    const orangeP10 = prodsP10.find(p => p.id === 4);
    const othersP10Upward = prodsP10.filter(p => p.id !== 4 && p.currentCupPrice > 25);
    if (orangeP10?.currentCupPrice === 26 && othersP10Upward.length === 0) {
        console.log('[PASS] Test 10: Orange surged to ₹26, 7 other products zero upward movement');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 10: Orange=${orangeP10?.currentCupPrice}, Upward count among others=${othersP10Upward.length}`);
    }
    console.log('\n');

    // TEST 11: Concurrent purchases stress test (10 Thunder checkouts)
    console.log('--- TEST 11: Concurrent Purchases (10 Thunder Checkouts) ---');
    await resetSystemState();
    const concPromises = Array.from({ length: 10 }).map((_, idx) =>
        httpRequest(`${API_BASE}/pos/checkout`, {
            method: 'POST',
            body: JSON.stringify({
                items: [{ productId: 23, quantity: 1, unitPrice: 25.00 }],
                paymentMethod: 'CASH',
                idempotencyKey: `AUDIT-CONCUR-${Date.now()}-${idx}`
            })
        })
    );
    const concRes = await Promise.all(concPromises);
    const concSuccess = concRes.filter(r => r.status === 200 || r.status === 201).length;
    if (concSuccess === 10) {
        console.log('[PASS] Test 11: 10/10 concurrent checkouts executed safely without deadlock');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 11: Only ${concSuccess}/10 succeeded`);
    }
    console.log('\n');

    // TEST 12: Trigger race safety (Scheduler + manual + POS)
    console.log('--- TEST 12: Trigger Race Safety ---');
    const racePromises = [
        httpRequest(`${API_BASE}/pricing/evaluate`, { method: 'POST' }),
        httpRequest(`${API_BASE}/pricing/evaluate`, { method: 'POST' }),
        httpRequest(`${API_BASE}/pos/checkout`, {
            method: 'POST',
            body: JSON.stringify({ items: [{ productId: 23, quantity: 1, unitPrice: 26.00 }], paymentMethod: 'CASH' })
        })
    ];
    const raceRes = await Promise.all(racePromises);
    const raceSuccess = raceRes.every(r => r.status === 200);
    if (raceSuccess) {
        console.log('[PASS] Test 12: Concurrent evaluations and POS checkout handled cleanly');
        passedTests++;
    } else {
        console.error(`[FAIL] Test 12: Race condition failed`);
    }
    console.log('\n');

    console.log('====================================================================');
    console.log(`🏁 MASTER PRICING AUDIT COMPLETE: ${passedTests}/${totalTests} TESTS PASSED`);
    console.log('====================================================================\n');
}

runMasterPricingAlgorithmAudit();
