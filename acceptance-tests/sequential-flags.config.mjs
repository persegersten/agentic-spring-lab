import { fileURLToPath } from 'node:url'
import base from './playwright.config.mjs'

const backend = 'http://127.0.0.1:18080'
const frontend = 'http://127.0.0.1:5174'

export default {
  ...base,
  testMatch: ['**/sequential-flags.spec.mjs', '**/final-flag.spec.mjs'],
  testIgnore: [],
  use: { ...base.use, baseURL: frontend },
  webServer: [
    {
      command: "bash mvnw -q spring-boot:test-run -Dspring-boot.run.profiles=in-memory,sequential-flags-e2e '-Dspring-boot.run.arguments=--server.port=18080 --wreckage.games.max-concurrent=50'",
      cwd: fileURLToPath(new URL('../backend/', import.meta.url)),
      url: `${backend}/games/test-fixtures/flags`,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 5174 --strictPort',
      cwd: fileURLToPath(new URL('../frontend/', import.meta.url)),
      env: { WRECKAGE_BACKEND_URL: backend },
      url: `${frontend}/games/test-fixtures/flags`,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
}
