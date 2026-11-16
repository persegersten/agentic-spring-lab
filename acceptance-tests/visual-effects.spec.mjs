import { expect, test } from '@playwright/test'
import { describeEffect, planEffects, planShakes, effectDurations } from '../frontend/src/presentation/visualEffects.ts'

const board={checkpoints:[{id:'CP1',position:{x:3,y:4}}]}
const item=(type,id='game:2:playback:1',at=0,extra={})=>({id,at,until:at+650,stream:'playback',event:{type,playerId:'local',vehicleId:'robot',oldPosition:{x:1,y:2},newPosition:{x:6,y:2},actionType:'LASER',...extra}})

test('effects preserve authoritative endpoints, capture reasons and short lifetimes',()=>{
  for(const [type,kind] of [['WEAPON_FIRED','beam'],['WEAPON_HIT','hit'],['RAM','collision'],['PUSH_BLOCKED','blocked'],['CRASH','crash'],['VEHICLE_RESPAWNED','respawn']]){
    const effect=describeEffect(item(type),board,'local')
    expect(effect.kind).toBe(kind)
    expect(effect.until).toBe(effectDurations[kind])
    expect(effect.anchor).toEqual(kind==='respawn'?{x:6,y:2}:{x:1,y:2})
  }
  expect(describeEffect(item('WEAPON_FIRED'),board,'local').fragments).toEqual([])
  expect(describeEffect(item('SCORE_CHANGED','a',0,{scoreReason:'CONTROL_POINT'}),board,'local')).toBeNull()
  expect(describeEffect(item('SCORE_CHANGED','a',0,{scoreReason:'CHECKPOINT',checkpointId:'missing'}),board,'local')).toBeNull()
  expect(describeEffect(item('SCORE_CHANGED','a',0,{scoreReason:'CHECKPOINT',checkpointId:'CP1'}),board,'local').anchor).toEqual({x:3,y:4})
  const shortened={...item('CRASH'),until:100}
  expect(describeEffect(shortened,board,'local').lifetime).toBe(100)
})

test('fragment geometry is deterministic and uses full namespaced event identity',()=>{
  const a=describeEffect(item('RAM'),board,'local')
  expect(describeEffect(item('RAM'),board,'local').fragments).toEqual(a.fragments)
  expect(describeEffect(item('RAM','game:3:playback:1'),board,'local').fragments).not.toEqual(a.fragments)
  expect(a.fragments).toHaveLength(6)
  expect(a.fragments.filter(f=>f.plate)).toHaveLength(3)
})

test('effect admission bounds simultaneous groups and fragments and never backfills drops',()=>{
  const events=Array.from({length:30},(_,i)=>item('RAM',String(i),0,{playerId:'other'}))
  const plan=planEffects({events},board,'local')
  expect(plan).toHaveLength(8)
  expect(plan.flatMap(e=>e.fragments)).toHaveLength(48)
  const important=item('CRASH','important',100)
  const preempted=planEffects({events:[...events,important]},board,'local')
  expect(preempted.find(e=>e.id==='important')).toBeTruthy()
  expect(preempted.filter(e=>e.until===100)).toHaveLength(1)
  for(let time=0;time<700;time+=10){
    expect(preempted.filter(e=>e.at<=time&&e.until>time).length).toBeLessThanOrEqual(8)
  }
  expect(preempted.map(e=>e.id).filter(id=>id!=='important')).toEqual(plan.map(e=>e.id))
})

test('shake cues only mark fresh significant local impacts without stacking',()=>{
  const events=[item('RAM','remote',0,{playerId:'remote',sourcePlayerId:'local'}),item('PUSH_BLOCKED','blocked',0),item('RAM','first',0),item('CRASH','suppressed',80),item('CONVEYOR_CRASH','next',120)]
  expect(planShakes({events},'local')).toEqual([{id:'first',at:0,until:120},{id:'next',at:120,until:240}])
})
