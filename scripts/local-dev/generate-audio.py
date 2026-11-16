#!/usr/bin/env python3
"""Reproduce Wreckage's original CC0 sound palette; standard library only, no samples."""
import hashlib
import json
import math
from pathlib import Path
import random
import struct
import wave

ROOT = Path(__file__).resolve().parents[2] / 'frontend/public/audio'
RATE = 22050
DURATIONS = {
    'submit': .12, 'tick': .10, 'move': .18, 'turn': .16,
    'impact': .24, 'blocked': .10, 'laser': .18, 'hit': .20,
    'crash': .48, 'respawn': .38, 'capture': .36, 'victory': .65,
    'complete': .40, 'shield': .28,
}


def envelope(t, decay=20):
    return 0 if t < 0 else min(1, t / .003) * math.exp(-decay * t)


def ring(t, frequency, decay=20):
    return envelope(t, decay) * sum(
        weight * math.sin(2 * math.pi * frequency * ratio * t)
        for weight, ratio in ((.65, 1), (.22, 2.71), (.13, 4.13))
    )


def melody(t, notes, spacing, decay=22):
    return sum(envelope(t - index * spacing, decay) * math.sin(2 * math.pi * frequency * (t - index * spacing))
               for index, frequency in enumerate(notes))


def sample(name, t, noise):
    if name in ('submit', 'tick', 'blocked'):
        frequency = {'submit': 430, 'tick': 125, 'blocked': 180}[name]
        return .7 * ring(t, frequency, 45) + .3 * ring(t - .024, frequency * .7, 55) + .3 * noise * envelope(t, 85)
    if name in ('move', 'turn'):
        frequency = 92 if name == 'move' else 170
        phase = 2 * math.pi * (frequency * t + (55 if name == 'move' else -120) * t * t)
        return envelope(t, 14) * (.65 * math.sin(phase) + .18 * math.sin(phase * 2) + .17 * noise)
    if name in ('impact', 'hit'):
        frequency = 175 if name == 'impact' else 340
        return ring(t, frequency, 19) + .65 * noise * envelope(t, 65)
    if name == 'laser':
        phase = 2 * math.pi * (260 * t + 1050 * .028 * (1 - math.exp(-t / .028)))
        return envelope(t, 22) * (.85 * math.sin(phase) + .15 * math.sin(phase * 1.5))
    if name == 'crash':
        debris = sum(.25 * ring(t - delay, frequency, 35) for delay, frequency in ((.08, 350), (.18, 250), (.28, 470)))
        return envelope(t, 9) * (.65 * noise + .35 * math.sin(2 * math.pi * 62 * t)) + debris
    if name == 'respawn':
        return .35 * envelope(t, 10) * math.sin(2 * math.pi * (100 * t + 350 * t * t)) + melody(t, (220, 330, 440), .08, 25)
    if name == 'capture':
        return melody(t, (523.25, 659.25, 783.99), .075, 25)
    if name == 'victory':
        return melody(t, (392, 493.88, 587.33, 783.99), .12, 16)
    if name == 'complete':
        return melody(t, (392, 329.63), .12, 18)
    return envelope(t, 12) * (math.sin(2 * math.pi * 190 * t) + .35 * math.sin(2 * math.pi * 570 * t))


ROOT.mkdir(parents=True, exist_ok=True)
manifest = {'source': 'Original Wreckage synthesis (no third-party samples)', 'license': 'CC0-1.0',
            'generator': 'scripts/local-dev/generate-audio.py', 'files': []}
for index, (name, duration) in enumerate(DURATIONS.items()):
    rng = random.Random(4700 + index)
    filtered = 0
    samples = []
    for frame in range(round(RATE * duration)):
        t = frame / RATE
        filtered = .6 * filtered + .4 * rng.uniform(-1, 1)
        fade = min(1, (duration - t) / .025)
        samples.append(sample(name, t, filtered) * fade)
    peak = max(abs(value) for value in samples)
    frames = b''.join(struct.pack('<h', round(32767 * .7 * value / peak)) for value in samples)
    path = ROOT / f'{name}.wav'
    with wave.open(str(path), 'wb') as output:
        output.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        output.writeframes(frames)
    manifest['files'].append({'file': path.name, 'sourceFile': f'original-synthesis:{name}',
                              'durationMs': round(duration * 1000), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                              'modifications': 'Fixed-seed synthesis, 25 ms tail fade, peak normalized to 0.7'})
(ROOT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
