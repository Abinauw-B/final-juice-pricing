const { Client } = require('pg');

async function clean() {
  const c = new Client({
    user: 'postgres',
    host: 'localhost',
    database: 'retailposdb',
    password: 'password',
    port: 5432,
  });

  await c.connect();
  const res = await c.query("DELETE FROM flyway_schema_history WHERE version = '31'");
  console.log('Cleaned flyway V31 rows:', res.rowCount);
  await c.end();
}

clean().catch(console.error);
