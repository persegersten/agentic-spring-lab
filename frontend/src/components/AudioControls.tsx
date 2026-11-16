import { useEffect, useLayoutEffect, useState } from 'react'
import { gameAudio } from '../presentation/audio'
import type { AudioSettings } from '../presentation/audioController'
import { Icon } from './Icon'

function load(): AudioSettings {
  try {
    const value = JSON.parse(localStorage.getItem('wreckage-audio') ?? 'null')
    if (typeof value?.muted === 'boolean' && Number.isFinite(value?.volume)) return { muted: value.muted, volume: Math.max(0, Math.min(1, value.volume)) }
  } catch { /* Storage can be unavailable. */ }
  return { muted: false, volume: .5 }
}
export function AudioControls() {
  const [settings, setSettings] = useState(load)
  useLayoutEffect(() => {
    gameAudio.configure(settings)
    try { localStorage.setItem('wreckage-audio', JSON.stringify(settings)) } catch { /* Session-only preferences. */ }
  }, [settings])
  useEffect(() => {
    gameAudio.prepare()
    const unlock = (event: Event) => { if (event.isTrusted) gameAudio.unlock() }
    const events = ['pointerdown', 'touchend', 'click', 'keydown'] as const
    for (const event of events) window.addEventListener(event, unlock)
    return () => {
      for (const event of events) window.removeEventListener(event, unlock)
      gameAudio.dispose()
    }
  }, [])
  return <div className="audio-controls">
    <button className="secondary" aria-label={settings.muted ? 'Slå på ljud' : 'Stäng av ljud'} aria-pressed={settings.muted}
      onClick={() => setSettings(old => ({ ...old, muted: !old.muted }))}><Icon name={settings.muted ? 'muted' : 'sound'} /></button>
    <label><span className="visually-hidden">Ljudvolym</span><input aria-label="Ljudvolym" type="range" min="0" max="100" value={Math.round(settings.volume * 100)}
      onChange={e => setSettings(old => ({ ...old, volume: Number(e.target.value) / 100 }))} /></label>
  </div>
}
