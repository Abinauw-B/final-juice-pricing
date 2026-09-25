const { Client } = require('pg');
const c = new Client({ host:'localhost', port:5432, user:'postgres', password:'postgres', database:'retailposdb' });
async function main() {
  await c.connect();
  const res = await c.query('SELECT ph.id, p.name, ph.old_price, ph.new_price, ph.reason, ph.created_at FROM price_history ph JOIN products p ON ph.product_id = p.id ORDER BY ph.id DESC LIMIT 20');
  console.table(res.rows);
  const products = await c.query('SELECT id, name, current_cup_price, default_cup_price, is_active FROM products ORDER BY id ASC');
  console.log('\nCurrent Products in PostgreSQL:');
  console.table(products.rows);
  await c.end();
}
main();
