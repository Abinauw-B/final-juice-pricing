const { Client } = require('pg');
const c = new Client({ host:'localhost', port:5432, user:'postgres', password:'postgres', database:'retailposdb' });

async function main() {
  await c.connect();
  console.log('Connected to PostgreSQL database retailposdb.');
  await c.query("ALTER TABLE products ADD COLUMN IF NOT EXISTS pricing_mode VARCHAR(30) NOT NULL DEFAULT 'DYNAMIC'");
  console.log('Successfully added pricing_mode column to products table.');
  const res = await c.query("SELECT id, name, current_cup_price, pricing_mode FROM products WHERE is_active = true ORDER BY id ASC");
  console.table(res.rows);
  await c.end();
}

main().catch(err => {
  console.error('Migration failed:', err);
  process.exit(1);
});
