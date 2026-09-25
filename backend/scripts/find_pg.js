const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function findFiles(dir, filter) {
  let results = [];
  try {
    const list = fs.readdirSync(dir);
    for (const file of list) {
      const filePath = path.join(dir, file);
      try {
        const stat = fs.statSync(filePath);
        if (stat && stat.isDirectory()) {
          results = results.concat(findFiles(filePath, filter));
        } else if (file.toLowerCase() === filter.toLowerCase()) {
          results.push(filePath);
        }
      } catch (e) {}
    }
  } catch (e) {}
  return results;
}

console.log('Searching for pg_dump.exe in C:\\Program Files, C:\\Program Files (x86), C:\\...');
const found = findFiles('C:\\Program Files', 'pg_dump.exe')
  .concat(findFiles('C:\\Program Files (x86)', 'pg_dump.exe'))
  .concat(findFiles('C:\\tools', 'pg_dump.exe'));

console.log('Found pg_dump paths:', found);
