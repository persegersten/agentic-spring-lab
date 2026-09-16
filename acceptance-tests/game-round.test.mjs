import assert from 'node:assert/strict'
import { test } from 'node:test'
const baseUrl=process.env.BASE_URL??'http://localhost:8080'
async function request(path,options={},status=200){const response=await fetch(baseUrl+path,options);const body=await response.json();assert.equal(response.status,status,JSON.stringify(body));return body}
async function join(gameId,name){return request(`/games/${gameId}/players`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name})},201)}
function headers(player){return {'X-Player-Id':player.id,'X-Player-Token':player.token,'Content-Type':'application/json'}}
test('players program private cards and receive a shared three-step playback',async()=>{
 const game=await request('/games',{method:'POST'},201),alice=await join(game.id,'Alice'),bob=await join(game.id,'Bob');
 const aliceView=await request(`/games/${game.id}/rounds`,{method:'POST',headers:headers(alice)},201);
 const bobView=await request(`/games/${game.id}/players/${bob.id}`,{headers:{'X-Player-Token':bob.token}});
 assert.equal(aliceView.round.hand.length,3);assert.equal(bobView.round.hand.length,3);
 const publicView=await request(`/games/${game.id}`);assert.equal(JSON.stringify(publicView).includes('hand'),false);
 await request(`/games/${game.id}/rounds/current/program`,{method:'POST',headers:headers(alice),body:JSON.stringify({orders:aliceView.round.hand})});
 const done=await request(`/games/${game.id}/rounds/current/program`,{method:'POST',headers:headers(bob),body:JSON.stringify({orders:bobView.round.hand})});
 assert.equal(done.round.state.phase,'PLAYBACK');assert.equal(done.round.state.playback.length,3);
})
