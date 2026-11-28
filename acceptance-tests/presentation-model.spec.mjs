import { expect, test } from '@playwright/test'
import { createTimeline, shouldAnimate } from '../frontend/src/presentation/timeline.ts'
import { createAudioController, cueForEvent } from '../frontend/src/presentation/audioController.ts'
import { currentFlagTransfers } from '../frontend/src/presentation/flagTransfers.ts'

const event = (type, sequence = 1, extra = {}) => ({ type, sequence, playerId:'local', sourcePlayerId:'other', vehicleId:'robot', ...extra })
const snapshot = (tick = 1, gameId = 'game') => ({ id:gameId, continuous:{ tick, intervalMillis:2000 }, round:{ state:{
  startEvents:[event('VEHICLE_RESPAWNED')], playback:[event('MOVE'), event('WEAPON_FIRED',2)],
} } })

test('timeline namespaces respawn and playback IDs and stays inside the tick budget', () => {
  const schedule = createTimeline(snapshot())
  expect(new Set(schedule.events.map(item => item.id)).size).toBe(3)
  expect(schedule.events.map(item => item.stream)).toEqual(['start','playback','playback'])
  expect(schedule.events.map(item => item.at)).toEqual([0,0,450])
  expect(schedule.duration).toBeLessThan(2000)
  expect(createTimeline(snapshot(2)).events[0].id).not.toBe(schedule.events[0].id)
})

test('only visible consecutive ticks animate', () => {
  const previous = snapshot(2)
  expect(shouldAnimate(previous,snapshot(3),true)).toBe(true)
  for (const [next, visible] of [[snapshot(2),true],[snapshot(4),true],[snapshot(1),true],[snapshot(3,'different'),true],[snapshot(3),false]]) {
    expect(shouldAnimate(previous,next,visible)).toBe(false)
  }
})

test('transfer highlights share their playback boundary and expire without reviving older transitions', () => {
  const view=snapshot()
  view.round.state.playback=[event('WEAPON_HIT'),event('FLAG_TRANSFERRED',2,{checkpointId:'CP1'}),
    event('FLAG_TRANSFERRED',3,{checkpointId:'CP2'}),event('MOVE',4)]
  const schedule=createTimeline(view)
  const transfers=schedule.events.filter(item=>item.event.type==='FLAG_TRANSFERRED')
  expect(transfers.map(item=>item.at)).toEqual([225,225])
  expect(currentFlagTransfers(schedule,224)).toEqual([])
  expect(currentFlagTransfers(schedule,225)).toEqual(transfers)
  expect(currentFlagTransfers(schedule,825)).toEqual([])
  const newer={...transfers[0],id:'newer',at:300,until:400}
  const overlapping={...schedule,events:[...schedule.events,newer]}
  expect(currentFlagTransfers(overlapping,350).map(item=>item.id)).toEqual(['newer',transfers[1].id])
  expect(currentFlagTransfers(overlapping,450).map(item=>item.id)).toEqual([transfers[1].id])
})

function audioFixture() {
  let ready = true
  const calls = [], active = new Set()
  const controller = createAudioController({ ready:() => ready, play(cue, volume, ended) {
    const voice = {cue, volume, ended:() => { active.delete(voice); ended() }}
    calls.push(voice); active.add(voice)
    return {stop:() => active.delete(voice),setVolume:value => {voice.volume=value}}
  } })
  return { controller, calls, active, setReady(value) { ready = value } }
}

test('audio consumes muted and locked cues without replaying them after unlock', () => {
  const {controller,calls,setReady} = audioFixture()
  setReady(false); controller.play('tick','locked'); setReady(true); controller.play('tick','locked')
  controller.configure({muted:true,volume:.5}); controller.play('impact','muted')
  controller.configure({muted:false,volume:.5}); controller.play('impact','muted')
  controller.play('submit','request-id'); controller.play('submit','request-id')
  expect(calls.map(call => call.cue)).toEqual(['submit'])
})

test('voices remain bounded and important local feedback replaces remote motors', () => {
  const {controller,calls,active} = audioFixture()
  controller.play('move','move:0',false)
  controller.play('turn','turn:0',false)
  for (let index=0;index<6;index++) controller.play('impact',`impact:${index}`)
  expect(active.size).toBe(8)
  controller.play('crash','crash')
  expect(active.size).toBe(8)
  expect(calls.at(-1).cue).toBe('crash')
  calls.at(-1).ended()
  expect(active.size).toBe(7)
  controller.configure({muted:true,volume:.5})
  expect(active.size).toBe(0)
})

test('event cues preserve semantics and suppress secondary laser collisions', () => {
  expect(cueForEvent(event('SCORE_CHANGED',1,{scoreReason:'CONTROL_POINT'}))).toBe(null)
  expect(cueForEvent(event('SCORE_CHANGED',1,{scoreReason:'CHECKPOINT'}))).toBe('capture')
  expect(cueForEvent(event('PUSH_BLOCKED',1,{actionType:'LASER'}))).toBe(null)
  expect(cueForEvent(event('TURN',1,{actionType:'LASER'}))).toBe(null)
  expect(cueForEvent(event('WEAPON_HIT'))).toBe('hit')
  expect(cueForEvent(event('VEHICLE_RESPAWNED'))).toBe('respawn')
})

test('recovery accepts equal revisions as a presentation reset without replacing authoritative state', async () => {
  const { acceptSnapshot, initialSnapshotState } = await import('../frontend/src/presentation/snapshots.ts')
  const view = { ...snapshot(), continuous: { ...snapshot().continuous, revision: 5 } }
  let state = acceptSnapshot(initialSnapshotState, { type: 'snapshot', snapshot: view })
  expect(acceptSnapshot(state, { type: 'snapshot', snapshot: structuredClone(view) })).toBe(state)
  state = acceptSnapshot(state, { type: 'disconnected' })
  const stale = { ...view, continuous: { ...view.continuous, revision: 4 } }
  expect(acceptSnapshot(state, { type: 'snapshot', snapshot: stale })).toBe(state)
  const recovered = acceptSnapshot(state, { type: 'snapshot', snapshot: structuredClone(view) })
  expect(recovered.view).toBe(view)
  expect(recovered.recoveryEpoch).toBe(1)
  expect(recovered.disconnected).toBe(false)
  expect(acceptSnapshot(recovered, { type: 'snapshot', snapshot: view })).toBe(recovered)
})

test('recovery resets newer ticks once and game identity permits a new revision sequence', async () => {
  const { acceptSnapshot, initialSnapshotState } = await import('../frontend/src/presentation/snapshots.ts')
  const view = { ...snapshot(), continuous: { ...snapshot().continuous, revision: 5 } }
  let state = acceptSnapshot(initialSnapshotState, { type: 'snapshot', snapshot: view })
  state = acceptSnapshot(state, { type: 'disconnected' })
  const newer = { ...view, continuous: { ...view.continuous, revision: 6, tick: 2 } }
  state = acceptSnapshot(state, { type: 'snapshot', snapshot: newer })
  expect(state.view).toBe(newer)
  expect(state.recoveryEpoch).toBe(1)
  const different = { ...newer, id: 'new-game', continuous: { ...newer.continuous, revision: 1, tick: 0 } }
  state = acceptSnapshot(state, { type: 'snapshot', snapshot: different })
  expect(state.view).toBe(different)
  expect(state.recoveryEpoch).toBe(2)
})
