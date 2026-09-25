const http = require('http');
const { execSync } = require('child_process');

const API_BASE = 'http://localhost:8088/api';
const PSQL = '"D:\\New folder\\bin\\psql.exe" -U postgres -d retailposdb -t -A -c';

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

function queryDb(sql) {
    try {
        const cmd = `${PSQL} "${sql.replace(/"/g, '\\"')}"`;
        const output = execSync(cmd, { encoding: 'utf8' }).trim();
        return output;
    } catch (e) {
        console.error('DB Query Error:', e.message);
        return '';
    }
}

async function resetAllPricesTo25() {
    await request('/pricing/reset-all', { method: 'POST' });
    queryDb("TRUNCATE TABLE pricing_processed_sales RESTART IDENTITY;");
    queryDb("DELETE FROM sales_order_items; DELETE FROM sales_orders;");
    await request('/pricing/reset-all', { method: 'POST' });
}

async function setProductPrice(productId, price) {
    return await request('/pricing/deploy', {
        method: 'POST',
        body: {
            productId: productId,
            price: price,
            currentPrice: price,
            currentCupPrice: price
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

async function fetchLiveProducts() {
    const res = await request('/pos/products');
    return res.body;
}

function findProduct(list, id) {
    if (!Array.isArray(list)) return null;
    return list.find(p => (p.id === id || p.beverageId === id || p.productId === id));
}

function getPrice(p) {
    if (!p) return 25.0;
    return p.currentCupPrice !== undefined ? p.currentCupPrice : (p.currentPrice !== undefined ? p.currentPrice : 25.0);
}

async function runProductIsolationPricingTest() {
    console.log('============================================================');
    console.log('PRODUCT ISOLATION PRICING TEST SUITE');
    console.log('============================================================\n');

    let allPassed = true;

    // STEP 1: Clean Reset
    await resetAllPricesTo25();
    console.log('✔ Reset all 8 product prices to ₹25.00');
    console.log('✔ Cleared pricing_processed_sales & sales_orders test data\n');

    // STEP 2: Record initial prices
    console.log('BEFORE PURCHASE:\n');
    const initialList = await fetchLiveProducts();
    const productIds = [1, 2, 3, 4, 5, 6, 7, 23];
    productIds.forEach(id => {
        const p = findProduct(initialList, id);
        console.log(`${(p.flavour || p.name).padEnd(20)}: ₹${getPrice(p)}`);
    });

    // STEP 3: Buy THUNDER x1 (Target = 0.9, Demand Ratio = 1.111)
    console.log('\n------------------------------------------------------------');
    console.log('TEST 1: BUY THUNDER x1 (Target = 0.9, Demand Ratio = 1.111)');
    console.log('------------------------------------------------------------\n');

    const preThunderList = await fetchLiveProducts();
    const preThunderPrice = getPrice(findProduct(preThunderList, 23));

    const thunderChk = await checkoutProduct(23, 1);
    console.log(`🛒 POS Order #${thunderChk.body.orderNumber} (Thunder x 1) Committed to DB.`);

    const postThunderList = await fetchLiveProducts();
    const thunderP = findProduct(postThunderList, 23);
    const thunderPrice = getPrice(thunderP);

    console.log(`\nTHUNDER Pre-Purchase Price : ₹${preThunderPrice}`);
    console.log(`THUNDER Post-Purchase Price: ₹${thunderPrice} (Expected: ₹${preThunderPrice + 1})`);

    if (thunderPrice === preThunderPrice + 1) {
        console.log(`✅ PASS: Thunder price increased linearly by +₹1 from ₹${preThunderPrice} to ₹${thunderPrice}!`);
    } else {
        console.error(`❌ FAIL: Thunder price is ₹${thunderPrice}`);
        allPassed = false;
    }

    console.log('\nOTHER PRODUCTS AFTER THUNDER PURCHASE:');
    let thunderContamination = false;
    [1, 2, 3, 4, 5, 6, 7].forEach(id => {
        const pPre = findProduct(preThunderList, id);
        const pPost = findProduct(postThunderList, id);
        const oldP = getPrice(pPre);
        const newP = getPrice(pPost);
        console.log(`${(pPost.flavour || pPost.name).padEnd(20)}: Old: ₹${oldP} -> New: ₹${newP}`);
        if (newP > oldP) {
            thunderContamination = true;
        }
    });

    if (!thunderContamination) {
        console.log('\n✅ PASS: Zero cross-product contamination! No other product received an upward surge.');
    } else {
        console.error('\n❌ FAIL: Cross-product contamination detected!');
        allPassed = false;
    }

    // STEP 4: 5 Repeated Settlements for Thunder
    console.log('\n------------------------------------------------------------');
    console.log('TEST 2: REPEATED SETTLEMENT IDEMPOTENCY (No Duplicate Surges)');
    console.log('------------------------------------------------------------\n');

    let repPassed = true;
    const baseRepPrice = thunderPrice;
    for (let i = 1; i <= 5; i++) {
        const sRep = await runSettlementEngine();
        const tRep = findProduct(sRep.body.updatedPrices, 23);
        const pRep = getPrice(tRep);
        console.log(`Settlement #${i} -> Thunder Price: ₹${pRep}`);
        if (pRep > baseRepPrice) {
            repPassed = false;
        }
    }

    if (repPassed) {
        console.log(`✅ PASS: Thunder did not surge repeatedly beyond ₹${baseRepPrice} across 5 settlements!`);
    } else {
        console.error('❌ FAIL: Duplicate surges detected during settlement!');
        allPassed = false;
    }

    // STEP 5: Buy ORANGE x2 (Target = 1.1, Demand Ratio = 1.818)
    console.log('\n------------------------------------------------------------');
    console.log('TEST 3: BUY ORANGE x2 (Target = 1.1, Demand Ratio = 1.818)');
    console.log('------------------------------------------------------------\n');

    await setProductPrice(4, 25.0);
    const preOrangeList = await fetchLiveProducts();
    const preOrangePrice = getPrice(findProduct(preOrangeList, 4));

    const orangeChk = await checkoutProduct(4, 2);
    console.log(`🛒 POS Order #${orangeChk.body.orderNumber} (Orange x 2) Committed to DB.`);

    const postOrangeList = await fetchLiveProducts();
    const orangeP = findProduct(postOrangeList, 4);
    const orangePrice = getPrice(orangeP);

    console.log(`\nORANGE Pre-Purchase Price : ₹${preOrangePrice}`);
    console.log(`ORANGE Post-Purchase Price: ₹${orangePrice} (Expected: ₹${preOrangePrice + 1})`);

    if (orangePrice === preOrangePrice + 1) {
        console.log(`✅ PASS: Orange price increased linearly by +₹1 from ₹${preOrangePrice} to ₹${orangePrice}!`);
    } else {
        console.error(`❌ FAIL: Orange price is ₹${orangePrice}`);
        allPassed = false;
    }

    console.log('\nOTHER PRODUCTS AFTER ORANGE PURCHASE:');
    let orangeContamination = false;
    [1, 2, 3, 5, 6, 7, 23].forEach(id => {
        const pPre = findProduct(preOrangeList, id);
        const pPost = findProduct(postOrangeList, id);
        const oldP = getPrice(pPre);
        const newP = getPrice(pPost);
        console.log(`${(pPost.flavour || pPost.name).padEnd(20)}: Old: ₹${oldP} -> New: ₹${newP}`);
        if (newP > oldP) {
            orangeContamination = true;
        }
    });

    if (!orangeContamination) {
        console.log('\n✅ PASS: Zero cross-product contamination! Thunder and other 6 products did not surge.');
    } else {
        console.error('\n❌ FAIL: Cross-product contamination detected!');
        allPassed = false;
    }

    // STEP 6: Buy MINT x1 (Target = 0.8, Demand Ratio = 1.250)
    console.log('\n------------------------------------------------------------');
    console.log('TEST 4: BUY MINT x1 (Target = 0.8, Demand Ratio = 1.250)');
    console.log('------------------------------------------------------------\n');

    await setProductPrice(3, 25.0);
    const preMintList = await fetchLiveProducts();
    const preMintPrice = getPrice(findProduct(preMintList, 3));

    const mintChk = await checkoutProduct(3, 1);
    console.log(`🛒 POS Order #${mintChk.body.orderNumber} (Mint x 1) Committed to DB.`);

    const postMintList = await fetchLiveProducts();
    const mintP = findProduct(postMintList, 3);
    const mintPrice = getPrice(mintP);

    console.log(`\nMINT Pre-Purchase Price : ₹${preMintPrice}`);
    console.log(`MINT Post-Purchase Price: ₹${mintPrice} (Expected: ₹${preMintPrice + 1})`);

    if (mintPrice === preMintPrice + 1) {
        console.log(`✅ PASS: Mint price increased linearly by +₹1 from ₹${preMintPrice} to ₹${mintPrice}!`);
    } else {
        console.error(`❌ FAIL: Mint price is ₹${mintPrice}`);
        allPassed = false;
    }

    console.log('\nOTHER PRODUCTS AFTER MINT PURCHASE:');
    let mintContamination = false;
    [1, 2, 4, 5, 6, 7, 23].forEach(id => {
        const pPre = findProduct(preMintList, id);
        const pPost = findProduct(postMintList, id);
        const oldP = getPrice(pPre);
        const newP = getPrice(pPost);
        console.log(`${(pPost.flavour || pPost.name).padEnd(20)}: Old: ₹${oldP} -> New: ₹${newP}`);
        if (newP > oldP) {
            mintContamination = true;
        }
    });

    if (!mintContamination) {
        console.log('\n✅ PASS: Zero cross-product contamination! All other products did not surge.');
    } else {
        console.error('\n❌ FAIL: Cross-product contamination detected!');
        allPassed = false;
    }

    console.log('\n============================================================');
    if (allPassed) {
        console.log('🏆 PRODUCT ISOLATION PRICING TEST SUITE PASSED 100%!');
    } else {
        console.log('⚠️ PRODUCT ISOLATION PRICING TEST SUITE HAD FAILURES!');
    }
    console.log('============================================================\n');
}

runProductIsolationPricingTest();
