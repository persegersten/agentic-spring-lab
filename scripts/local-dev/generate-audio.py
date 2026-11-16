#!/usr/bin/env python3
"""Reproduce Wreckage's original, deterministic mono PCM sound effects (standard library only)."""
import math
from pathlib import Path
import random
import struct
import wave

ROOT = Path(__file__).resolve().parents[2] / 'frontend/public/audio'
# duration, start/end pitch, noise mix, decay, amplitude
SOUNDS = {
    'submit': (.12, 680, 190, .45, 20, .55),
    'tick': (.10, 200, 70, .35, 30, .45),
    'move': (.19, 95, 135, .12, 10, .35),
    'turn': (.16, 220, 100, .15, 15, .35),
    'impact': (.24, 190, 55, .65, 17, .65),
    'blocked': (.10, 160, 80, .45, 35, .5),
    'laser': (.25, 1500, 170, .08, 12, .45),
    'hit': (.20, 550, 80, .7, 24, .55),
    'crash': (.55, 130, 35, .8, 9, .65),
    'respawn': (.40, 100, 480, .08, 5, .4),
    'capture': (.45, 440, 880, .02, 5, .4),
    'victory': (.65, 330, 990, .01, 3, .4),
    'complete': (.45, 440, 220, .04, 6, .4),
    'shield': (.30, 190, 570, .04, 6, .4),
}
ROOT.mkdir(parents=True, exist_ok=True)
for index, (name, (duration, start, end, noise, decay, amplitude)) in enumerate(SOUNDS.items()):
    rng = random.Random(4700 + index)
    rate = 22050
    frames = bytearray()
    phase = 0.0
    filtered = 0.0
    for frame in range(int(rate * duration)):
        t = frame / rate
        f = start + (end - start) * t / duration
        phase += 2 * math.pi * f / rate
        # Inharmonic partials give impacts a short metallic ring; filter the noise to avoid harsh hiss.
        filtered = .6 * filtered + .4 * rng.uniform(-1, 1)
        tone = (.65 * math.sin(phase) + .22 * math.sin(phase * 2.71) + .13 * math.sin(phase * 4.13))
        envelope = min(1, t / .004) * math.exp(-decay * t) * min(1, (duration - t) / .025)
        value = amplitude * envelope * ((1 - noise) * tone + noise * filtered)
        frames.extend(struct.pack('<h', round(32767 * max(-1, min(1, value)))))
    with wave.open(str(ROOT / f'{name}.wav'), 'wb') as output:
        output.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
        output.writeframes(frames)
