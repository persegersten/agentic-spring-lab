import type { RoundEvent, Vehicle } from '../types/game'

/** Apply confirmed events only; this contains no movement or targeting rules. */
export function playbackVehicles(initial: Vehicle[], events: RoundEvent[]) {
  const vehicles = initial.filter(vehicle => vehicle.status !== 'CRASHED').map(vehicle => ({ ...vehicle }))
  for (const event of events) {
    const index = vehicles.findIndex(vehicle => vehicle.id === event.vehicleId)
    if (index < 0) continue
    if (['CRASH', 'CONVEYOR_CRASH'].includes(event.type)) {
      vehicles.splice(index, 1)
    } else if (['MOVE', 'TURN', 'RAM', 'PUSH', 'CONVEYOR_MOVE', 'CONVEYOR_RAM', 'CONVEYOR_PUSH', 'ROTATOR_TURN', 'VEHICLE_RESPAWNED'].includes(event.type)) {
      Object.assign(vehicles[index], { x: event.newPosition.x, y: event.newPosition.y, direction: event.newDirection })
    }
  }
  return vehicles
}
