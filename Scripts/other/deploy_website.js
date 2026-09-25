#!/usr/bin/env node
/**
 * Deploys the OrganizationWebsite (rizer001-site) to the host via SSH.
 *
 * Usage (from the UltimateImprovments repo root):
 *   SSH_PASS='...' node Scripts/other/deploy_website.js
 *
 * Auth: SSH key (~/.ssh/id_ed25519, override with SSH_KEY) for login;
 *       SSH_PASS is used only for remote sudo prompts.
 *
 * Steps:
 *   1. Tar the local Next.js project (excluding node_modules/.next/.git)
 *   2. Upload it over SFTP to the host
 *   3. Extract into /opt/websites/rizer001-site (the path deploy.sh expects)
 *   4. Run k8s/deploy.sh under sudo (nerdctl build → ctr import → kubectl rollout)
 *
 * Requires: ssh2 (npm i ssh2), tar in PATH (Git Bash has it).
 */

const path = require('path');
const os = require('os');
const { execSync, spawn } = require('child_process');
const fs = require('fs');
const { Client } = require('ssh2');

const HOST = 'rizer001.opik.net';
const USER = 'user';
const SUDO_PASS = process.env.SSH_PASS || process.env.SUDO_PASS; // only for remote sudo
const KEY_PATH = process.env.SSH_KEY || path.join(os.homedir(), '.ssh', 'id_ed25519');
const REMOTE_DIR = '/opt/websites/rizer001-site';
const WEBSITE_DIR = path.resolve(__dirname, '..', '..', '..', 'OrganizationWebsite');
const TARBALL_REMOTE = '/tmp/rizer001-site.tar.gz';

if (!SUDO_PASS) {
  console.error('SSH_PASS (remote sudo password) env var is required');
  process.exit(1);
}
if (!fs.existsSync(KEY_PATH)) {
  console.error('SSH key not found at ' + KEY_PATH + ' (set SSH_KEY to override)');
  process.exit(1);
}
if (!fs.existsSync(path.join(WEBSITE_DIR, 'package.json'))) {
  console.error('Website project not found at ' + WEBSITE_DIR);
  process.exit(1);
}

// ── 1. Local tar ─────────────────────────────────────────────────────────────
console.log('[1/4] Packing website (excluding node_modules/.next/.git)...');
execSync(
  `tar -czf rizer001-site.tar.gz --warning=no-file-changed --exclude=rizer001-site.tar.gz --exclude=node_modules --exclude=.next --exclude=.git .`,
  { cwd: WEBSITE_DIR, stdio: 'inherit' }
);
const TARBALL_LOCAL = path.join(WEBSITE_DIR, 'rizer001-site.tar.gz');
const sizeMb = (fs.statSync(TARBALL_LOCAL).size / 1024 / 1024).toFixed(1);
console.log(`      packed: ${sizeMb} MB`);

const conn = new Client();

function sudoExec(cmd) {
  return new Promise((resolve, reject) => {
    conn.exec(cmd, { pty: true }, (err, stream) => {
      if (err) return reject(err);
      let out = '';
      stream
        .on('close', (code) => (code === 0 ? resolve(out) : reject(new Error(`exit ${code}\n${out}`))))
        .on('data', (d) => {
          const s = d.toString();
          out += s;
          process.stdout.write(s);
          if (s.includes('[sudo] password') || s.includes('password for')) {
            stream.write(SUDO_PASS + '\n');
          }
        })
        .stderr.on('data', (d) => {
          const s = d.toString();
          out += s;
          process.stderr.write(s);
          if (s.includes('[sudo] password') || s.includes('password for')) {
            stream.write(SUDO_PASS + '\n');
          }
        });
      // password is sent only when the sudo prompt appears (handlers above)
    });
  });
}

conn
  .on('ready', async () => {
    try {
      // ── 2. Upload ──────────────────────────────────────────────────────────
      console.log('[2/4] Uploading tarball via SFTP...');
      await new Promise((resolve, reject) => {
        conn.sftp((err, sftp) => {
          if (err) return reject(err);
          const rs = fs.createReadStream(TARBALL_LOCAL);
          const ws = sftp.createWriteStream(TARBALL_REMOTE);
          ws.on('error', reject).on('close', resolve);
          rs.pipe(ws);
        });
      });
      console.log('      uploaded');

      // ── 3. Extract ─────────────────────────────────────────────────────────
      console.log(`[3/4] Extracting into ${REMOTE_DIR}...`);
      await sudoExec(
        `sudo -S mkdir -p ${REMOTE_DIR} && sudo -S tar -xzf ${TARBALL_REMOTE} -C ${REMOTE_DIR} && sudo -S rm -f ${TARBALL_REMOTE} && echo EXTRACT_OK`
      );

      // ── 4. Deploy (nerdctl build + k8s rollout) ────────────────────────────
      console.log('[4/4] Running deploy.sh (build + rollout, may take minutes)...');
      await sudoExec(`sudo -S bash ${REMOTE_DIR}/k8s/deploy.sh`);
      console.log('\n=== Deploy finished ===');
      conn.end();
      process.exit(0);
    } catch (e) {
      console.error('\nDEPLOY FAILED: ' + e.message);
      conn.end();
      process.exit(1);
    }
  })
  .on('error', (e) => {
    console.error('SSH error: ' + e.message);
    process.exit(1);
  })
  .connect({
    host: HOST,
    port: 22,
    username: USER,
    privateKey: fs.readFileSync(KEY_PATH),
    readyTimeout: 30000,
  });
