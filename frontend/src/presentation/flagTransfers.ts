import type { PresentedEvent, TickPresentation } from './timeline'

export type FlagTransfer = PresentedEvent & { age: number; motion: boolean }

/** Select the latest presented transfer per flag before checking expiry, so old transitions cannot return. */
export function currentFlagTransfers(schedule: TickPresentation, elapsed: number): PresentedEvent[] {
  const latest = new Map<string, PresentedEvent>()
  for (const item of schedule.events) {
    if (item.event.type === 'FLAG_TRANSFERRED' && item.event.checkpointId && item.at <= elapsed) {
      latest.set(item.event.checkpointId, item)
    }
  }
  return [...latest.values()].filter(item => item.until > elapsed)
}
