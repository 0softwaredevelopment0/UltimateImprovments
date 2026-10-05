// Patches darkfusionreactor.nbt: template cell (0,2,2) [anchor-relative (-5,-7,-2),
// the wall cell above the Power Stats sign] changes from air to an acacia wall
// sign facing west (existing palette entry 22) — making the Content Absorber
// panel a mandatory structure cell at assembly.
//
// Surgical byte patch: finds the unique blocks entry with pos [0,2,2] and
// rewrites its "state" int from 6 (air) to 22 (acacia_wall_sign, facing=west).
// Usage: node patch_reactor_nbt_absorber_sign.js [revert]
const fs = require('fs');
const zlib = require('zlib');

const file = 'ui-mbs/src/main/resources/NBT-Files/darkfusionreactor.nbt';
const revert = process.argv[2] === 'revert';

let data = zlib.gunzipSync(fs.readFileSync(file));

// TAG_List(Int) "pos" = [0,2,2], followed by TAG_Int "state" = value
const posPattern = Buffer.from([
  0x09, 0x00, 0x03, 0x70, 0x6F, 0x73, 0x03, 0x00, 0x00, 0x00, 0x03,
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x02,
  0x03, 0x00, 0x05, 0x73, 0x74, 0x61, 0x74, 0x65,
]);
const matches = [];
let idx = data.indexOf(posPattern);
while (idx !== -1) { matches.push(idx); idx = data.indexOf(posPattern, idx + 1); }

if (matches.length !== 1) {
  console.error('Expected exactly 1 match for pos [0,2,2], found ' + matches.length + ' — aborting');
  process.exit(1);
}

const stateOff = matches[0] + posPattern.length;
const oldState = data.readUInt32BE(stateOff);
const newState = revert ? 6 : 22;

if (!revert) {
  if (oldState !== 6) {
    console.error('Cell state is ' + oldState + ' (expected 6 = air) — already patched? Aborting');
    process.exit(1);
  }
} else {
  if (oldState !== 22) {
    console.error('Cell state is ' + oldState + ' (expected 22 = acacia_wall_sign) — nothing to revert');
    process.exit(1);
  }
}

data.writeUInt32BE(newState, stateOff);
fs.writeFileSync(file, zlib.gzipSync(data, { level: 9 }));
console.log('Patched template cell (0,2,2): state ' + oldState + ' -> ' + newState
  + (revert ? ' (air, reverted)' : ' (acacia_wall_sign facing=west)'));
