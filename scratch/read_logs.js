const fs = require('fs');
const content = fs.readFileSync('C:/Users/reach/.gemini/antigravity-ide/brain/2183d9ff-8a08-44c0-9bdb-89b7dd7d3f05/.system_generated/logs/transcript.jsonl', 'utf8');
const lines = content.split('\n');
for (let i = 0; i < lines.length; i++) {
  if (lines[i].includes('capture_browser_console_logs')) {
    console.log('--- FOUND AT LINE ' + i + ' ---');
    console.log(lines[i].substring(0, 300));
    if (lines[i+1]) {
      console.log('NEXT LINE:');
      console.log(lines[i+1].substring(0, 500));
    }
  }
}
