import { expect, test } from '@playwright/test'

test('elimination is revealed after playback and spectators can replay', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000001'
  const playerId = '20000000-0000-0000-0000-000000000001'
  const vehicle = { id: 'v1', playerId, x: 0, y: 0, direction: 'EAST', damage: 2 }
  const other = { ...vehicle, id: 'v2', playerId: 'other', x: 1, damage: 0 }
  const state = {
    id: gameId, playerId, status: 'FINISHED',
    configuration: { maxPlayers: 2, cardsPerRound: 3, planningTimeoutSeconds: 30, joinTimeoutSeconds: 60 },
    players: [{ id: playerId, name: 'Per' }, { id: 'other', name: 'Alice' }],
    board: { width: 5, height: 5, walls: [], pits: [] },
    vehicles: [{ ...vehicle, damage: 3 }, other],
    round: { hand: [], state: {
      number: 1, phase: 'PLAYBACK', ready: { [playerId]: true, other: true },
      initialVehicles: [vehicle, other],
      playback: [{
        sequence: 1, type: 'DAMAGE', playerId, vehicleId: vehicle.id,
        sourcePlayerId: 'other', sourceVehicleId: other.id,
        oldPosition: { x: 0, y: 0 }, newPosition: { x: 0, y: 0 },
        oldDirection: 'EAST', newDirection: 'EAST', oldDamage: 2, newDamage: 3,
      }],
    } },
  }
  await page.clock.install()
  await page.addInitScript(session => sessionStorage.setItem('wreckage-session', JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
  await page.goto(`/game/${gameId}`)
  await expect(page.getByRole('button', { name: 'Pausa', exact: true })).toBeVisible()
  await expect(page.getByText('✕ Utslagen', { exact: true })).not.toBeVisible()
  await expect(page.getByText('Alice vann!', { exact: true })).not.toBeVisible()
  await page.clock.runFor(1000)
  await expect(page.getByText('✕ Utslagen', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Du är utslagen' })).toBeVisible()
  await expect(page.getByText('Alice vann!', { exact: true })).toBeVisible()
  await expect(page.getByTestId('player-vehicle')).toHaveCount(1)
  await expect(page.getByRole('button', { name: 'Starta nästa runda' })).not.toBeVisible()
  await page.getByRole('button', { name: 'Spela om', exact: true }).click()
  await expect(page.getByText('Alice vann!', { exact: true })).not.toBeVisible()
  await page.clock.runFor(1000)
  await expect(page.getByText('Alice vann!', { exact: true })).toBeVisible()
})
