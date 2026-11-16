import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { createAudioController, cueForEvent } from '../frontend/src/presentation/audioController.ts'
import { audioCues } from '../frontend/src/presentation/audioCues.ts'
import { createHowlerAudio } from '../frontend/src/presentation/howlerAudio.ts'

function policyFixture() {
  let time=0,ready=true
  const calls=[],active=new Set()
  const controller=createAudioController({ready:()=>ready,play(cue,volume,ended){
    const voice={cue,volume,stop(){active.delete(voice)},setVolume(value){voice.volume=value},end(){active.delete(voice);ended()}}
    calls.push(voice);active.add(voice);return voice
  }},()=>time)
  return {controller,calls,active,advance(ms){time+=ms},ready(value){ready=value}}
}

test('all authoritative event types map to the intended cue, without inventing extra hits or captures',()=>{
  const expected={MOVE:'move',CONVEYOR_MOVE:'move',TURN:'turn',ROTATOR_TURN:'turn',RAM:'impact',CONVEYOR_RAM:'impact',
    MOVE_BLOCKED:'blocked',PUSH_BLOCKED:'blocked',WEAPON_FIRED:'laser',WEAPON_HIT:'hit',CRASH:'crash',CONVEYOR_CRASH:'crash',
    VEHICLE_RESPAWNED:'respawn',SHIELD_ACTIVATED:'shield',PUSH:null,CONVEYOR_PUSH:null}
  for(const [type,cue] of Object.entries(expected)) expect(cueForEvent({type})).toBe(cue)
  for(const type of ['TURN','RAM','PUSH_BLOCKED']) expect(cueForEvent({type,actionType:'LASER'})).toBe(null)
  expect(cueForEvent({type:'SCORE_CHANGED',scoreReason:'CHECKPOINT'})).toBe('capture')
  for(const reason of ['CONTROL_POINT','CRASH_PENALTY','PUSH_CRASH']) expect(cueForEvent({type:'SCORE_CHANGED',scoreReason:reason})).toBe(null)
})

test('master volume updates active voices proportionally; mute and zero stop them without replay',()=>{
  const f=policyFixture()
  f.controller.play('move','move');f.controller.play('impact','hit',false)
  const original=f.calls.map(voice=>voice.volume)
  f.controller.configure({muted:false,volume:1})
  expect(f.active.size).toBe(2)
  f.calls.forEach((voice,index)=>expect(voice.volume).toBeCloseTo(original[index]*2))
  f.controller.configure({muted:true,volume:1});expect(f.active.size).toBe(0)
  f.controller.play('laser','muted-shot')
  f.controller.configure({muted:false,volume:1});f.controller.play('laser','muted-shot')
  expect(f.calls).toHaveLength(2)
  f.controller.play('laser','new-shot');f.controller.configure({muted:false,volume:0})
  expect(f.active.size).toBe(0)
})

test('motor voices are capped at two and local movement replaces remote movement',()=>{
  const f=policyFixture()
  f.controller.play('move','remote-1',false);f.controller.play('turn','remote-2',false)
  f.advance(100);f.controller.play('move','remote-3',false)
  expect(f.active.size).toBe(2)
  f.controller.play('move','local-1')
  expect(f.active.size).toBe(2)
  expect(f.calls.at(-1).volume).toBe(audioCues.move.gain*.5)
})

test('remote motor and impact cooldowns consume suppressed event IDs',()=>{
  const f=policyFixture()
  f.controller.play('impact','one',false);f.advance(79);f.controller.play('impact','suppressed',false)
  expect(f.calls).toHaveLength(1)
  f.advance(1);f.controller.play('impact','two',false);f.advance(100);f.controller.play('impact','suppressed',false)
  expect(f.calls).toHaveLength(2)
  f.controller.play('impact','local')
  expect(f.calls).toHaveLength(3)
})

test('movement coalesces per vehicle, cue and tick but a future tick can sound again',()=>{
  const f=policyFixture(),move={type:'MOVE',vehicleId:'a',playerId:'local',sourcePlayerId:'local'}
  f.controller.event(move,'game:1:playback:1','local')
  f.controller.event({...move,type:'CONVEYOR_MOVE'},'game:1:playback:2','local')
  expect(f.calls).toHaveLength(1)
  f.calls[0].end()
  f.controller.event(move,'game:2:playback:1','local')
  expect(f.calls).toHaveLength(2)
})

test('eight-voice limit protects local capture and victory over remote impacts',()=>{
  const f=policyFixture()
  for(let i=0;i<8;i++){f.controller.play('impact',`hit:${i}`,false);f.advance(80)}
  expect(f.active.size).toBe(8)
  f.controller.play('capture','capture');f.controller.play('victory','victory')
  expect(f.active.size).toBe(8)
  expect([...f.active].filter(voice=>voice.cue==='victory'||voice.cue==='capture')).toHaveLength(2)
})

test('immediate errors, locked cues and unavailable clips do not leak voices or replay',()=>{
  let calls=0,ready=false
  const controller=createAudioController({ready:()=>ready,play(cue,volume,ended){calls++;ended();return {stop(){},setVolume(){}}}})
  controller.play('submit','locked');ready=true;controller.play('submit','locked')
  expect(calls).toBe(0)
  for(let i=0;i<20;i++)controller.play('crash',`crash:${i}`)
  expect(calls).toBe(20)
  controller.configure({muted:true,volume:.5})
})

function browserFixture({webAudio=true,loaded=true}={}) {
  let time=0,nextTimer=0,visible=true
  const timers=new Map(),sounds=[]
  const context={state:'suspended',resume:async()=>{context.state='running'}}
  const create=options=>{
    const listeners=[]
    const sound={options,status:loaded?'loaded':'loading',plays:[],volumes:[],stops:[],unloads:0,
      state(){return sound.status},
      play(){const id=sound.plays.length+1;sound.plays.push(id);return id},
      volume(value,id){sound.volumes.push({value,id});return sound},
      stop(id){sound.stops.push(id);return sound},
      once(event,fn,id){listeners.push({event,fn,id});return sound},
      off(event,fn,id){for(let i=listeners.length-1;i>=0;i--)if(listeners[i].event===event&&listeners[i].fn===fn&&listeners[i].id===id)listeners.splice(i,1);return sound},
      unload(){sound.unloads++;sound.status='unloaded';listeners.splice(0);return null},
      emit(event,id){for(const listener of [...listeners])if(listener.event===event&&(listener.id==null||listener.id===id)){sound.off(event,listener.fn,listener.id);listener.fn(id)}},
      get listeners(){return listeners.length},
    }
    sounds.push(sound);return sound
  }
  const adapter=createHowlerAudio({create,usingWebAudio:()=>webAudio,context:()=>webAudio?context:null,visible:()=>visible},
    {set(fn,ms){const id=++nextTimer;timers.set(id,{at:time+ms,fn});return id},clear(id){timers.delete(id)}})
  return {adapter,sounds,context,timers,sound(cue){return sounds.filter(sound=>sound.options.src[0]===`/audio/${audioCues[cue].file}`).at(-1)},
    visible(value){visible=value},advance(ms){const until=time+ms;while(true){const next=[...timers].sort((a,b)=>a[1].at-b[1].at)[0];if(!next||next[1].at>until)break;time=next[1].at;timers.delete(next[0]);next[1].fn()}time=until}}
}

test('preloading never plays and unlock readiness follows the actual context',async()=>{
  const f=browserFixture()
  f.adapter.prepare();expect(f.sounds).toHaveLength(Object.keys(audioCues).length)
  expect(f.sounds.every(sound=>sound.plays.length===0&&sound.options.volume===0&&sound.options.autoplay===false&&sound.options.loop===false)).toBe(true)
  let resume
  f.context.resume=()=>new Promise(resolve=>{resume=()=>{f.context.state='running';resolve()}})
  f.adapter.unlock();expect(f.adapter.sink.ready('laser')).toBe(false)
  expect(f.adapter.sink.play('laser',.5,()=>{})).toBe(null)
  resume();await Promise.resolve()
  expect(f.adapter.sink.ready('laser')).toBe(true)
  f.adapter.dispose()
})

test('HTML5 fallback primes each loaded element silently within a gesture',()=>{
  const f=browserFixture({webAudio:false})
  f.adapter.prepare();expect(f.adapter.sink.ready('laser')).toBe(false)
  f.adapter.unlock()
  expect(f.sounds.every(sound=>sound.plays.length===1&&sound.options.volume===0)).toBe(true)
  expect(f.adapter.sink.ready('laser')).toBe(false)
  f.sound('laser').emit('play',1)
  expect(f.sound('laser').stops).toEqual([1])
  expect(f.adapter.sink.ready('laser')).toBe(true)
  f.adapter.sink.play('laser',.3,()=>{});f.sound('laser').emit('play',2)
  expect(f.adapter.sink.ready('laser')).toBe(false)
  expect(f.adapter.sink.play('laser',.3,()=>{})).toBe(null)
  f.sound('laser').emit('end',2);expect(f.adapter.sink.ready('laser')).toBe(true)
  f.adapter.dispose();expect(f.timers.size).toBe(0)
})

test('loading clips and hidden tabs do not play or release backlog after becoming ready',()=>{
  const f=browserFixture({loaded:false})
  f.adapter.unlock()
  expect(f.adapter.sink.play('laser',.5,()=>{})).toBe(null)
  f.sound('laser').status='loaded';f.visible(false)
  expect(f.adapter.sink.play('laser',.5,()=>{})).toBe(null)
  f.visible(true);expect(f.sound('laser').plays).toHaveLength(0)
  f.adapter.dispose()
})

test('playback failure unloads the cue, cleans listeners, and retries only on a future gesture',()=>{
  const f=browserFixture();let ended=0
  f.adapter.unlock()
  const sound=f.sound('laser')
  f.adapter.sink.play('laser',.5,()=>ended++)
  sound.emit('playerror',1)
  expect(ended).toBe(1);expect(sound.unloads).toBe(1);expect(sound.listeners).toBe(0)
  expect(f.adapter.sink.ready('laser')).toBe(false)
  f.adapter.unlock();expect(f.sound('laser')).not.toBe(sound)
  expect(f.sound('laser').plays).toHaveLength(0)
  f.adapter.dispose();expect(f.timers.size).toBe(0)
})

test('a pending start is unloaded after 150 ms and cannot play after a later unlock',()=>{
  const f=browserFixture();let ended=0
  f.adapter.unlock();const sound=f.sound('hit')
  f.adapter.sink.play('hit',.3,()=>ended++)
  f.advance(151)
  expect(ended).toBe(1);expect(sound.unloads).toBe(1);expect(sound.listeners).toBe(0)
  sound.emit('play',1);expect(f.adapter.sink.ready('hit')).toBe(false)
  f.adapter.dispose();expect(f.timers.size).toBe(0)
})

test('active voices adjust volume and end/dispose clean up all playback resources',()=>{
  const f=browserFixture();let ended=0
  f.adapter.unlock();const sound=f.sound('victory')
  const voice=f.adapter.sink.play('victory',.2,()=>ended++)
  sound.emit('play',1);voice.setVolume(.4)
  expect(sound.volumes.at(-1)).toEqual({value:.4,id:1})
  sound.emit('end',1);expect(ended).toBe(1);expect(sound.listeners).toBe(0);expect(f.timers.size).toBe(0)
  f.adapter.sink.play('victory',.2,()=>ended++);sound.emit('play',2)
  f.adapter.dispose();expect(ended).toBe(2);expect(f.sounds.every(sound=>sound.unloads>=1)).toBe(true)
  expect(f.timers.size).toBe(0)
})

test('load errors and rejected resume are harmless and subsequent gestures can recover',async()=>{
  const f=browserFixture();f.context.resume=()=>Promise.reject(new Error('denied'))
  f.adapter.unlock();await Promise.resolve()
  expect(f.adapter.sink.ready('move')).toBe(false)
  f.sound('move').options.onloaderror()
  f.context.resume=async()=>{f.context.state='running'}
  f.adapter.unlock();await Promise.resolve()
  expect(f.adapter.sink.ready('move')).toBe(true)
  f.adapter.dispose()
})

test('bundled cue durations, peaks, hashes and license manifest match the playback definitions',async()=>{
  const root=new URL('../frontend/public/audio/',import.meta.url)
  const manifest=JSON.parse(await readFile(new URL('manifest.json',root),'utf8'))
  expect(manifest.license).toBe('CC0-1.0')
  expect(manifest.files.map(file=>file.file).sort()).toEqual(Object.values(audioCues).map(cue=>cue.file).sort())
  for(const cue of Object.values(audioCues)){
    const bytes=await readFile(new URL(cue.file,root))
    expect(bytes.subarray(0,4).toString()).toBe('RIFF');expect(bytes.subarray(8,12).toString()).toBe('WAVE')
    const duration=bytes.readUInt32LE(40)/bytes.readUInt32LE(28)*1000
    expect(duration).toBeCloseTo(cue.durationMs,0)
    expect(duration).toBeLessThanOrEqual(700);expect(cue.gain).toBeGreaterThan(0);expect(cue.gain).toBeLessThanOrEqual(1)
    let peak=0
    for(let i=44;i<bytes.length;i+=2)peak=Math.max(peak,Math.abs(bytes.readInt16LE(i)))
    expect(peak/32767).toBeLessThanOrEqual(.701)
    expect(peak).toBeGreaterThan(0)
    expect(manifest.files.find(file=>file.file===cue.file).sha256).toBe(createHash('sha256').update(bytes).digest('hex'))
  }
  expect(await readFile(new URL('LICENSE.txt',root),'utf8')).toContain('CC0')
})

test('cancelling an old tick preserves an acknowledged local submission and current tick voices',()=>{
  const f=policyFixture()
  f.controller.play('impact','game:1:playback:1')
  f.controller.play('submit','game:submit:request-id')
  f.controller.play('turn','game:2:playback:1')
  f.controller.stop('game:1:')
  expect([...f.active].map(voice=>voice.cue)).toEqual(['submit','turn'])
  f.controller.stop();expect(f.active.size).toBe(0)
})

test('old resume rejections cannot relock a newer successful gesture or a new session',async()=>{
  const f=browserFixture();let reject
  f.context.resume=()=>new Promise((resolve,rejectResume)=>{reject=rejectResume})
  f.adapter.unlock()
  f.context.state='running';f.adapter.unlock()
  reject(new Error('old failure'));await Promise.resolve()
  expect(f.adapter.sink.ready('tick')).toBe(true)
  f.context.state='suspended';f.adapter.unlock()
  f.adapter.dispose();f.context.state='running';f.adapter.prepare();f.adapter.unlock()
  reject(new Error('disposed session'));await Promise.resolve()
  expect(f.adapter.sink.ready('tick')).toBe(true)
  f.adapter.dispose()
})
