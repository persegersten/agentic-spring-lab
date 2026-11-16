import { expect, test } from '@playwright/test'

async function setup(page, count = 2) {
  const gameId='10000000-0000-0000-0000-000000000007', playerId='local'
  const vehicle = {id:'robot',playerId,x:1,y:1,direction:'NORTH',status:'ACTIVE'}
  const state={id:gameId,playerId,status:'RUNNING',players:Array.from({length:count},(_,i)=>({id:i ? `player-${i}`:playerId,name:`Robot ${i+1}`,capturedCheckpoints:0,visitedCheckpoints:[]})),
    board:{width:10,height:10,walls:[],pits:[],obstacles:[],checkpoints:[{id:'CP1',order:1,position:{x:2,y:1}}],spawnPoints:[],conveyors:[],rotators:[],controlPoints:[]},
    vehicles:[vehicle],placements:[],round:null,shieldAvailable:true,
    continuous:{revision:1,tick:1,serverTime:new Date().toISOString(),nextTick:new Date(Date.now()+2000).toISOString(),intervalMillis:2000,queue:[null,null,null,null,null],lastExecutedCommand:'WAIT',activeShieldPlayerIds:[],shieldTicksRemaining:0}}
  let response=state
  await page.addInitScript(({gameId,playerId})=>localStorage.setItem(`wreckage-session:${gameId}`,JSON.stringify({gameId,playerId,token:'test'})),{gameId,playerId})
  await page.route(`**/games/${gameId}/players/${playerId}`,route=>route.fulfill({json:response}))
  await page.goto(`/game/${gameId}`)
  await expect(page.getByTestId('player-vehicle')).toBeVisible()
  const next=(events, startEvents=[])=>{
    const initial=structuredClone(state.vehicles)
    state.continuous.tick++;state.continuous.revision++
    state.round={state:{number:state.continuous.tick,initialVehicles:initial,startEvents,playback:events}}
  }
  const event=(type,sequence=1,extra={})=>({type,sequence,playerId,vehicleId:'robot',sourcePlayerId:playerId,sourceVehicleId:'robot',oldPosition:{x:1,y:1},newPosition:{x:2,y:1},oldDirection:'NORTH',newDirection:'NORTH',...extra})
  return {state,next,event,respond(value){response=value}}
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
  game.next([game.event('WEAPON_FIRED',1,{actionType:'LASER'})])
  await expect(page.getByTestId('laser-shot')).toBeVisible()
})

test('new ticks cancel stale effects and confirmed checkpoints have distinct feedback',async({page})=>{
  const game=await setup(page)
  game.next([game.event('CRASH')])
  await expect(page.locator('[data-effect="crash"]')).toBeVisible()
  game.next([game.event('SCORE_CHANGED',1,{scoreReason:'CHECKPOINT',checkpointId:'CP1'})])
  await expect(page.locator('[data-effect="capture"]')).toBeVisible()
  await expect(page.locator('[data-effect="crash"]')).toHaveCount(0)
  await expect(page.getByTestId('board-effect')).toHaveCount(0)
})

test('the shared scheduler dispatches audio once and suppresses reconnect, skipped and duplicate snapshots',async({page})=>{
  await page.addInitScript(()=>{window.audioDispatches=[]})
  await page.route('**/src/presentation/audio.ts',route=>route.fulfill({contentType:'application/javascript',body:`
    export const gameAudio={configure(){},unlock(){},stop(){},
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
  await expect(spin).toHaveAttribute('style','transform: rotate(360deg);')
  await expect(page.locator('.tick-board')).toHaveClass(/timeline-snap/)
  await expect(spin).toHaveAttribute('style','transform: rotate(360deg);')
  game.next([game.event('TURN',1,{actionType:'LASER'})])
  await expect(spin).toHaveAttribute('style','transform: rotate(720deg);')
  await expect(spin).toHaveAttribute('data-spin-count','1')
  game.state.continuous.tick+=3;game.state.continuous.revision++
  await expect(spin).toHaveAttribute('style','transform: rotate(0deg);')
})
