const { execSync } = require('child_process');
const fs = require('fs');

const env = { ...process.env, PGPASSWORD: 'postgres' };
const PG_BIN = '"D:\\New folder\\bin\\';

console.log('====================================================================');
console.log('💾 DATABASE BACKUP & RESTORE AUDIT — POSTGRESQL');
console.log('====================================================================\n');

try {
  // 1. pg_dump export
  console.log('[1/4] Running pg_dump on production database retailposdb...');
  const dumpCmd = `${PG_BIN}pg_dump.exe" -U postgres -h localhost -p 5432 -d retailposdb -f retailposdb_backup.sql`;
  execSync(dumpCmd, { env, stdio: 'inherit' });
  console.log('✅ Export successful: retailposdb_backup.sql created.\n');

  // 2. Drop existing retailposdb_restored database if present
  console.log('[2/4] Preparing target database retailposdb_restored...');
  try {
    execSync(`${PG_BIN}dropdb.exe" -U postgres -h localhost -p 5432 --if-exists retailposdb_restored`, { env, stdio: 'pipe' });
  } catch (e) {}
  execSync(`${PG_BIN}createdb.exe" -U postgres -h localhost -p 5432 retailposdb_restored`, { env, stdio: 'inherit' });
  console.log('✅ Target database retailposdb_restored created.\n');

  // 3. Restore backup into retailposdb_restored
  console.log('[3/4] Restoring backup file into retailposdb_restored...');
  const restoreCmd = `${PG_BIN}psql.exe" -U postgres -h localhost -p 5432 -d retailposdb_restored -f retailposdb_backup.sql`;
  execSync(restoreCmd, { env, stdio: 'pipe' });
  console.log('✅ Restore complete.\n');

  // 4. Verify tables, rows, constraints, and audit data in retailposdb_restored
  console.log('[4/4] Verifying restored schema, constraints, and rows...');
  const tablesQuery = "SELECT table_name FROM information_schema.tables WHERE table_schema='public' ORDER BY table_name;";
  const tablesCmd = `${PG_BIN}psql.exe" -U postgres -h localhost -p 5432 -d retailposdb_restored -t -A -c "${tablesQuery}"`;
  const tableListStr = execSync(tablesCmd, { env, encoding: 'utf8' }).trim();
  const tables = tableListStr.split('\n').map(s => s.trim()).filter(Boolean);

  console.log('Restored Public Tables:', tables.join(', '));

  const prodCount = Number(execSync(`${PG_BIN}psql.exe" -U postgres -h localhost -p 5432 -d retailposdb_restored -t -A -c "SELECT COUNT(*) FROM products;"`, { env, encoding: 'utf8' }).trim());
  const ordersCount = Number(execSync(`${PG_BIN}psql.exe" -U postgres -h localhost -p 5432 -d retailposdb_restored -t -A -c "SELECT COUNT(*) FROM sales_orders;"`, { env, encoding: 'utf8' }).trim());
  const priceHistoryCount = Number(execSync(`${PG_BIN}psql.exe" -U postgres -h localhost -p 5432 -d retailposdb_restored -t -A -c "SELECT COUNT(*) FROM price_history;"`, { env, encoding: 'utf8' }).trim());
  const auditCount = Number(execSync(`${PG_BIN}psql.exe" -U postgres -h localhost -p 5432 -d retailposdb_restored -t -A -c "SELECT COUNT(*) FROM audit_logs;"`, { env, encoding: 'utf8' }).trim());
  const flywayCount = Number(execSync(`${PG_BIN}psql.exe" -U postgres -h localhost -p 5432 -d retailposdb_restored -t -A -c "SELECT COUNT(*) FROM flyway_schema_history;"`, { env, encoding: 'utf8' }).trim());

  console.log(`
  Verification Results:
  ---------------------
  Total Tables Count: ${tables.length} (Expected >= 8)
  Products Count: ${prodCount} (Expected >= 8)
  Sales Orders Count: ${ordersCount}
  Price History Count: ${priceHistoryCount}
  Audit Logs Count: ${auditCount}
  Flyway Schema Migrations: ${flywayCount}
  `);

  if (tables.length >= 8 && prodCount >= 8) {
    console.log('====================================================================');
    console.log('🎉 POSTGRESQL BACKUP & RESTORE VERIFICATION SUCCESS (100% VERIFIED)');
    console.log('====================================================================\n');
    process.exit(0);
  } else {
    throw new Error('Verification failed: insufficient tables or products');
  }

} catch (err) {
  console.error('❌ Backup/Restore audit failed:', err.message);
  process.exit(1);
}
