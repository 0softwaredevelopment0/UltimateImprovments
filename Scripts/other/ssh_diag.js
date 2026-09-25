#!/usr/bin/env node
/**
 * SSH auth diagnostics: shows which auth methods the server advertises
 * and tries password + keyboard-interactive.
 *
 * Usage: SSH_PASS='...' node Scripts/other/ssh_diag.js [user@host]
 */
const { Client } = require('ssh2');

const PASS = process.env.SSH_PASS;
const [userArg, hostArg] = (process.argv[2] || 'user@rizer001.opik.net').split('@');
const USER = userArg || 'user';
const HOST = hostArg || 'rizer001.opik.net';

console.log(`Dialing ${USER}@${HOST}:22 ...`);

const conn = new Client();
conn
  .on('ready', () => {
    console.log('AUTH OK: connection ready');
    conn.exec('echo REMOTE_OK && id', (err, stream) => {
      if (err) { console.error('exec err:', err.message); conn.end(); return; }
      stream.on('close', () => conn.end()).on('data', (d) => process.stdout.write(d.toString()))
        .stderr.on('data', (d) => process.stderr.write(d.toString()));
    });
  })
  .on('keyboard-interactive', (name, instr, lang, prompts, finish) => {
    console.log('keyboard-interactive prompts:', JSON.stringify(prompts));
    finish([PASS]);
  })
  .on('error', (e) => {
    console.error('ERROR level=' + e.level + ' reason=' + e.reason);
    console.error('description:', e.description);
    console.error('message:', e.message);
    process.exit(1);
  })
  .connect({
    host: HOST,
    port: 22,
    username: USER,
    password: PASS,
    readyTimeout: 30000,
    tryKeyboard: true,
    authHandler: ['password', 'keyboard-interactive'],
    debug: (s) => { if (/auth|USERAUTH|banner|Handshake|identify/i.test(s)) console.log('[dbg]', s); },
  });
