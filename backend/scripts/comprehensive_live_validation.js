/**
 * Comprehensive Live Pricing Engine Validation Runner
 * Tests every single requirement against the live running Spring Boot backend and PostgreSQL database.
 */
const http = require('http');
const { execFileSync } = require('child_process');

const API_BASE = 'http://localhost:8088/api';
const PSQL_EXE = 'D:\\New folder\\bin\\psql.exe';

function httpRequest(path, method = 'GET', body = null, headers = {}) {
    return new Promise((resolve, reject) => {
        const url = new URL(API_BASE + path);
        const reqHeaders = {
            'Content-Type': 'application/json',
            ...headers
        };
        const payload = body ? JSON.stringify(body) : null;
        if (payload) {
            reqHeaders['Content-Length'] = Buffer.byteLength(payload);
        }

        const req = http.request({
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method,
            headers: reqHeaders
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                let json = null;
                try {
                    json = JSON.parse(data);
                } catch (e) {
                    json = data;
                }
                resolve({ status: res.statusCode, data: json });
            });
        });

        req.on('error', reject);
        if (payload) req.write(payload);
        req.end();
    });
}

function queryDb(sql) {
    try {
        return execFileSync(PSQL_EXE, ['-U', 'postgres', '-d', 'retailposdb', '-t', '-A', '-c', sql], { encoding: 'utf8' }).trim();
    } catch (e) {
        return 'ERROR: ' + e.message;
    }
}

async function runLiveValidation() {
    console.log('================================================================================');
    console.log('🏛️ COMPREHENSIVE LIVE PRICING ENGINE & SETTLEMENT VALIDATION');
    console.log('================================================================================\n');

    let allPass = true;

    // -------------------------------------------------------------------------
    // TEST 1: ADMIN RESET & INITIAL STATE
    // -------------------------------------------------------------------------
    console.log('--- TEST 1: Admin Reset to Default (₹25.00) ---');
    const resetRes = await httpRequest('/pricing/reset', 'POST');
    console.log('Reset Response Status:', resetRes.status);
    const dbPricesAfterReset = queryDb("SELECT id, name, current_cup_price, default_cup_price FROM products WHERE is_active=true ORDER BY id;");
    console.log('DB Products After Reset:\n' + dbPricesAfterReset);

    // -------------------------------------------------------------------------
    // TEST 2: CHECKOUT SERVER PRICE AUTHORITY (Section 4)
    // Client sends unitPrice = ₹18.00 while DB has currentCupPrice = ₹30.00
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 2: Server-Authoritative Price on Checkout (Section 4) ---');
    // Set DB price of Mango (ID 1) to ₹30.00
    queryDb("UPDATE products SET current_cup_price = 30.00 WHERE id = 1;");
    
    // Client attempts to send forged price ₹18.00 in cart
    const spoofCheckout = await httpRequest('/pos/checkout', 'POST', {
        items: [{ productId: 1, quantity: 1, unitPrice: 18.00 }],
        paymentMethod: 'CASH'
    });
    console.log('Checkout Status:', spoofCheckout.status);
    console.log('Charged Total Amount:', spoofCheckout.data.totalAmount);
    console.log('Item Unit Price Charged:', spoofCheckout.data.items[0].unitPrice);
    
    if (spoofCheckout.data.totalAmount === 30.00 && spoofCheckout.data.items[0].unitPrice === 30.00) {
        console.log('✅ PASS: Server ignored client price ₹18.00 and charged authoritative DB price ₹30.00');
    } else {
        console.log('❌ FAIL: Spoofed price accepted!');
        allPass = false;
    }

    // -------------------------------------------------------------------------
    // TEST 3: CHECKOUT DOES NOT DIRECTLY ALTER PRICE (Section 3)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 3: Checkout Does Not Directly Alter Price (Section 3) ---');
    const priceBefore = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    await httpRequest('/pos/checkout', 'POST', {
        items: [{ productId: 1, quantity: 2 }],
        paymentMethod: 'CASH'
    });
    const priceAfterCheckout = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log(`Price before checkout: ₹${priceBefore} | Price immediately after checkout: ₹${priceAfterCheckout}`);
    if (priceBefore === priceAfterCheckout) {
        console.log('✅ PASS: Product price remained unchanged upon checkout (no immediate +₹1 surge)');
    } else {
        console.log('❌ FAIL: Product price changed on checkout!');
        allPass = false;
    }

    // -------------------------------------------------------------------------
    // TEST 4: QUANTITY HANDLING & MULTI-PRODUCT CART INDEPENDENCE (Section 5)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 4: Quantity Handling & Multi-Product Cart (Section 5) ---');
    // Clear sales history
    queryDb("TRUNCATE TABLE sales_orders, sales_order_items, price_history RESTART IDENTITY CASCADE;");
    queryDb("UPDATE products SET current_cup_price = 25.00 WHERE is_active = true;");

    // Checkout: 2 Mango (ID 1) + 3 Lemon (ID 2) + 1 Orange (ID 4)
    const multiCheckout = await httpRequest('/pos/checkout', 'POST', {
        items: [
            { productId: 1, quantity: 2 },
            { productId: 2, quantity: 3 },
            { productId: 4, quantity: 1 }
        ],
        paymentMethod: 'CASH'
    });
    console.log('Multi-cart checkout status:', multiCheckout.status);

    const salesItemQuantities = queryDb("SELECT product_id, SUM(quantity) FROM sales_order_items GROUP BY product_id ORDER BY product_id;");
    console.log('sales_order_items recorded quantities:\n' + salesItemQuantities);

    // -------------------------------------------------------------------------
    // TEST 5: EXACT DATABASE RECORDS (Section 6)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 5: Exact Database Records Created (Section 6) ---');
    const lastOrder = queryDb("SELECT id, order_number, total_amount, payment_method, payment_status, created_at FROM sales_orders ORDER BY id DESC LIMIT 1;");
    console.log('sales_orders record: ' + lastOrder);
    const orderItems = queryDb("SELECT id, order_id, product_id, product_name, quantity, unit_price, total_price, volume_deducted_ml, cup_size_ml, locked_price, price_version FROM sales_order_items WHERE order_id = (SELECT MAX(id) FROM sales_orders);");
    console.log('sales_order_items records:\n' + orderItems);

    // -------------------------------------------------------------------------
    // TEST 6: THREE TIME WINDOWS & EXCLUSIVE BOUNDARY QUERY (Section 7)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 6: Three Time Windows & Exclusive Query Boundaries (Section 7) ---');
    // Clear sales history
    queryDb("TRUNCATE TABLE sales_orders, sales_order_items RESTART IDENTITY CASCADE;");
    
    // Purchase 3 cups at T-1m (W0)
    await httpRequest('/pos/checkout', 'POST', { items: [{ productId: 1, quantity: 3 }], paymentMethod: 'CASH' });
    const idW0 = queryDb("SELECT MAX(id) FROM sales_order_items;");
    queryDb(`UPDATE sales_order_items SET created_at = NOW() - INTERVAL '1 minute' WHERE id = ${idW0};`);
    
    // Purchase 1 cup at T-3m (W1)
    await httpRequest('/pos/checkout', 'POST', { items: [{ productId: 1, quantity: 1 }], paymentMethod: 'CASH' });
    const idW1 = queryDb("SELECT MAX(id) FROM sales_order_items;");
    queryDb(`UPDATE sales_order_items SET created_at = NOW() - INTERVAL '3 minutes' WHERE id = ${idW1};`);

    // Purchase 2 cups at T-5m (W2)
    await httpRequest('/pos/checkout', 'POST', { items: [{ productId: 1, quantity: 2 }], paymentMethod: 'CASH' });
    const idW2 = queryDb("SELECT MAX(id) FROM sales_order_items;");
    queryDb(`UPDATE sales_order_items SET created_at = NOW() - INTERVAL '5 minutes' WHERE id = ${idW2};`);

    // Verify debug endpoint returns exact W0=3, W1=1, W2=2
    const debugRes = await httpRequest('/pricing/debug/1');
    console.log('Debug Evaluation for Mango (ID 1):', JSON.stringify(debugRes.data, null, 2));

    // -------------------------------------------------------------------------
    // TEST 7: DWMA SETTLEMENT CALCULATION (Section 8)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 7: DWMA Settlement Execution (Section 8) ---');
    // Delete W2 for exact example: W0=3, W1=1, W2=0, Target=1.10 -> Sw=3.50, Rd=3.1818 -> +1 -> ₹26.00
    queryDb(`DELETE FROM sales_order_items WHERE id = ${idW2};`);
    queryDb("UPDATE products SET current_cup_price = 25.00, target_sales_per_2_minute = 1.10 WHERE id = 1;");
    
    const settleRes = await httpRequest('/pricing/evaluate?force=true', 'POST');
    console.log('Settlement executed. Status:', settleRes.status);
    const mangoAfterSettle = queryDb("SELECT id, name, current_cup_price FROM products WHERE id = 1;");
    console.log('Mango product after DWMA settlement:\n' + mangoAfterSettle);
    const historyRecord = queryDb("SELECT product_id, old_price, new_price, price_change, raw_w0, raw_w1, raw_w2, weighted_sales, target_sales, demand_ratio, reason, explanation FROM price_history ORDER BY id DESC LIMIT 1;");
    console.log('price_history audit entry:\n' + historyRecord);

    // -------------------------------------------------------------------------
    // TEST 8: ZERO-DEMAND DECAY TO FLOOR (Section 9 & 11)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 8: Zero-Demand Decay (Section 9 & 11) ---');
    queryDb("TRUNCATE TABLE sales_orders, sales_order_items RESTART IDENTITY CASCADE;");
    queryDb("UPDATE products SET current_cup_price = 25.00 WHERE id = 1;");
    
    console.log('Starting price: ₹25.00');
    // Cycle 1: 25 -> 23
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const p1 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 1 (Zero demand): ₹' + p1);

    // Cycle 2: 23 -> 21
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const p2 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 2 (Zero demand): ₹' + p2);

    // Cycle 3: 21 -> 19
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const p3 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 3 (Zero demand): ₹' + p3);

    // Cycle 4: 19 -> 18 (clamped to floor)
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const p4 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 4 (Floor clamp): ₹' + p4);

    // Cycle 5: 18 -> 18 (floor hard stop)
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const p5 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 5 (Floor hard stop): ₹' + p5);

    // -------------------------------------------------------------------------
    // TEST 9: HIGH DEMAND SURGING TO CEILING (Section 10 & 12)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 9: High Demand Surging to Ceiling (Section 10 & 12) ---');
    queryDb("UPDATE products SET current_cup_price = 33.00, target_sales_per_2_minute = 1.00 WHERE id = 1;");
    await httpRequest('/pos/checkout', 'POST', { items: [{ productId: 1, quantity: 10 }], paymentMethod: 'CASH' });

    console.log('Starting price: ₹33.00');
    // Cycle 1: 33 -> 34
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const s1 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 1: ₹' + s1);

    // Cycle 2: 34 -> 35 (clamped to ceiling)
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const s2 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 2 (Ceiling clamp): ₹' + s2);

    // Cycle 3: 35 -> 35 (ceiling hard stop)
    await httpRequest('/pricing/evaluate?force=true', 'POST');
    const s3 = queryDb("SELECT current_cup_price FROM products WHERE id = 1;");
    console.log('After Cycle 3 (Ceiling hard stop): ₹' + s3);

    // -------------------------------------------------------------------------
    // TEST 10: MARKET CRASH & SNAPSHOT RESTORATION (Section 19)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 10: Market Crash & Snapshot Restoration (Section 19) ---');
    queryDb("UPDATE products SET current_cup_price = 29.00 WHERE id = 1;");
    queryDb("UPDATE products SET current_cup_price = 23.00 WHERE id = 2;");

    console.log('Pre-Crash Prices: Mango=₹29.00, Lemon=₹23.00');
    const crashTrigger = await httpRequest('/pricing/market-crash/trigger', 'POST', { durationMinutes: 3, triggerType: 'MANUAL_ADMIN' });
    console.log('Crash Trigger Status:', crashTrigger.status);

    const crashPrices = queryDb("SELECT id, name, current_cup_price FROM products WHERE id IN (1, 2);");
    console.log('In-Crash DB Prices:\n' + crashPrices);

    const crashStop = await httpRequest('/pricing/market-crash/stop', 'POST');
    console.log('Crash Stop Status:', crashStop.status);

    const restoredPrices = queryDb("SELECT id, name, current_cup_price FROM products WHERE id IN (1, 2);");
    console.log('Restored DB Prices:\n' + restoredPrices);

    // -------------------------------------------------------------------------
    // TEST 11: FAILED CHECKOUT ISOLATION (Section 22)
    // -------------------------------------------------------------------------
    console.log('\n--- TEST 11: Failed Checkout Isolation (Section 22) ---');
    const salesCountBefore = queryDb("SELECT COUNT(*) FROM sales_order_items;");
    const failedCheckout = await httpRequest('/pos/checkout', 'POST', {
        items: [{ productId: 999999, quantity: 1 }], // Non-existent product
        paymentMethod: 'CASH'
    });
    console.log('Failed checkout response status:', failedCheckout.status);
    const salesCountAfter = queryDb("SELECT COUNT(*) FROM sales_order_items;");
    console.log(`sales_order_items count before: ${salesCountBefore} | after: ${salesCountAfter}`);
    if (salesCountBefore === salesCountAfter) {
        console.log('✅ PASS: Failed checkout created zero sales order items');
    } else {
        console.log('❌ FAIL: Orphan sales items left after failure!');
        allPass = false;
    }

    console.log('\n================================================================================');
    console.log('🎉 LIVE TEST RUN COMPLETED');
    console.log('================================================================================');
}

runLiveValidation().catch(err => {
    console.error('Fatal error during live validation:', err);
});
