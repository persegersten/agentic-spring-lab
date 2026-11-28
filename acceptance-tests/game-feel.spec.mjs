import { expect, test } from '@playwright/test'

async function setup(page, count = 2, queue = [null,null,null,null,null], checkpoints = [{id:'CP1',order:1,position:{x:2,y:1}}]) {
  const gameId='10000000-0000-0000-0000-000000000007', playerId='local'
  const vehicle = {id:'robot',playerId,x:1,y:1,direction:'NORTH',status:'ACTIVE'}
  const state={id:gameId,playerId,status:'RUNNING',players:Array.from({length:count},(_,i)=>({id:i ? `player-${i}`:playerId,name:`Robot ${i+1}`,capturedCheckpoints:0,visitedCheckpoints:[]})),
    board:{width:10,height:10,walls:[],pits:[],obstacles:[],checkpoints,spawnPoints:[],conveyors:[],rotators:[],controlPoints:[]},
    vehicles:[vehicle],placements:[],round:null,shieldAvailable:true,
    continuous:{revision:1,tick:1,serverTime:new Date().toISOString(),nextTick:new Date(Date.now()+2000).toISOString(),intervalMillis:2000,queue,lastExecutedCommand:'WAIT',activeShieldPlayerIds:[],shieldTicksRemaining:0}}
  let response=state
  let disconnected=false, failures=0
  await page.addInitScript(({gameId,playerId})=>localStorage.setItem(`wreckage-session:${gameId}`,JSON.stringify({gameId,playerId,token:'test'})),{gameId,playerId})
  await page.route(`**/games/${gameId}/players/${playerId}`,route=>{if(disconnected){failures++;return route.abort('failed')}return route.fulfill({json:response})})
  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('player-vehicle')).toBeVisible()
  const next=(events, startEvents=[])=>{
    const initial=structuredClone(state.vehicles)
    state.continuous.tick++;state.continuous.revision++
    state.round={state:{number:state.continuous.tick,initialVehicles:initial,startEvents,playback:events}}
  }
  const event=(type,sequence=1,extra={})=>({type,sequence,playerId,vehicleId:'robot',sourcePlayerId:playerId,sourceVehicleId:'robot',oldPosition:{x:1,y:1},newPosition:{x:2,y:1},oldDirection:'NORTH',newDirection:'NORTH',...extra})
  return {state,next,event,respond(value){response=value},disconnect(){disconnected=true},resume(){disconnected=false},get failures(){return failures}}
}

test('stable robots, respawn markers and same-tick updates do not restart playback', async ({page})=>{
  const game=await setup(page)
  await page.getByTestId('player-vehicle').evaluate(el=>{window.originalRobot=el})
  game.next([game.event('MOVE')],[game.event('VEHICLE_RESPAWNED')])
  game.state.vehicles[0].x=2
  await expect(page.locator('[data-effect="respawn"]')).toBeVisible()
  expect(await page.getByTestId('player-vehicle').evaluate(el=>el===window.originalRobot)).toBe(true)
  await expect(page.getByTestId('board-effect')).toHaveCount(0)
  game.state.continuous.activeShieldPlayerIds=['local'];game.state.continuous.revision++
  await expect(page.getByTestId('player-vehicle')).toHaveClass(/shield-effect/)
  await expect(page.getByTestId('board-effect')).toHaveCount(0)
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','2')
  await page.reload()
  await expect(page.getByTestId('board-effect')).toHaveCount(0)
})

test('stale snapshots and skipped ticks never replay effects',async({page})=>{
  const game=await setup(page)
  const stale=structuredClone(game.state)
  game.state.continuous.tick+=3
  game.next([game.event('CRASH')])
  game.state.vehicles[0].x=7
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','7')
  game.respond(stale)
  await page.waitForTimeout(600)
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','7')
  await expect(page.getByTestId('board-effect')).toHaveCount(0)
})

test('audio preferences survive reload and malformed storage is harmless',async({page})=>{
  await page.addInitScript(()=>{if(!localStorage.getItem('audio-fixture')){localStorage.setItem('wreckage-audio','broken-json');localStorage.setItem('audio-fixture','1')}})
  await setup(page)
  await expect(page.getByRole('slider',{name:'Ljudvolym'})).toHaveValue('50')
  await page.getByRole('button',{name:'Stäng av ljud'}).click()
  await page.getByRole('slider',{name:'Ljudvolym'}).fill('23')
  await expect.poll(()=>page.evaluate(()=>JSON.parse(localStorage.getItem('wreckage-audio')))).toEqual({muted:true,volume:.23})
  await page.reload()
  await expect(page.getByRole('button',{name:'Slå på ljud'})).toHaveAttribute('aria-pressed','true')
  await expect(page.getByRole('slider',{name:'Ljudvolym'})).toHaveValue('23')
})

test('audio settings restore and loaded audio files are valid',async({page})=>{
  await page.addInitScript(()=>localStorage.setItem('wreckage-audio',JSON.stringify({muted:true,volume:.23})))
  await setup(page)
  await expect(page.getByRole('button',{name:'Slå på ljud'})).toHaveAttribute('aria-pressed','true')
  await expect(page.getByRole('slider',{name:'Ljudvolym'})).toHaveValue('23')
  await page.getByRole('button',{name:'Slå på ljud'}).click()
  const bytes=await page.evaluate(async()=>Array.from(new Uint8Array(await (await fetch('/audio/impact.wav')).arrayBuffer()).slice(0,4)))
  expect(bytes).toEqual([82,73,70,70])
})

test('dense multiplayer effects stay bounded and clean up',async({page},testInfo)=>{
  const game=await setup(page,10)
  game.state.vehicles=Array.from({length:10},(_,index)=>({id:index ? `robot-${index}`:'robot',playerId:index ? `player-${index}`:'local',x:index,y:1,direction:'NORTH',status:'ACTIVE'}))
  game.state.continuous.revision++
  await expect(page.getByTestId('player-vehicle')).toHaveCount(10)
  await page.evaluate(()=>{
    window.presentationProfile={frames:[],longTasks:[],started:performance.now()}
    const sample=now=>{const profile=window.presentationProfile;if(profile.last && profile.frames.length<600)profile.frames.push(now-profile.last);profile.last=now;profile.raf=requestAnimationFrame(sample)}
    window.presentationProfile.raf=requestAnimationFrame(sample)
    if(PerformanceObserver.supportedEntryTypes.includes('longtask')){
      window.presentationProfile.observer=new PerformanceObserver(list=>window.presentationProfile.longTasks.push(...list.getEntries().map(entry=>entry.duration)))
      window.presentationProfile.observer.observe({entryTypes:['longtask']})
    }
  })
  for(let tick=0;tick<3;tick++){
    const events=Array.from({length:40},(_,index)=>{
      const robot=game.state.vehicles[index%10]
      return game.event(['WEAPON_FIRED','WEAPON_HIT','RAM','PUSH_BLOCKED'][index%4],index+1,{
        vehicleId:robot.id,playerId:robot.playerId,sourceVehicleId:robot.id,sourcePlayerId:robot.playerId,
        oldPosition:{x:robot.x,y:robot.y},newPosition:{x:robot.x,y:4},...(index%4<2 ? {actionType:'LASER'}:{})})
    })
    game.next(events)
    await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick',String(game.state.continuous.tick))
    await page.waitForTimeout(400)
    expect(await page.getByTestId('board-effect').count()).toBeLessThanOrEqual(8)
    expect(await page.locator('.effect-fragment').count()).toBeLessThanOrEqual(48)
    await expect(page.getByTestId('board-effect')).toHaveCount(0)
  }
  const profile=await page.evaluate(()=>{
    const profile=window.presentationProfile
    cancelAnimationFrame(profile.raf);profile.observer?.disconnect()
    const sorted=profile.frames.toSorted((a,b)=>a-b)
    return {userAgent:navigator.userAgent,logicalProcessors:navigator.hardwareConcurrency,durationMs:performance.now()-profile.started,
      frameCount:sorted.length,medianFrameMs:sorted[Math.floor(sorted.length*.5)],p95FrameMs:sorted[Math.floor(sorted.length*.95)],longTasksMs:profile.longTasks}
  })
  await testInfo.attach('presentation-performance.json',{body:JSON.stringify(profile,null,2),contentType:'application/json'})
  await page.screenshot({path:'/tmp/wreckage-polish-desktop.png'})
})

test('hidden tabs establish a silent baseline and resume only future ticks',async({page})=>{
  const game=await setup(page)
  await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});document.dispatchEvent(new Event('visibilitychange'))})
  game.next([game.event('WEAPON_FIRED',1,{actionType:'LASER'})])
  game.state.vehicles[0].x=4
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','4')
  await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>false});document.dispatchEvent(new Event('visibilitychange'))})
  await expect(page.getByTestId('laser-shot')).toHaveCount(0)
  await expect(page.getByTestId('executed-command')).not.toHaveClass(/execution-pulse/)
  await page.evaluate(()=>{
    window.resumedBeams=[]
    new MutationObserver(()=>{
      for(const beam of document.querySelectorAll('[data-effect="beam"]'))window.resumedBeams.push(beam.dataset.eventId)
    }).observe(document.querySelector('.board-stage'),{childList:true,subtree:true})
  })
  game.next([game.event('WEAPON_FIRED',1,{actionType:'LASER'})])
  await expect.poll(()=>page.evaluate(()=>window.resumedBeams.length)).toBeGreaterThan(0)
})

test('new ticks cancel stale effects and confirmed checkpoints have distinct feedback',async({page})=>{
  await page.clock.install()
  const game=await setup(page)
  await page.clock.pauseAt(new Date(await page.evaluate(()=>Date.now())+1000))
  game.next([game.event('CRASH')])
  for(let i=0;i<100&&await page.locator('[data-effect="crash"]').count()===0;i++)await page.clock.runFor(20)
  await expect(page.locator('[data-effect="crash"]')).toBeVisible()
  game.next([game.event('SCORE_CHANGED',1,{scoreReason:'CHECKPOINT',checkpointId:'CP1'})])
  for(let i=0;i<100&&await page.locator('[data-effect="capture"]').count()===0;i++)await page.clock.runFor(20)
  await expect(page.locator('[data-effect="capture"]')).toBeVisible()
  await expect(page.locator('[data-effect="crash"]')).toHaveCount(0)
  await page.clock.runFor(650)
  await expect(page.getByTestId('board-effect')).toHaveCount(0)
})

test('the shared scheduler dispatches audio once and suppresses reconnect, skipped and duplicate snapshots',async({page})=>{
  await page.addInitScript(()=>{window.audioDispatches=[]})
  await page.route('**/src/presentation/audio.ts',route=>route.fulfill({contentType:'application/javascript',body:`
    export const gameAudio={configure(){},prepare(){},unlock(){},stop(){},dispose(){},
      play(cue,id){window.audioDispatches.push({cue,id})},
      event(event,id){window.audioDispatches.push({cue:event.type,id})}}
  `}))
  const game=await setup(page)
  game.next([game.event('MOVE')],[game.event('VEHICLE_RESPAWNED')])
  await expect.poll(()=>page.evaluate(()=>window.audioDispatches.length)).toBe(3)
  const dispatches=await page.evaluate(()=>window.audioDispatches)
  expect(new Set(dispatches.map(item=>item.id)).size).toBe(3)
  game.state.continuous.revision++
  await page.waitForTimeout(650)
  expect(await page.evaluate(()=>window.audioDispatches.length)).toBe(3)
  game.state.continuous.tick+=3
  game.state.continuous.revision++
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick','5')
  expect(await page.evaluate(()=>window.audioDispatches.length)).toBe(3)
  await page.reload()
  await expect(page.getByTestId('player-vehicle')).toBeVisible()
  expect(await page.evaluate(()=>window.audioDispatches.length)).toBe(0)
})

test('laser rotation accumulates across ticks without reversing and resets on recovery',async({page})=>{
  const game=await setup(page)
  const spin=page.getByTestId('vehicle-spin')
  game.next([game.event('TURN',1,{actionType:'LASER'})])
  await expect(spin).toHaveAttribute('data-rotation','360')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  await expect(spin).toHaveAttribute('data-rotation','360')
  game.next([game.event('TURN',1,{actionType:'LASER'})])
  await expect(spin).toHaveAttribute('data-rotation','720')
  await expect(spin).toHaveAttribute('data-spin-count','1')
  game.state.continuous.tick+=3;game.state.continuous.revision++
  await expect(spin).toHaveAttribute('data-rotation','0')
})

async function recordM1Frames(page) {
  await page.evaluate(() => {
    window.m1Frames = []
    window.executionAppearances = []
    const observer = new MutationObserver(records => {
      for (const record of records) for (const node of record.addedNodes) {
        if (node.nodeType === 1 && node.matches('[data-testid="slot-execution"]')) window.executionAppearances.push(node.dataset.executedCommand)
      }
    })
    observer.observe(document.querySelector('.command-machine'), { childList: true, subtree: true })
    const sample = () => {
      const robot = document.querySelector('[data-testid="player-vehicle"]')
      const direction = robot.querySelector('.vehicle-direction')
      window.m1Frames.push({ x: robot.getBoundingClientRect().left, direction: getComputedStyle(direction).transform,
        recoil: getComputedStyle(robot.querySelector('.vehicle-recoil')).transform })
      if (window.m1Frames.length < 300) requestAnimationFrame(sample)
      else observer.disconnect()
    }
    requestAnimationFrame(sample)
  })
}

test('M1: smooth authoritative movement and turning never dispatch gameplay from animation', async ({ page }) => {
  const mutations = []
  page.on('request', request => { if (request.method() !== 'GET' && request.url().includes('/games/')) mutations.push(request.url()) })
  const game = await setup(page)
  await recordM1Frames(page)
  const before = await page.getByTestId('player-vehicle').boundingBox()
  game.next([game.event('MOVE'), game.event('TURN', 2, { oldPosition:{x:2,y:1}, newPosition:{x:2,y:1}, newDirection:'EAST' })])
  Object.assign(game.state.vehicles[0], { x:2, direction:'EAST' })
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-direction', 'EAST')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  const after = await page.getByTestId('player-vehicle').boundingBox()
  const frames = await page.evaluate(() => window.m1Frames)
  expect(frames.some(frame => frame.x > before.x + 1 && frame.x < after.x - 1)).toBe(true)
  expect(new Set(frames.map(frame => frame.direction)).size).toBeGreaterThan(2)
  await expect(page.locator('.vehicle-direction')).toHaveCSS('transform', 'matrix(0, 1, -1, 0, 0, 0)')
  expect(mutations).toEqual([])
})

test('M1: slot 1 highlights the executed command rather than the incoming queue head', async ({ page }) => {
  const game = await setup(page, 2, ['LASER','TURN_LEFT','FORWARD_1','FORWARD_1','TURN_RIGHT'])
  await recordM1Frames(page)
  game.next([])
  game.state.continuous.lastExecutedCommand = 'LASER'
  game.state.continuous.queue = ['TURN_LEFT','FORWARD_1','FORWARD_1','TURN_RIGHT',null]
  await expect.poll(() => page.evaluate(() => window.executionAppearances)).toEqual(['LASER'])
  await expect(page.getByTestId('slot-1')).toContainText('TURN_LEFT')
  await expect(page.getByTestId('slot-execution')).toHaveCount(0)
  game.state.continuous.revision++
  await page.waitForTimeout(650)
  expect(await page.evaluate(() => window.executionAppearances)).toEqual(['LASER'])
  game.next([]); game.state.continuous.lastExecutedCommand = 'WAIT'
  await expect.poll(() => page.evaluate(() => window.executionAppearances)).toEqual(['LASER','WAIT'])
  await expect(page.getByTestId('slot-execution')).toHaveCount(0)
  game.next([]); game.state.continuous.lastExecutedCommand = null
  await expect(page.getByTestId('executed-command')).toHaveText('Väntar')
  expect(await page.evaluate(() => window.executionAppearances)).toEqual(['LASER','WAIT'])
})

test('M1: collision recoil preserves the authoritative vehicle coordinates', async ({ page }) => {
  const game = await setup(page)
  await recordM1Frames(page)
  const before = await page.getByTestId('player-vehicle').boundingBox()
  game.next([game.event('MOVE_BLOCKED', 1, {newPosition:{x:1,y:1}})])
  await expect(page.locator('.vehicle-recoil')).toHaveAttribute('data-impact','true')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  const frames = await page.evaluate(() => window.m1Frames)
  expect(new Set(frames.map(frame => frame.recoil)).size).toBeGreaterThan(2)
  expect(frames.every(frame => Math.abs(frame.x - before.x) < .1)).toBe(true)
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','1')
})

for (const equalRevision of [false, true]) test(`M1: polling recovery snaps ${equalRevision ? 'an equal revision' : 'a consecutive tick'} without replay`, async ({ page }) => {
  const game = await setup(page)
  await recordM1Frames(page)
  if (equalRevision) {
    game.next([game.event('MOVE')]);game.state.vehicles[0].x=2
    await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','2')
  }
  game.disconnect()
  await expect.poll(() => game.failures).toBeGreaterThan(0)
  if (!equalRevision) {
    game.next([game.event('MOVE',1,{newPosition:{x:6,y:1}})])
    game.state.vehicles[0].x=6;game.state.continuous.lastExecutedCommand='FORWARD_1'
  }
  const previous = await page.evaluate(() => window.executionAppearances.length)
  game.resume()
  await page.waitForTimeout(650)
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x',equalRevision ? '2' : '6')
  const robot = await page.getByTestId('player-vehicle').boundingBox()
  const board = await page.getByTestId('game-board').boundingBox()
  expect(Math.abs(robot.x - board.x - board.width * (equalRevision ? .2 : .6))).toBeLessThan(1)
  expect(await page.evaluate(() => window.executionAppearances.length)).toBe(previous)
})

test('M1: changing reduced motion disables subsequent translation, rotation and recoil', async ({ page }) => {
  const game = await setup(page)
  await page.emulateMedia({reducedMotion:'reduce'})
  await recordM1Frames(page)
  game.next([game.event('MOVE',1,{newPosition:{x:4,y:1}}),game.event('TURN',2,{newPosition:{x:4,y:1},newDirection:'EAST'}),game.event('MOVE_BLOCKED',3,{oldPosition:{x:4,y:1},newPosition:{x:4,y:1},newDirection:'EAST'})])
  Object.assign(game.state.vehicles[0],{x:4,direction:'EAST'})
  await expect(page.locator('.vehicle-recoil')).toHaveAttribute('data-impact','true')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  const frames = await page.evaluate(() => window.m1Frames)
  const positions = new Set(frames.map(frame => Math.round(frame.x * 10)))
  expect(positions.size).toBeLessThanOrEqual(2)
  expect(frames.every(frame => frame.recoil === 'none')).toBe(true)
  await expect(page.getByTestId('slot-execution')).toHaveCount(0)
})

test('M1: enabling reduced motion stops an active robot and conveyor tween immediately', async ({ page }) => {
  const game = await setup(page,2,['LASER','TURN_LEFT','FORWARD_1','FORWARD_1','TURN_RIGHT'])
  game.next([game.event('MOVE',1,{newPosition:{x:7,y:1}})])
  game.state.vehicles[0].x=7
  game.state.continuous.queue=['TURN_LEFT','FORWARD_1','FORWARD_1','TURN_RIGHT',null]
  await page.evaluate(() => new Promise((resolve,reject) => {
    const started=performance.now()
    const sample=()=>{
      const robot=document.querySelector('[data-testid="player-vehicle"]')
      const board=document.querySelector('[data-testid="game-board"]').getBoundingClientRect()
      const x=robot.getBoundingClientRect().left
      if(robot.dataset.x==='7' && x>board.x+board.width*.1+1 && x<board.x+board.width*.7-1) return resolve(true)
      if(performance.now()-started>4000) return reject(new Error('Expected an intermediate movement frame'))
      requestAnimationFrame(sample)
    }
    requestAnimationFrame(sample)
  }))
  await page.emulateMedia({reducedMotion:'reduce'})
  await expect.poll(async()=>{
    const robot=await page.getByTestId('player-vehicle').boundingBox()
    const board=await page.getByTestId('game-board').boundingBox()
    return Math.abs(robot.x-board.x-board.width*.7)
  }).toBeLessThan(1)
  const cargo=page.locator('[data-testid="queue-cargo"][data-slot="2"]')
  await expect.poll(async()=>Math.abs((await cargo.boundingBox()).x-(await page.getByTestId('slot-2').boundingBox()).x-2)).toBeLessThan(1)
  await page.emulateMedia({reducedMotion:'no-preference'})
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-x','7')
})

async function mockM2Audio(page) {
  await page.addInitScript(()=>{window.m2Cues=[];window.m2Stopped=[]})
  await page.route('**/src/presentation/audio.ts',route=>route.fulfill({contentType:'application/javascript',body:`
    import {createAudioController} from '/src/presentation/audioController.ts'
    const controller=createAudioController({ready:()=>true,play(cue,volume,ended){
      window.m2Cues.push({cue,volume});const timer=setTimeout(ended,cue==='submit'?120:8)
      return {stop(){window.m2Stopped.push(cue);clearTimeout(timer)},setVolume(value){window.m2Cues.push({volume:value})}}
    }})
    export const gameAudio={...controller,prepare(){},unlock(){},dispose(){controller.stop()}}
  `}))
}

async function acceptMockCommand(page,game,{loseFirst=false,rejectFirst=false,advanceTick=false}={}) {
  const requests=[]
  await page.route('**/games/*/commands',async route=>{
    const body=route.request().postDataJSON();requests.push(body)
    if(requests.length===1&&rejectFirst)return route.fulfill({status:503,json:{message:'Try again'}})
    if(!game.state.continuous.queue.includes(body.command)){
      game.state.continuous.queue[2]=body.command;game.state.continuous.revision++
    }
    if(requests.length===1&&advanceTick)game.next([])
    if(requests.length===1&&loseFirst)return route.abort('failed')
    return route.fulfill({json:game.state})
  })
  return requests
}

test('M2: rejected submissions stay silent and confirmed retries sound once per request ID',async({page})=>{
  await mockM2Audio(page)
  const game=await setup(page),requests=await acceptMockCommand(page,game,{rejectFirst:true})
  await page.getByRole('button',{name:'Framåt',exact:true}).click()
  await expect(page.getByRole('button',{name:'Försök igen',exact:true})).toBeVisible()
  expect(await page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='submit'))).toHaveLength(0)
  await page.getByRole('button',{name:'Försök igen',exact:true}).click()
  await expect.poll(()=>page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='submit').length)).toBe(1)
  expect(requests[0].requestId).toBe(requests[1].requestId)
  game.state.continuous.revision++;await page.waitForTimeout(650)
  expect(await page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='submit'))).toHaveLength(1)
})

test('M2: a lost submission acknowledgement stays silent until confirmed after reload',async({page})=>{
  await mockM2Audio(page)
  const game=await setup(page),requests=await acceptMockCommand(page,game,{loseFirst:true})
  await page.getByRole('button',{name:'Framåt',exact:true}).click()
  await expect(page.getByRole('button',{name:'Försök igen',exact:true})).toBeVisible()
  expect(await page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='submit'))).toHaveLength(0)
  await page.reload()
  await page.getByRole('button',{name:'Försök igen',exact:true}).click()
  await expect.poll(()=>page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='submit').length)).toBe(1)
  expect(requests[0].requestId).toBe(requests[1].requestId)
})

test('M2: authoritative combat, respawn, capture and victory cues dispatch once',async({page})=>{
  await mockM2Audio(page)
  const game=await setup(page)
  game.next(['MOVE','TURN','RAM','MOVE_BLOCKED','WEAPON_FIRED','WEAPON_HIT','CRASH'].map((type,i)=>game.event(type,i+1,
    {newPosition:{x:1,y:1},...(['WEAPON_FIRED','WEAPON_HIT'].includes(type)?{actionType:'LASER'}:{})})))
  game.state.vehicles[0].status='CRASHED'
  await expect.poll(()=>page.evaluate(()=>window.m2Cues.filter(c=>c.cue).map(c=>c.cue))).toEqual(['tick','move','turn','impact','blocked','laser','hit','crash'])
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  game.state.vehicles[0].status='ACTIVE'
  game.next([game.event('SCORE_CHANGED',1,{scoreReason:'CHECKPOINT',checkpointId:'CP1'})],[game.event('VEHICLE_RESPAWNED')])
  await expect.poll(()=>page.evaluate(()=>window.m2Cues.filter(c=>c.cue).map(c=>c.cue).slice(-3))).toEqual(['tick','respawn','capture'])
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  game.state.status='FINISHED';game.state.placements=[{playerId:'local',placement:1,winner:true}];game.state.continuous.revision++
  await expect.poll(()=>page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='victory').length)).toBe(1)
  game.state.continuous.revision++;await page.waitForTimeout(650)
  expect(await page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='victory'))).toHaveLength(1)
  await page.reload();await expect(page.getByTestId('game-board')).toBeVisible()
  expect(await page.evaluate(()=>window.m2Cues.filter(c=>c.cue))).toHaveLength(0)
})

test('M2: late tick callbacks do not release a stale execution sound',async({page})=>{
  await mockM2Audio(page)
  const game=await setup(page)
  await page.evaluate(()=>{
    const frame=window.requestAnimationFrame.bind(window)
    window.requestAnimationFrame=callback=>frame(time=>setTimeout(()=>callback(time),190))
  })
  game.next([game.event('MOVE')])
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick','2')
  await page.waitForTimeout(400)
  expect(await page.evaluate(()=>window.m2Cues.filter(c=>c.cue))).toHaveLength(0)
})

test('M2: unavailable preference storage leaves mute and volume controls usable',async({page})=>{
  await page.addInitScript(()=>{
    const get=Storage.prototype.getItem,set=Storage.prototype.setItem
    Storage.prototype.getItem=function(key){if(key==='wreckage-audio')throw new Error('Unavailable');return get.call(this,key)}
    Storage.prototype.setItem=function(key,value){if(key==='wreckage-audio')throw new Error('Unavailable');return set.call(this,key,value)}
  })
  const errors=[];page.on('pageerror',error=>errors.push(error.message))
  await setup(page)
  await expect(page.getByRole('slider',{name:'Ljudvolym'})).toHaveValue('50')
  await page.getByRole('button',{name:'Stäng av ljud'}).click()
  await page.getByRole('slider',{name:'Ljudvolym'}).fill('12')
  await expect(page.getByRole('button',{name:'Slå på ljud'})).toHaveAttribute('aria-pressed','true')
  await expect(page.getByRole('slider',{name:'Ljudvolym'})).toHaveValue('12')
  expect(errors).toEqual([])
})

async function observeNativeAudio(page) {
  await page.addInitScript(()=>{
    window.nativeAudioStarts=[]
    const start=AudioBufferSourceNode.prototype.start
    AudioBufferSourceNode.prototype.start=function(...args){
      if(this.buffer&&this.buffer.length>1)window.nativeAudioStarts.push(this.buffer.duration)
      return start.apply(this,args)
    }
  })
}

test('M2: real Howler preloads silently and plays only after a trusted action',async({page})=>{
  const errors=[];page.on('pageerror',error=>errors.push(error.message))
  await observeNativeAudio(page)
  const game=await setup(page)
  await acceptMockCommand(page,game)
  await page.evaluate(()=>{
    window.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true}))
    document.body.dispatchEvent(new MouseEvent('click',{bubbles:true}))
  })
  game.next([game.event('MOVE')])
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick','2')
  expect(await page.evaluate(()=>window.nativeAudioStarts)).toHaveLength(0)
  await page.getByRole('button',{name:'Framåt',exact:true}).click()
  await expect.poll(()=>page.evaluate(()=>window.nativeAudioStarts.length)).toBe(1)
  await page.getByRole('button',{name:'Stäng av ljud'}).click()
  game.next([game.event('WEAPON_FIRED',1,{actionType:'LASER'})])
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick','3')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  await page.getByRole('button',{name:'Slå på ljud'}).click()
  expect(await page.evaluate(()=>window.nativeAudioStarts)).toHaveLength(1)
  expect(errors).toEqual([])
})

test('M2: failed sound decoding cannot prevent command submission or other cues',async({page})=>{
  const errors=[];page.on('pageerror',error=>errors.push(error.message))
  await observeNativeAudio(page)
  await page.route('**/audio/laser.wav',route=>route.fulfill({contentType:'audio/wav',body:'not a sound'}))
  const game=await setup(page)
  await acceptMockCommand(page,game)
  await page.getByRole('button',{name:'Framåt',exact:true}).click()
  await expect(page.getByTestId('slot-3')).toContainText('FORWARD_1')
  await expect.poll(()=>page.evaluate(()=>window.nativeAudioStarts.length)).toBe(1)
  game.next([game.event('WEAPON_FIRED',1,{actionType:'LASER'})])
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick','2')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  // Only the execution tick sounds; the invalid laser cannot start a source.
  expect(await page.evaluate(()=>window.nativeAudioStarts)).toHaveLength(2)
  expect(errors).toEqual([])
})

test('M2: real HTML5 fallback unlocks silently and honors mute without Web Audio',async({page})=>{
  const errors=[];page.on('pageerror',error=>errors.push(error.message))
  await page.addInitScript(()=>{
    window.AudioContext=undefined;window.webkitAudioContext=undefined;window.html5Volumes=[]
    const volume=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'volume')
    Object.defineProperty(HTMLMediaElement.prototype,'volume',{configurable:true,get:volume.get,set(value){
      if(value>0)window.html5Volumes.push({source:this.currentSrc||this.src,value})
      volume.set.call(this,value)
    }})
  })
  const game=await setup(page)
  await acceptMockCommand(page,game)
  expect(await page.evaluate(()=>window.html5Volumes)).toHaveLength(0)
  // Both mute controls are genuine gestures; silent priming must not create audible starts.
  await page.getByRole('button',{name:'Stäng av ljud'}).click()
  await page.getByRole('button',{name:'Slå på ljud'}).click()
  await page.getByRole('button',{name:'Framåt',exact:true}).click()
  await expect.poll(()=>page.evaluate(()=>window.html5Volumes.filter(entry=>entry.source.endsWith('/audio/submit.wav')).length)).toBeGreaterThan(0)
  await page.getByRole('button',{name:'Stäng av ljud'}).click()
  const previous=await page.evaluate(()=>window.html5Volumes.length)
  game.next([game.event('WEAPON_FIRED',1,{actionType:'LASER'})])
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick','2')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  expect(await page.evaluate(()=>window.html5Volumes.length)).toBe(previous)
  expect(errors).toEqual([])
})

test('M2: a command acknowledgement that advances the tick keeps its submission cue audible',async({page})=>{
  await mockM2Audio(page)
  const game=await setup(page)
  await acceptMockCommand(page,game,{advanceTick:true})
  await page.getByRole('button',{name:'Framåt',exact:true}).click()
  await expect(page.getByTestId('executed-command')).toHaveAttribute('data-execution-tick','2')
  await expect.poll(()=>page.evaluate(()=>window.m2Cues.filter(c=>c.cue==='submit').length)).toBe(1)
  await page.waitForTimeout(180)
  expect(await page.evaluate(()=>window.m2Stopped.filter(c=>c==='submit'))).toHaveLength(0)
})

for (const reducedMotion of ['no-preference','reduce']) {
  test(`capture and successor flashes overlap without reviving captured flags (${reducedMotion})`,async({page})=>{
    await page.emulateMedia({reducedMotion})
    await page.clock.install()
    const checkpoints=[1,2,3].map(order=>({id:`CP${order}`,order,position:{x:order+1,y:1}}))
    const game=await setup(page,2,[null,null,null,null,null],checkpoints)
    await page.clock.pauseAt(new Date(await page.evaluate(()=>Date.now())+1000))
    const effect=(kind,id)=>page.locator(`[data-effect="${kind}"][data-checkpoint-id="${id}"]`)
    async function advanceUntil(locator,count=1) {
      for(let i=0;i<100&&await locator.count()!==count;i++)await page.clock.runFor(20)
      await expect(locator).toHaveCount(count)
    }
    game.state.players[0].visitedCheckpoints=['CP1']
    game.state.players[1].visitedCheckpoints=['CP2']
    game.state.players.forEach(player=>player.capturedCheckpoints=1)
    game.next([game.event('MOVE'),game.event('SCORE_CHANGED',2,{scoreReason:'CHECKPOINT',checkpointId:'CP1'}),
      game.event('SCORE_CHANGED',3,{scoreReason:'CHECKPOINT',checkpointId:'CP2',playerId:'player-1'}),game.event('TURN',4)])
    await advanceUntil(effect('capture','CP1'))
    await expect(page.locator('[data-testid="board-checkpoint"][data-checkpoint-id="CP1"]')).toHaveCount(0)
    await expect(effect('capture','CP1')).toHaveAttribute('transform','translate(2.5,8.5)')
    await expect(effect('activation','CP2')).toHaveCount(0)
    await expect(page.locator('[data-testid="board-checkpoint"][data-state=available]')).toHaveAttribute('data-checkpoint-id','CP3')
    await expect(page.locator('[data-testid="board-checkpoint"][data-state=available]')).toHaveCSS('animation-name','none')
    const original=await effect('capture','CP1').elementHandle()
    await acceptMockCommand(page,game)
    await page.getByRole('button',{name:'Framåt',exact:true}).click()
    await expect(page.getByTestId('slot-3')).toContainText('FORWARD_1')
    expect(await original.evaluate(el=>el===document.querySelector('[data-effect="capture"][data-checkpoint-id="CP1"]'))).toBe(true)
    await page.screenshot({path:`/tmp/wreckage-flag-capture-${reducedMotion}.png`})
    await advanceUntil(effect('activation','CP2'))
    await expect(effect('capture','CP1')).toHaveCount(0)
    await expect(effect('capture','CP2')).toHaveCount(1)
    await expect(effect('activation','CP2')).toHaveAttribute('transform','translate(3.5,8.5)')
    await expect(page.locator('[data-testid="board-checkpoint"][data-checkpoint-id="CP2"]')).toHaveCount(0)
    await expect(effect('activation','CP2')).toHaveAttribute('data-motion',reducedMotion==='reduce'?'off':'on')
    await expect(effect('activation','CP2').locator('.effect-activation-frame')).toHaveCSS('vector-effect','non-scaling-stroke')
    if(reducedMotion==='reduce'){
      await expect(effect('activation','CP2')).toHaveCSS('animation-name','none')
      await expect(effect('activation','CP2').locator('.effect-activation-frame')).toHaveCSS('animation-name','none')
    }
    await page.screenshot({path:`/tmp/wreckage-flag-overlap-${reducedMotion}.png`})
    await advanceUntil(effect('activation','CP3'))
    await expect(effect('capture','CP2')).toHaveCount(0)
    await page.screenshot({path:`/tmp/wreckage-flag-activation-${reducedMotion}.png`})
    await page.clock.runFor(1700)
    await expect(page.getByTestId('board-effect')).toHaveCount(0)
    await expect(page.locator('[data-testid="board-checkpoint"][data-state=available]')).toHaveCSS('animation-name',reducedMotion==='reduce'?'none':'flag-available-pulse')
    game.state.continuous.revision++
    await page.clock.runFor(650)
    await expect(page.getByTestId('board-effect')).toHaveCount(0)
    game.disconnect()
    await page.clock.runFor(650)
    await expect.poll(()=>game.failures).toBeGreaterThan(0)
    game.resume()
    await page.clock.runFor(650)
    await expect(page.getByTestId('board-effect')).toHaveCount(0)
    await page.reload()
    await expect(page.getByTestId('game-board')).toBeVisible()
    await expect(page.getByTestId('board-effect')).toHaveCount(0)
    game.state.players[0].visitedCheckpoints.push('CP3');game.state.players[0].capturedCheckpoints=2
    game.next([game.event('SCORE_CHANGED',1,{scoreReason:'CHECKPOINT',checkpointId:'CP3'})])
    await advanceUntil(effect('capture','CP3'))
    await expect(page.getByTestId('board-checkpoint')).toHaveCount(0)
    await expect(page.locator('[data-effect="activation"]')).toHaveCount(0)
    await page.clock.runFor(1700)
    await expect(page.getByTestId('board-effect')).toHaveCount(0)
    await page.reload()
    await expect(page.getByTestId('game-board')).toBeVisible()
    await expect(page.getByTestId('board-effect')).toHaveCount(0)
    await expect(page.getByTestId('board-checkpoint')).toHaveCount(0)
  })
}

for (const reducedMotion of ['no-preference','reduce']) {
  test(`flag transfer colours replace older highlights without replay (${reducedMotion})`,async({page})=>{
    await page.emulateMedia({reducedMotion})
    await page.clock.install()
    const checkpoints=[1,2,3].map(order=>({id:`CP${order}`,order,position:{x:order+1,y:1}}))
    const game=await setup(page,3,[null,null,null,null,null],checkpoints)
    game.state.players[0].visitedCheckpoints=['CP1','CP2'];game.state.players[0].capturedCheckpoints=2
    game.state.vehicles=game.state.players.map((player,i)=>({id:`robot-${i}`,playerId:player.id,x:i+1,y:1,direction:'NORTH',status:'ACTIVE'}))
    game.state.continuous.revision++
    await page.reload()
    await expect(page.getByTestId('flag-strip')).toBeVisible()
    await page.clock.pauseAt(new Date(await page.evaluate(()=>Date.now())+1000))
    const flag=id=>page.locator(`[data-testid="flag-status"][data-checkpoint-id="${id}"]`)
    const highlight=id=>flag(id).getByTestId('flag-transfer')
    async function advanceUntil(locator){
      for(let i=0;i<100&&await locator.count()===0;i++)await page.clock.runFor(20)
      await expect(locator).toHaveCount(1)
    }
    const transfer=(id,sequence,from,to)=>game.event('FLAG_TRANSFERRED',sequence,{checkpointId:id,playerId:from,sourcePlayerId:to})
    await expect(page.getByTestId('flag-transfer')).toHaveCount(0)
    game.state.players[0].visitedCheckpoints=[];game.state.players[0].capturedCheckpoints=0
    game.state.players[1].visitedCheckpoints=['CP1','CP2'];game.state.players[1].capturedCheckpoints=2
    game.next([game.event('WEAPON_HIT'),transfer('CP1',2,'local','player-1'),transfer('CP2',3,'local','player-1'),game.event('TURN',4)])
    await advanceUntil(highlight('CP1'))
    await expect(page.getByTestId('flag-transfer')).toHaveCount(2)
    for(const id of ['CP1','CP2']){
      await expect(flag(id)).toHaveAttribute('data-owner-id','player-1')
      await expect(highlight(id)).toHaveAttribute('data-motion',reducedMotion==='reduce'?'off':'on')
      const colours=await flag(id).evaluate(el=>{
        const style=getComputedStyle(el)
        return {from:style.getPropertyValue('--transfer-from').trim(),to:style.getPropertyValue('--transfer-to').trim()}
      })
      expect(colours).toEqual({from:'#dbb66d',to:'#afbfc4'})
      if(reducedMotion==='reduce')await expect(highlight(id)).toHaveCSS('animation-name','none')
      else{
        const samples=await highlight(id).evaluate(el=>{
          const animation=el.getAnimations()[0],time=animation.currentTime
          const delay=parseFloat(getComputedStyle(el).animationDelay)*1000
          animation.pause();animation.currentTime=delay
          const from=getComputedStyle(el).backgroundColor
          animation.currentTime=delay+390
          const to=getComputedStyle(el).backgroundColor
          animation.currentTime=time;animation.play()
          return {from,to}
        })
        expect(samples).toEqual({from:'rgb(219, 182, 109)',to:'rgb(175, 191, 196)'})
      }
    }
    const original=await highlight('CP1').elementHandle(),oldId=await highlight('CP1').getAttribute('data-event-id')
    await acceptMockCommand(page,game)
    await page.getByRole('button',{name:'Framåt',exact:true}).click()
    expect(await original.evaluate(el=>el===document.querySelector('[data-checkpoint-id="CP1"] [data-testid="flag-transfer"]'))).toBe(true)
    await page.screenshot({path:`/tmp/wreckage-flag-transfer-${reducedMotion}.png`})
    game.state.players[1].visitedCheckpoints=['CP2'];game.state.players[1].capturedCheckpoints=1
    game.state.players[2].visitedCheckpoints=['CP1'];game.state.players[2].capturedCheckpoints=1
    game.next([game.event('WEAPON_HIT'),transfer('CP1',2,'player-1','player-2'),game.event('TURN',3)])
    await advanceUntil(page.locator(`[data-testid="flag-transfer"][data-event-id*=":${game.state.continuous.tick}:playback:"]`))
    await expect(flag('CP1')).toHaveAttribute('data-owner-id','player-2')
    expect(await highlight('CP1').getAttribute('data-event-id')).not.toBe(oldId)
    await expect(highlight('CP2')).toHaveCount(0)
    if(reducedMotion==='no-preference'){
      await page.emulateMedia({reducedMotion:'reduce'})
      await expect(highlight('CP1')).toHaveAttribute('data-motion','off')
      await page.emulateMedia({reducedMotion:'no-preference'})
      await expect(highlight('CP1')).toHaveAttribute('data-motion','off')
      await expect(highlight('CP1')).toHaveCSS('animation-name','none')
    }
    game.disconnect();await page.clock.runFor(500)
    await expect.poll(()=>game.failures).toBeGreaterThan(0)
    game.resume();await page.clock.runFor(500)
    await expect(page.getByTestId('flag-transfer')).toHaveCount(0)
    await page.clock.runFor(1700)
    game.state.continuous.revision++;await page.clock.runFor(650)
    await expect(page.getByTestId('flag-transfer')).toHaveCount(0)
    await expect(flag('CP1')).toHaveAttribute('data-owner-id','player-2')
    await page.reload()
    await expect(page.getByTestId('flag-strip')).toBeVisible()
    await expect(page.getByTestId('flag-transfer')).toHaveCount(0)
    await expect(flag('CP1')).toHaveAttribute('data-owner-id','player-2')
  })
}

test('M3: capture brackets and standings feedback keep checkpoint progress outside the board',async({page})=>{
  await page.clock.install()
  const game=await setup(page)
  await page.clock.pauseAt(new Date(await page.evaluate(()=>Date.now())+1000))
  await expect(page.locator('.board-stage [data-testid="checkpoint-progress"]')).toHaveCount(0)
  await expect(page.locator('.compact-scores [data-testid="checkpoint-progress"]')).toBeVisible()
  game.next([game.event('SCORE_CHANGED',1,{scoreReason:'CHECKPOINT',checkpointId:'CP1'})])
  const capture=page.locator('[data-effect="capture"]')
  for(let i=0;i<100&&await capture.count()===0;i++)await page.clock.runFor(20)
  await expect(capture).toBeVisible()
  await expect(capture).toHaveAttribute('transform','translate(2.5,8.5)')
  await expect(page.getByTestId('capture-highlight')).toBeVisible()
  expect(await capture.evaluate(el=>getComputedStyle(el.closest('svg')).pointerEvents)).toBe('none')
  expect(await page.getByTestId('board-checkpoint').evaluate(el=>Number(getComputedStyle(el).zIndex))).toBeGreaterThan(3)
  await page.screenshot({path:'/tmp/wreckage-m3-capture-desktop.png'})
  await page.clock.runFor(650)
  await expect(capture).toHaveCount(0)
  await expect(page.getByTestId('capture-highlight')).toHaveCount(0)
})

test('M3: live reduced-motion changes cancel debris and shake without replaying old effects',async({page})=>{
  await page.clock.install()
  const game=await setup(page)
  await page.clock.pauseAt(new Date(await page.evaluate(()=>Date.now())+1000))
  await page.evaluate(()=>{
    window.shakes=[]
    const original=Element.prototype.animate
    Element.prototype.animate=function(frames,options){
      if(this.matches('[data-testid="game-board"]'))window.shakes.push({frames,options})
      return original.call(this,frames,options)
    }
  })
  game.next([game.event('CRASH')])
  const effect=page.locator('[data-effect="crash"]')
  for(let i=0;i<100&&await effect.count()===0;i++)await page.clock.runFor(20)
  await expect(effect).toHaveAttribute('data-motion','on')
  await page.emulateMedia({reducedMotion:'reduce'})
  await expect(effect).toHaveAttribute('data-motion','off')
  expect(await effect.locator('.effect-fragment').first().evaluate(el=>getComputedStyle(el).display)).toBe('none')
  await page.emulateMedia({reducedMotion:'no-preference'})
  await expect(effect).toHaveAttribute('data-motion','off')
  const shakes=await page.evaluate(()=>window.shakes)
  expect(shakes).toHaveLength(1)
  expect(shakes[0].options.duration).toBe(120)
  expect(shakes[0].frames.map(frame=>frame.transform)).toEqual(['none','translate(2px,-1px)','translate(-1px,1px)','none'])
  await page.clock.runFor(650)
  await expect(effect).toHaveCount(0)
  game.state.continuous.revision++
  await page.clock.runFor(650)
  expect(await page.evaluate(()=>window.shakes.length)).toBe(1)
})

test('M3: respawn deployment and settled board need no idle animation frames or DOM mutations',async({page})=>{
  const game=await setup(page)
  game.next([],[game.event('VEHICLE_RESPAWNED')])
  await expect(page.locator('[data-effect="respawn"] .effect-deployment')).toBeVisible()
  await expect(page.getByTestId('player-vehicle')).toHaveAttribute('data-respawning','on')
  await page.screenshot({path:'/tmp/wreckage-m3-respawn-desktop.png'})
  await expect(page.getByTestId('board-effect')).toHaveCount(0)
  await page.waitForTimeout(1800)
  await page.evaluate(()=>{
    window.idleWork={frames:0,mutations:0}
    const original=window.requestAnimationFrame
    window.requestAnimationFrame=function(callback){window.idleWork.frames++;return original.call(this,callback)}
    window.idleObserver=new MutationObserver(records=>window.idleWork.mutations+=records.length)
    window.idleObserver.observe(document.querySelector('[data-testid="game-board"]'),{subtree:true,childList:true,attributes:true})
  })
  await page.waitForTimeout(700)
  expect(await page.evaluate(()=>window.idleWork)).toEqual({frames:0,mutations:0})
})
