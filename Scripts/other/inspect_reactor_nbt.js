// One-off inspector for darkfusionreactor.nbt (structure block NBT, gzip).
// Prints: size, palette, all sign cells, and the cell above Power Stats.
const fs = require('fs');
const zlib = require('zlib');

const path = process.argv[2] || 'ui-mbs/src/main/resources/NBT-Files/darkfusionreactor.nbt';
const buf = zlib.gunzipSync(fs.readFileSync(path));

let off = 0;
function u8() { return buf[off++]; }
function i16() { const v = buf.readInt16BE(off); off += 2; return v; }
function i32() { const v = buf.readInt32BE(off); off += 4; return v; }
function i64() { const v = buf.readBigInt64BE(off); off += 8; return v; }
function f32() { const v = buf.readFloatBE(off); off += 4; return v; }
function f64() { const v = buf.readDoubleBE(off); off += 8; return v; }
function str() { const len = buf.readUInt16BE(off); off += 2; const s = buf.toString('utf8', off, off + len); off += len; return s; }

function payload(type) {
  switch (type) {
    case 1: return u8();
    case 2: return i16();
    case 3: return i32();
    case 4: return i64();
    case 5: return f32();
    case 6: return f64();
    case 7: { const len = i32(); const a = buf.subarray(off, off + len); off += len; return a; }
    case 8: return str();
    case 9: { const elType = u8(); const len = i32(); const out = []; for (let i = 0; i < len; i++) out.push(elType === 0 ? null : payload(elType)); return out; }
    case 10: { const out = {}; for (;;) { const t = u8(); if (t === 0) return out; const name = str(); out[name] = payload(t); } }
    case 11: { const len = i32(); const out = []; for (let i = 0; i < len; i++) out.push(i32()); return out; }
    case 12: { const len = i32(); const out = []; for (let i = 0; i < len; i++) out.push(i64()); return out; }
    default: throw new Error('bad tag ' + type);
  }
}

const rootType = u8(); // 10
str(); // root name ""
const root = payload(rootType);

const size = root.size;
console.log('size (x,y,z):', size.join(' x '));
console.log('DataVersion:', root.DataVersion);

const paletteLines = root.palette.map((p, i) => `${i}: ${p.Name}${p.Properties ? ' [' + Object.entries(p.Properties).map(([k, v]) => k + '=' + v).join(',') + ']' : ''}`);
console.log('palette entries:', paletteLines.length);
paletteLines.forEach(l => console.log(' ', l));

const blocks = root.blocks;
console.log('blocks:', blocks.length);

// 1. All sign cells with facing (anchor-relative dx,dy,dz = tx-5, ty-9, tz-4)
console.log('\n--- SIGN CELLS (template pos -> anchor-relative) ---');
for (const b of blocks) {
  const name = root.palette[b.state].Name;
  if (name.endsWith('SIGN') || name.endsWith('sign')) {
    const [x, y, z] = b.pos;
    const props = root.palette[b.state].Properties || {};
    console.log(`tmpl(${x},${y},${z}) -> rel(${x - 5},${y - 9},${z - 4}) ${name} facing=${props.facing}`);
  }
}

// 2. Barrel cells
console.log('\n--- BARREL CELLS ---');
for (const b of blocks) {
  const name = root.palette[b.state].Name;
  if (name === 'minecraft:barrel') {
    const [x, y, z] = b.pos;
    console.log(`tmpl(${x},${y},${z}) -> rel(${x - 5},${y - 9},${z - 4})`);
  }
}

// 3. The cell above Power Stats: anchor rel (-5,-7,-2) = tmpl (0,2,2)
console.log('\n--- CELL tmpl(0,2,2) [above Power Stats] ---');
for (const b of blocks) {
  const [x, y, z] = b.pos;
  if (x === 0 && y === 2 && z === 2) {
    console.log('palette idx', b.state, '->', root.palette[b.state].Name, root.palette[b.state].Properties || '');
  }
}

// 4. Front wall column x=0: what fills ty=1..2 rows
console.log('\n--- FRONT WALL x=0 (ty=1..2, tz=0..8) ---');
for (const b of blocks) {
  const [x, y, z] = b.pos;
  if (x === 0 && (y === 1 || y === 2)) {
    const props = root.palette[b.state].Properties || {};
    console.log(`tmpl(${x},${y},${z}) rel(${x - 5},${y - 9},${z - 4}) ${root.palette[b.state].Name} ${JSON.stringify(props)}`);
  }
}

// 5. Counts per palette entry
console.log('\n--- MATERIAL COUNTS ---');
const counts = {};
for (const b of blocks) { const n = root.palette[b.state].Name; counts[n] = (counts[n] || 0) + 1; }
for (const [n, c] of Object.entries(counts).sort((a, b) => b[1] - a[1])) console.log(String(c).padStart(4), n);
