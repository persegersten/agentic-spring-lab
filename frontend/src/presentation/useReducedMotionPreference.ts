import { useSyncExternalStore } from 'react'
import { useReducedMotion } from 'motion/react'

const query = '(prefers-reduced-motion: reduce)'
let media: MediaQueryList | undefined
const mediaQuery = () => media ??= window.matchMedia(query)
const readPreference = () => mediaQuery().matches
function subscribe(listener: () => void) {
  const preference = mediaQuery()
  preference.addEventListener('change', listener)
  return () => preference.removeEventListener('change', listener)
}

export function useReducedMotionPreference() {
  // Motion 14 captures the initial preference; subscribe explicitly for live changes.
  const initialPreference = useReducedMotion()
  return useSyncExternalStore(subscribe, readPreference, () => initialPreference ?? false)
}
