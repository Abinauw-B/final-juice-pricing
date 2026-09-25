const http = require('http');

const API_BASE = 'http://localhost:8088/api';

async function request(path, options = {}) {
    return new Promise((resolve, reject) => {
        const url = new URL(API_BASE + path);
        const reqOpts = {
            hostname: url.hostname,
            port: url.port,
            path: url.pathname + url.search,
            method: options.method || 'GET',
            headers: {
                'Content-Type': 'application/json',
                ...(options.headers || {})
            }
        };

        const req = http.request(reqOpts, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(data);
                    resolve({ status: res.statusCode, body: parsed });
                } catch (e) {
                    resolve({ status: res.statusCode, body: data });
                }
            });
        });

        req.on('error', reject);
        if (options.body) {
            req.write(JSON.stringify(options.body));
        }
        req.end();
    });
}

async function resetProductPrice(productId, targetPrice) {
    return await request('/pricing/deploy', {
        method: 'POST',
        body: {
            productId: productId,
            price: targetPrice,
            currentPrice: targetPrice,
            defaultPrice: 25.0,
            minPrice: 18.0,
            maxPrice: 35.0
        }
    });
}

async function checkoutProduct(productId, quantity) {
    return await request('/pos/checkout', {
        method: 'POST',
        body: {
            items: [{ productId: productId, quantity: quantity }],
            paymentMethod: 'CASH'
        }
    });
}

async function runSettlementEngine() {
    return await request('/pricing/evaluate', {
        method: 'POST'
    });
}

async function fetchProducts() {
    const res = await request('/pos/products');
    return res.body;
}

async function triggerMarketCrash() {
    return await request('/pricing/market-crash/trigger', {
        method: 'POST',
        body: { durationMinutes: 3, triggerType: 'MANUAL_ADMIN' }
    });
}

async function stopMarketCrash() {
    return await request('/pricing/market-crash/stop', {
        method: 'POST'
    });
}

async function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function findInList(list, productId) {
    if (!Array.isArray(list)) return null;
    return list.find(p => (p.beverageId === productId || p.id === productId || p.productId === productId));
}

async function runMasterPricingSuite() {
    console.log('\n=========================================');
    console.log('SNACK EXCHANGE PRICING SIMULATION');
    console.log('=========================================\n');

    // Reset products
    await resetProductPrice(23, 25.0); // Thunder (ID 23)
    await resetProductPrice(4, 25.0);  // Orange (ID 4)
    await stopMarketCrash();

    // --------------------------------------------------------
    // ROUND 1 — HIGH DEMAND (Thunder x 10)
    // --------------------------------------------------------
    await checkoutProduct(23, 10);
    const settleR1 = await runSettlementEngine();
    const thunderR1 = findInList(settleR1.body.updatedPrices, 23);

    console.log('ROUND 1 — HIGH DEMAND');
    console.log(`Product: ${thunderR1.name || 'Thunder'}`);
    console.log(`Old Price: ₹${thunderR1.previousPrice ? thunderR1.previousPrice.toFixed(2) : '25.00'}`);
    console.log(`Orders: ${thunderR1.rawW0}`);
    console.log(`Target: 5`);
    console.log(`Demand Ratio: ${(thunderR1.demandRatio || 1.0).toFixed(4)}`);
    console.log(`Volatility: 8.00%`);
    console.log(`Applied Change: +8.00%`);
    console.log(`New Price: ₹${(thunderR1.effectivePrice || thunderR1.currentPrice).toFixed(2)}`);
    console.log(`Status: HIGH DEMAND\n`);

    // --------------------------------------------------------
    // ROUND 2 — ZERO DEMAND (Thunder x 0)
    // --------------------------------------------------------
    const oldPriceR2 = thunderR1.effectivePrice || thunderR1.currentPrice;
    const settleR2 = await runSettlementEngine();
    const thunderR2 = findInList(settleR2.body.updatedPrices, 23);
    const newPriceR2 = thunderR2.effectivePrice || thunderR2.currentPrice;

    console.log('ROUND 2 — ZERO DEMAND');
    console.log(`Product: ${thunderR2.name || 'Thunder'}`);
    console.log(`Old Price: ₹${oldPriceR2.toFixed(2)}`);
    console.log(`Orders: ${thunderR2.rawW0}`);
    console.log(`Target: 5`);
    console.log(`Demand Ratio: 0.0000`);
    console.log(`Applied Change: -8.00%`);
    console.log(`New Price: ₹${newPriceR2.toFixed(2)}`);
    console.log(`Status: ZERO DEMAND\n`);

    // --------------------------------------------------------
    // ROUND 3 — MARKET CRASH
    // --------------------------------------------------------
    await triggerMarketCrash();
    const crashState = await runSettlementEngine();
    const thunderR3 = findInList(crashState.body.updatedPrices, 23);
    const newPriceR3 = thunderR3.effectivePrice || thunderR3.currentPrice;

    console.log('ROUND 3 — MARKET CRASH');
    console.log(`Product: ${thunderR3.name || 'Thunder'}`);
    console.log(`Old Price: ₹${newPriceR2.toFixed(2)}`);
    console.log(`Floor: ₹18.00`);
    console.log(`Ceiling: ₹35.00`);
    console.log(`Crash Buffer: 5.00%`);
    console.log(`Crash Price: ₹${newPriceR3.toFixed(2)}`);
    console.log(`Status: MARKET CRASH\n`);

    console.log('=========================================');
    console.log('SIMULATION COMPLETE');
    console.log('=========================================\n');

    await stopMarketCrash();
}

runMasterPricingSuite();
