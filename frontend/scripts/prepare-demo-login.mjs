import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';
import { demoAvatar } from './demo-avatar.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const file = path.join(root, '.runtime/feature-demo-manifest.json');
const accountsFile = path.join(root, '.runtime/demo-login-accounts.json');
const manifest = JSON.parse(await fs.readFile(file, 'utf8'));
assert.equal(manifest.namespace, 'feature-demo-v1');
assert.equal(manifest.datasetCreated, true, 'Prepare the feature demo data first.');
const apply = process.argv.includes('--apply');
if (!apply) {
  console.log(JSON.stringify({ mode: 'dry-run', roles: ['演示学生', '演示老师', '演示管理'], existingAdmin: Boolean(manifest.accounts.admin?.id), credentialsFile: accountsFile, enabledByDefault: false }, null, 2));
} else {
  const db = await mysql.createConnection({ host: '127.0.0.1', user: process.env.DB_USERNAME || 'root', password: process.env.DB_PASSWORD || 'root', database: process.env.DEMO_DATABASE || 'learning_platform' });
  try {
    const base = process.env.DEMO_BASE_URL || 'http://127.0.0.1:8081';
    assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
    let admin = manifest.accounts.admin;
    if (!admin) {
      const [[collision]] = await db.query('select id from users where email=?', ['feature-demo-admin@zhidu.local']);
      assert.ok(!collision, 'Refusing to replace an untracked account.');
      admin = { email: 'feature-demo-admin@zhidu.local', password: `Demo!${crypto.randomBytes(6).toString('base64url')}26`, role: 'admin', name: '【演示】管理员·功能体验', bootstrapPending: true };
      manifest.accounts.admin = admin;
      await fs.writeFile(file, JSON.stringify(manifest, null, 2));
    }
    let [[user]] = await db.query('select id,name,role from users where email=?', [admin.email]);
    if (!user) {
      const response = await fetch(base + '/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: admin.name, email: admin.email, password: admin.password, role: 'student' }) });
      const registered = await response.json();
      assert.equal(response.status, 200, registered.message);
      [[user]] = await db.query('select id,name,role from users where email=?', [admin.email]);
      assert.ok(user && user.id === registered.user.id, 'The configured API and database must refer to the same local project.');
    }
    assert.equal(user.name, admin.name);
    if (user.role !== 'admin') {
      assert.equal(user.role, 'student');
      assert.equal(admin.bootstrapPending, true);
      // Promotion is an explicit local setup operation, never added to public registration.
      const [updated] = await db.query("update users set role='admin' where id=? and email=? and name=? and role='student'", [user.id, admin.email, admin.name]);
      assert.equal(updated.affectedRows, 1);
    }
    admin.id = user.id;
    admin.bootstrapPending = false;
    admin.avatarUrl = '/uploads/avatars/feature-demo-v1-admin.png';
    await fs.mkdir(path.join(root, 'backend/uploads/avatars'), { recursive: true });
    await fs.writeFile(path.join(root, 'backend/uploads/avatars/feature-demo-v1-admin.png'), demoAvatar('A', '735344'));
    await db.query("update users set avatar_url=? where id=? and email=? and role='admin'", [admin.avatarUrl, admin.id, admin.email]);
    const configured = {};
    for (const [role, key] of [['student', 'weak'], ['teacher', 'teacher'], ['admin', 'admin']]) {
      const account = manifest.accounts[key];
      const [[current]] = await db.query('select id,role from users where id=? and email=?', [account.id, account.email]);
      assert.equal(current?.role, role);
      configured[role] = { email: account.email, password: account.password };
    }
    await fs.writeFile(accountsFile, JSON.stringify(configured, null, 2), { mode: 0o600 });
    await fs.writeFile(path.join(root, '.runtime/demo-login.local'),
      '# Local demo login, generated only after explicit setup.\napp.demo-login.enabled=${DEMO_LOGIN_ENABLED:true}\napp.demo-login.accounts-file=' + accountsFile.replaceAll('\\', '/') + '\n');
    manifest.demoLoginAccountsFile = accountsFile;
    await fs.writeFile(file, JSON.stringify(manifest, null, 2));
    console.log(JSON.stringify({ roles: Object.keys(configured), adminAccountId: admin.id, credentialsFile: accountsFile, existingAccountPasswordsUnchanged: true }, null, 2));
  } finally { await db.end(); }
}
