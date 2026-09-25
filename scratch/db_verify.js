const { Client } = require('pg');

async function verifyDb() {
  const client = new Client({
    host: 'localhost',
    port: 5432,
    user: 'postgres',
    password: 'postgres',
    database: 'retailposdb'
  });

  try {
    await client.connect();
    console.log('✅ PostgreSQL Connection: SUCCESS');

    const tablesRes = await client.query(
      "SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name;"
    );
    console.log('Total Tables:', tablesRes.rows.length);
    console.log('Tables:', tablesRes.rows.map(r => r.table_name).join(', '));

    const flywayRes = await client.query(
      "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"
    );
    console.log('\nLatest Flyway Migrations:');
    console.table(flywayRes.rows);

    const prodsRes = await client.query(
      "SELECT id, name, flavour, current_cup_price, default_cup_price, min_cup_price, max_cup_price, is_active, pricing_mode, image_url FROM products ORDER BY id ASC;"
    );
    console.log('\nActive Products:');
    console.table(prodsRes.rows);

    const batchesRes = await client.query(
      "SELECT id, product_id, flavour, batch_number, remaining_volume_ml, total_volume_ml, status FROM juice_batches ORDER BY id DESC LIMIT 10;"
    );
    console.log('\nJuice Batches:');
    console.table(batchesRes.rows);

    const ordersCount = await client.query("SELECT count(*) FROM sales_order;");
    const itemsCount = await client.query("SELECT count(*) FROM sales_order_item;");
    const priceHistCount = await client.query("SELECT count(*) FROM price_history;");
    const auditCount = await client.query("SELECT count(*) FROM pricing_config_audit_log;");

    console.log('\nRecord Counts:');
    console.log({
      sales_orders: ordersCount.rows[0].count,
      sales_order_items: itemsCount.rows[0].count,
      price_history_records: priceHistCount.rows[0].count,
      audit_logs: auditCount.rows[0].count
    });

    await client.end();
  } catch (err) {
    console.error('❌ DB Verification Error:', err.message);
  }
}

verifyDb();
