import { useLayoutEffect } from 'react'
import { animate, useMotionValue } from 'motion/react'
import { useReducedMotionPreference } from './useReducedMotionPreference'

/** A cancellable visual tween. Discontinuities set the confirmed target immediately. */
export function usePresentationTween<T extends string | number>(target: T, enabled: boolean, duration: number) {
  const value = useMotionValue(target)
  const reducedMotion = useReducedMotionPreference()
  useLayoutEffect(() => {
    value.stop()
    if (!enabled || reducedMotion || duration === 0) value.set(target)
    else animate(value, target, { duration, ease: [.25, .65, .35, 1] })
    return () => value.stop()
  }, [target, enabled, duration, reducedMotion, value])
  return value
}
