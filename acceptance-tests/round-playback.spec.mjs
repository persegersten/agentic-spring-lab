import { expect, test } from '@playwright/test'
import { fillProgram, joinGame, withPlayerPages } from './player-pages.mjs'

test('players receive and play the same server ordered event sequence', async ({ browser }) => {
  await withPlayerPages(browser, ['per', 'alice'], async ({ per, alice }) => {
    await per.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: 'http://127.0.0.1:5173' })
    await per.goto('/')
    await per.getByLabel('Max spelare', { exact: true }).fill('2')
    await per.getByRole('button', { name: 'Skapa spel', exact: true }).click()
    await expect(per.getByRole('heading', { name: 'Anslut till spelet' })).toBeVisible()
    const gameId = await per.getByLabel('Spel-id', { exact: true }).inputValue()
    await joinGame(per, 'Per')

    await alice.goto('/')
    await alice.getByLabel('Spel-id', { exact: true }).fill(gameId)
    await alice.getByRole('button', { name: 'Öppna spel', exact: true }).click()
    await joinGame(alice, 'Alice')

    await expect(per.getByRole('heading', { name: 'Planering', exact: true })).toBeVisible()
    for (const page of [per, alice]) {
      await fillProgram(page, ['TURN_LEFT', 'FORWARD_2', 'WAIT'])
    }

    for (const page of [per, alice]) {
      await expect(page.getByRole('heading', { name: 'Uppspelning', exact: true }).first()).toBeVisible()
      await expect(page.getByTestId('event-debug-view')).toBeVisible()
      await page.getByText('Eventsekvens (debug)', { exact: true }).click()
    }
    const perEvents = await per.getByTestId('round-event').allTextContents()
    const aliceEvents = await alice.getByTestId('round-event').allTextContents()
    expect(aliceEvents).toEqual(perEvents)
    expect(perEvents.some(event => event.includes('MOVE'))).toBe(true)
    const sequences = await per.getByTestId('round-event').evaluateAll(events =>
      events.map(event => Number(event.getAttribute('data-sequence'))))
    expect(sequences).toEqual(sequences.map((_, index) => index + 1))

    const displayedState = JSON.parse(await per.getByTestId('initial-game-state').textContent())
    expect(displayedState.board).toMatchObject({ width: 20, height: 20 })
    expect(displayedState.vehicles).toHaveLength(2)

    await per.getByRole('button', { name: 'Kopiera game-state', exact: true }).click()
    await expect(per.getByText('Game-state kopierat', { exact: true })).toBeVisible()
    const copiedState = await per.evaluate(() => navigator.clipboard.readText())
    expect(JSON.parse(copiedState)).toEqual(displayedState)
  })
})

test('playback stays paused and automatically starts the next round when finished', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000001'
  const playerId = '20000000-0000-0000-0000-000000000001'
  const vehicle = { id: 'vehicle-1', playerId, x: 0, y: 0, direction: 'EAST', status: 'ACTIVE' }
  const playback = Array.from({ length: 12 }, (_, index) => ({
    sequence: index + 1, type: 'MOVE', playerId, vehicleId: vehicle.id,
    sourcePlayerId: playerId, sourceVehicleId: vehicle.id,
    oldPosition: { x: index, y: 0 }, newPosition: { x: index + 1, y: 0 },
    oldDirection: 'EAST', newDirection: 'EAST',
  }))
  let polls = 0
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
    configuration: { maxPlayers: 1, programSize: 3, planningTimeoutSeconds: 120, joinTimeoutSeconds: 300 },
    players: [{ id: playerId, name: 'Per' }],
    board: { width: 20, height: 20, walls: [], pits: [] },
    vehicles: [{ ...vehicle, x: 12 }],
    round: { state: { number: 1, phase: 'PLAYBACK', ready: { [playerId]: true }, initiative: [playerId], initialVehicles: [vehicle], playback }, program: [] },
  }
  const nextState = {
    ...state,
    round: { state: { number: 2, phase: 'PLANNING', planningDeadline: '2026-01-01T00:02:00Z', ready: { [playerId]: false }, initiative: [playerId], initialVehicles: state.vehicles, initialScores: { [playerId]: 0 }, startEvents: [], playback: [] }, program: [] },
  }
  let currentState = state
  let starts = 0
  await page.clock.install()
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => {
    polls++
    return route.fulfill({ json: currentState })
  })
  await page.route(`**/games/${gameId}/rounds?completedRound=1`, route => {
    starts++
    currentState = nextState
    return route.fulfill({ json: nextState })
  })
  await page.goto(`/game/${gameId}`)
  await expect(page.getByRole('button', { name: 'Pausa', exact: true })).toBeVisible()
  await expect(page.getByTestId('initial-game-state')).not.toBeVisible()
  await page.clock.runFor(1000)
  await page.getByRole('button', { name: 'Pausa', exact: true }).click()
  const pausedSequence = await page.getByTestId('current-playback-event').getAttribute('data-sequence')
  await page.clock.runFor(3000)
  await expect.poll(() => polls).toBeGreaterThanOrEqual(3)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-sequence', pausedSequence)
  await page.getByRole('button', { name: 'Fortsätt', exact: true }).click()
  await page.clock.runFor(3000)
  await expect(page.getByRole('button', { name: 'Starta nästa runda' })).not.toBeVisible()
  await expect(page.getByTestId('round-number')).toHaveText('Round 2')
  await expect(page.getByTestId('round-start-dialog')).toContainText('Runda 2 startar')
  expect(starts).toBe(1)
  await page.clock.runFor(1800)
  await expect(page.getByTestId('round-start-dialog')).not.toBeVisible()
})

test('playback removes a vehicle exactly when its crash event is reached', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000002'
  const playerId = '20000000-0000-0000-0000-000000000002'
  const vehicle = { id: 'vehicle-crash', playerId, x: 0, y: 0, direction: 'EAST', status: 'ACTIVE' }
  const playback = [
    {
      sequence: 1, type: 'MOVE', playerId, vehicleId: vehicle.id,
      sourcePlayerId: playerId, sourceVehicleId: vehicle.id,
      oldPosition: { x: 0, y: 0 }, newPosition: { x: 1, y: 0 },
      oldDirection: 'EAST', newDirection: 'EAST',
    },
    {
      sequence: 2, type: 'CRASH', playerId, vehicleId: vehicle.id,
      sourcePlayerId: playerId, sourceVehicleId: vehicle.id,
      oldPosition: { x: 1, y: 0 }, newPosition: { x: 2, y: 0 },
      oldDirection: 'EAST', newDirection: 'EAST',
    },
  ]
  const state = {
    id: gameId, playerId, status: 'RUNNING', roundLimit: 7,
    configuration: { maxPlayers: 1, programSize: 3, planningTimeoutSeconds: 120, joinTimeoutSeconds: 300 },
    players: [{ id: playerId, name: 'Per' }],
    board: { width: 2, height: 2, walls: [], pits: [] },
    vehicles: [{ ...vehicle, x: 2, status: 'CRASHED' }],
    round: { state: { number: 1, phase: 'PLAYBACK', ready: { [playerId]: true }, initiative: [playerId], initialVehicles: [vehicle], playback }, program: [] },
  }
  await page.clock.install()
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))

  await page.goto(`/game/${gameId}`)
  await page.clock.runFor(1)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'MOVE')
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x', '1')
  await page.clock.runFor(250)
  await expect(page.getByTestId('current-playback-event')).toHaveAttribute('data-event-type', 'CRASH')
  await expect(page.getByTestId('player-vehicle')).toHaveCount(0)

})

test('finishing the last round does not request another round', async ({ page }) => {
  const gameId = '10000000-0000-0000-0000-000000000009'
  const playerId = '20000000-0000-0000-0000-000000000009'
  const vehicle = { id: 'vehicle-final', playerId, x: 0, y: 0, direction: 'NORTH', status: 'ACTIVE' }
  const state = {
    id: gameId, playerId, status: 'FINISHED', roundLimit: 1,
    configuration: { maxPlayers: 1, programSize: 1, planningTimeoutSeconds: 30, joinTimeoutSeconds: 30 },
    players: [{ id: playerId, name: 'Per', score: 0, visitedCheckpoints: [], crashes: 0 }],
    board: { width: 2, height: 2, walls: [], pits: [], checkpoints: [], spawnPoints: [], conveyors: [], rotators: [], controlPoints: [] },
    vehicles: [vehicle], placements: [{ playerId, placement: 1, score: 0, checkpointsVisited: 0, crashes: 0, winner: true }],
    round: { state: { number: 1, phase: 'PLAYBACK', ready: { [playerId]: true }, initiative: [playerId], initialVehicles: [vehicle], initialScores: { [playerId]: 0 }, startEvents: [], playback: [] }, program: [] },
  }
  let starts = 0
  await page.clock.install()
  await page.addInitScript(session => localStorage.setItem(`wreckage-session:${session.gameId}`, JSON.stringify(session)),
    { gameId, playerId, token: 'test-token' })
  await page.route(`**/games/${gameId}/players/${playerId}`, route => route.fulfill({ json: state }))
  await page.route(`**/games/${gameId}/rounds?*`, route => { starts++; return route.abort() })

  await page.goto(`/game/${gameId}`)
  await page.clock.runFor(100)

  await expect(page.getByTestId('finished-game')).toBeVisible()
  await expect(page.getByTestId('round-start-dialog')).not.toBeVisible()
  expect(starts).toBe(0)
})

test('playback visibly applies a conveyor event',async({page})=>{const gameId='10000000-0000-0000-0000-000000000007',playerId='20000000-0000-0000-0000-000000000007',vehicle={id:'v',playerId,x:0,y:0,direction:'EAST',status:'ACTIVE'};const playback=[{sequence:1,type:'CONVEYOR_MOVE',playerId,vehicleId:'v',sourcePlayerId:playerId,sourceVehicleId:'v',oldPosition:{x:0,y:0},newPosition:{x:1,y:0},oldDirection:'EAST',newDirection:'EAST'}];const state={id:gameId,playerId,status:'RUNNING',configuration:{maxPlayers:1,programSize:1,planningTimeoutSeconds:30,joinTimeoutSeconds:30},players:[{id:playerId,name:'Per'}],board:{width:3,height:2,walls:[],pits:[],checkpoints:[],spawnPoints:[],conveyors:[{position:{x:0,y:0},direction:'EAST'}],rotators:[]},vehicles:[{...vehicle,x:1}],round:{state:{number:1,phase:'PLAYBACK',ready:{[playerId]:true},initiative:[playerId],initialVehicles:[vehicle],playback},program:[]}};await page.clock.install();await page.addInitScript(s=>localStorage.setItem(`wreckage-session:${s.gameId}`,JSON.stringify(s)),{gameId,playerId,token:'t'});await page.route(`**/games/${gameId}/players/${playerId}`,r=>r.fulfill({json:state}));await page.goto(`/game/${gameId}`);await expect(page.getByTestId('board-conveyor')).toHaveAttribute('data-direction','EAST');await page.clock.runFor(1);await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','1')})
