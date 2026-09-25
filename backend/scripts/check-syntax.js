const fs = require('fs');
const vm = require('vm');

const files = [
  'admin-panel/src/index.html',
  'customer-web/src/index.html',
  'customer-web/src/led-display.html'
];

files.forEach(file => {
  console.log(`Checking ${file}...`);
  const html = fs.readFileSync(file, 'utf8');
  const regex = /<script\b[^>]*>([\s\S]*?)<\/script>/gi;
  let match;
  let scriptIdx = 0;
  while ((match = regex.exec(html)) !== null) {
    scriptIdx++;
    const code = match[1];
    if (!code.trim()) continue;
    try {
      new vm.Script(code);
      console.log(`  Script #${scriptIdx}: Syntax OK`);
    } catch (err) {
      console.error(`  Script #${scriptIdx}: SYNTAX ERROR: ${err.message}`);
    }
  }
});
