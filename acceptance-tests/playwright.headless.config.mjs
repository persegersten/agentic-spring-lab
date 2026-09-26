import baseConfig from './playwright.config.mjs'
import { defineConfig } from '@playwright/test'

export default defineConfig({
  ...baseConfig,
  testMatch: 'headless-players.spec.mjs',
  testIgnore: [],
  webServer: [
    {
      command: '../start-server.sh in-memory headless-players',
      url: 'http://127.0.0.1:8080/games/running',
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: 'npm --prefix ../frontend run dev -- --host 127.0.0.1',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
})
