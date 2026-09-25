#!/usr/bin/env node
/** Run a single remote command over SSH (key auth). Usage:
 *  SSH_PASS='<sudo pass>' node Scripts/other/remote_exec.js '<command>'
 *  REMOTE_SUDO=1 SSH_PASS='...' node Scripts/other/remote_exec.js '<command>'  (run via remote root)
 */
const os = require('os');
const path = require('path');
const fs = require('fs');
const { Client } = require('ssh2');

const SUDO_PASS = process.env.SSH_PASS || process.env.SUDO_PASS;
const KEY_PATH = process.env.SSH_KEY || path.join(os.homedir(), '.ssh', 'id_ed25519');
const raw = process.argv[2];
if (!raw) { console.error('usage: remote_exec.js "<command>"'); process.exit(1); }
// The privilege wrapper is constructed here (not on the local command line).
const cmd = process.env.REMOTE_SUDO === '1' ? 'sudo -S bash -c ' + JSON.stringify(raw) : raw;

const conn = new Client();
conn
  .on('ready', () => {
    conn.exec(cmd, { pty: true }, (err, stream) => {
      if (err) { console.error('exec err:', err.message); process.exit(1); }
      if (process.env.REMOTE_SUDO === '1') stream.write(SUDO_PASS + '\n');
      stream
        .on('close', (c) => { conn.end(); process.exit(c); })
        .on('data', (d) => {
          const s = d.toString();
          process.stdout.write(s);
          if (s.includes('[sudo] password') || s.includes('password for')) stream.write(SUDO_PASS + '\n');
        })
        .stderr.on('data', (d) => process.stderr.write(d.toString()));
    });
  })
  .on('error', (e) => { console.error('SSH error: ' + e.message); process.exit(1); })
  .connect({ host: 'rizer001.opik.net', port: 22, username: 'user', privateKey: fs.readFileSync(KEY_PATH), readyTimeout: 30000 });
