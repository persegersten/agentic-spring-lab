import { fileURLToPath } from 'node:url'
import base from './playwright.config.mjs'

export default {
  ...base,
  testMatch: '**/flag-recovery.spec.mjs',
  testIgnore: [],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  use: { ...base.use, baseURL: 'http://127.0.0.1:5176' },
  // The test owns the backend process so it can stop/restart the same database.
  webServer: [{
    command: 'npm run dev -- --host 127.0.0.1 --port 5176 --strictPort',
    cwd: fileURLToPath(new URL('../frontend/', import.meta.url)),
    env: { WRECKAGE_BACKEND_URL: 'http://127.0.0.1:18082' },
    url: 'http://127.0.0.1:5176',
    reuseExistingServer: false,
    timeout: 120_000,
  }],
}
