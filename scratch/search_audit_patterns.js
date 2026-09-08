const fs = require('fs');
const path = require('path');

const targets = [
  'new BigDecimal("-2.00")',
  '"-2.00"',
  'movement = -2',
  'decrease step 2',
  'DECREASE_STEP_2',
  '|| 18',
  '|| 20',
  '|| 25',
  '|| 30',
  '|| 35'
];

function searchDir(dir, results = []) {
  const list = fs.readdirSync(dir);
  for (const file of list) {
    if (file === 'node_modules' || file === '.git' || file === 'target' || file === '.gemini') continue;
    const fullPath = path.join(dir, file);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      searchDir(fullPath, results);
    } else if (stat.isFile()) {
      const ext = path.extname(fullPath);
      if (['.java', '.html', '.js', '.ts', '.sql'].includes(ext)) {
        const content = fs.readFileSync(fullPath, 'utf8');
        const lines = content.split('\n');
        lines.forEach((line, idx) => {
          targets.forEach(tgt => {
            if (line.includes(tgt)) {
              results.push({ file: fullPath.replace(/\\/g, '/'), line: idx + 1, target: tgt, text: line.trim() });
            }
          });
        });
      }
    }
  }
  return results;
}

const found = searchDir('.');
console.log('Search matches found: ' + found.length);
console.log(JSON.stringify(found, null, 2));
