const fs = require('fs');
const path = require('path');

const cyr = /[\u0400-\u04FF]/;
const base = 'src/main/java';
const files = [];
let total = 0;

function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p);
    else if (entry.name.endsWith('.java')) {
      let txt;
      try { txt = fs.readFileSync(p, 'utf8'); } catch (e) { continue; }
      const lines = txt.split('\n');
      const n = lines.filter(l => cyr.test(l)).length;
      if (n > 0) { files.push({ n, p }); total += n; }
    }
  }
}
walk(base);
files.sort((a, b) => b.n - a.n);
console.log('files_with_cyrillic:', files.length);
console.log('cyrillic_lines_total:', total);
console.log('--- all files sorted by count ---');
for (const f of files) console.log(String(f.n).padStart(6), f.p);
