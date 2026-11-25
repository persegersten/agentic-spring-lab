import { memo, useEffect, useMemo, useState } from 'react'
import type { PlayerGame } from '../types/game'

/** The countdown owns its timer; the board and controls do not render on clock updates. */
export const TickStatus = memo(function TickStatus({ snapshot, running, finishing = false }: { snapshot: PlayerGame['continuous']; running: boolean; finishing?: boolean }) {
  const [now, setNow] = useState(Date.now())
  const [visible, setVisible] = useState(!document.hidden)
  const offset = useMemo(() => snapshot ? Date.now() - Date.parse(snapshot.serverTime) : 0, [snapshot])
  useEffect(() => {
    const change = () => { setVisible(!document.hidden); setNow(Date.now()) }
    document.addEventListener('visibilitychange', change)
    return () => document.removeEventListener('visibilitychange', change)
  }, [])
  useEffect(() => {
    if (!visible || !running) return
    const timer = window.setInterval(() => setNow(Date.now()), 100)
    return () => window.clearInterval(timer)
  }, [visible, running])
  const remaining = snapshot ? Math.max(0, (Date.parse(snapshot.nextTick) - now + offset) / 1000).toFixed(1) : '…'
  return <p className="tick-status">Tick {snapshot?.tick ?? 0} · {running ? `${remaining} s` : finishing ? 'Avslutar' : 'Slut'}</p>
})
