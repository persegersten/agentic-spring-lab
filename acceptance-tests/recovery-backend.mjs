import { spawn } from 'node:child_process'
import { closeSync, mkdtempSync, openSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

export const recoveryBackendUrl = 'http://127.0.0.1:18082'

/** Own only the child process group created here; never stop an existing server. */
export function recoveryBackend() {
  const directory = mkdtempSync(join(tmpdir(), 'wreckage-flag-recovery-'))
  const database = join(directory, 'match')
  const logPath = join(directory, 'backend.log')
  closeSync(openSync(logPath, 'a'))
  let child = null
  let closed = null

  async function probe(path) {
    try {
      const response = await fetch(`${recoveryBackendUrl}${path}`, { signal: AbortSignal.timeout(1500) })
      await response.arrayBuffer()
      return response
    } catch { return null }
  }

  async function start() {
    const existing = await probe('/games/running')
    if (existing) throw new Error('Port 18082 is occupied. Stop the manual recovery server before running this test.')
    const log = openSync(logPath, 'a')
    let spawnError = null
    try {
      child = spawn('bash', ['mvnw', '-q', 'spring-boot:test-run',
        '-Dspring-boot.run.profiles=sequential-flags-e2e,flag-recovery-e2e',
        '-Dspring-boot.run.arguments=--server.port=18082 --wreckage.games.max-concurrent=50'], {
        cwd: fileURLToPath(new URL('../backend/', import.meta.url)),
        env: { ...process.env, WRECKAGE_TEST_RECOVERY_DB: database },
        detached: true,
        stdio: ['ignore', log, log],
      })
      child.on('error', error => { spawnError = error })
      closed = new Promise(resolve => child.once('close', () => resolve(true)))
    } finally { closeSync(log) }
    const deadline = Date.now() + 120_000
    while (Date.now() < deadline) {
      if (spawnError || child.exitCode != null || child.signalCode != null) {
        throw new Error(`Recovery backend stopped: ${spawnError ?? child.exitCode}\n${readFileSync(logPath, 'utf8').slice(-8000)}`)
      }
      const ready = await probe('/games/test-fixtures/flags')
      if (ready?.ok) return
      await delay(250)
    }
    throw new Error(`Recovery backend did not become ready. See ${logPath}`)
  }

  async function stop() {
    if (!child?.pid || child.exitCode != null || child.signalCode != null) return
    try { process.kill(-child.pid, 'SIGTERM') } catch (error) { if (error.code !== 'ESRCH') throw error }
    let timer
    const stopped = await Promise.race([closed, new Promise(resolve => { timer = setTimeout(() => resolve(false), 15_000) })])
    clearTimeout(timer)
    if (!stopped) {
      try { process.kill(-child.pid, 'SIGKILL') } catch (error) { if (error.code !== 'ESRCH') throw error }
      await closed
      throw new Error(`Recovery backend required forced shutdown. See ${logPath}`)
    }
    // Maven can exit before Tomcat has closed its listener during graceful shutdown.
    const deadline = Date.now() + 10_000
    while (await probe('/games/running')) {
      if (Date.now() > deadline) throw new Error(`Recovery backend did not release port 18082. See ${logPath}`)
      await delay(250)
    }
    child = null
  }

  return { database, logPath, start, stop }
}
