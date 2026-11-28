/** Locally bundled, short, non-looping sounds. Sources and licenses: docs/assets.md. */
export const audioCues = {
  submit: { file: 'submit.wav', durationMs: 120, gain: .5 },
  tick: { file: 'tick.wav', durationMs: 100, gain: .16 },
  move: { file: 'move.wav', durationMs: 180, gain: .24 },
  turn: { file: 'turn.wav', durationMs: 160, gain: .24 },
  impact: { file: 'impact.wav', durationMs: 240, gain: .65 },
  blocked: { file: 'blocked.wav', durationMs: 100, gain: .38 },
  laser: { file: 'laser.wav', durationMs: 180, gain: .65 },
  hit: { file: 'hit.wav', durationMs: 200, gain: .75 },
  crash: { file: 'crash.wav', durationMs: 480, gain: .65 },
  respawn: { file: 'respawn.wav', durationMs: 380, gain: .46 },
  capture: { file: 'capture.wav', durationMs: 360, gain: .7 },
  victory: { file: 'victory.wav', durationMs: 650, gain: 1 },
  complete: { file: 'complete.wav', durationMs: 400, gain: .42 },
  shield: { file: 'shield.wav', durationMs: 280, gain: .4 },
} as const
export type Cue = keyof typeof audioCues
