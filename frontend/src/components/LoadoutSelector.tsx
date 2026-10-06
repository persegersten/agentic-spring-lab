import type { PrimaryWeapon, SpecialAbility } from '../types/game'

const weapons: { value: PrimaryWeapon; description: string }[] = [
  { value: 'LASER', description: 'Range 6 · 1 damage · unlimited' },
  { value: 'REPULSOR', description: 'Range 3 · pushes target 1 cell' },
  { value: 'ROCKET', description: 'Range 5 · 2 damage · one shot per match' },
]
const abilities: { value: SpecialAbility; label: string; description: string }[] = [
  { value: 'TURBO', label: 'TURBO', description: 'Extra forward step' },
  { value: 'SHIELD', label: 'SHIELD', description: 'Blocks 1 weapon damage in chosen register' },
  { value: 'SIDE_STEP', label: 'SIDE STEP', description: 'Move one cell sideways' },
  { value: 'ANCHOR', label: 'ANCHOR', description: 'Cannot be pushed during chosen register' },
]

export function LoadoutSelector({ weapon, ability, disabled, onChange }: {
  weapon: PrimaryWeapon
  ability: SpecialAbility
  disabled: boolean
  onChange: (weapon: PrimaryWeapon, ability: SpecialAbility) => Promise<void>
}) {
  return <div className="loadout-selector" data-testid="loadout-selector">
    <label>Primary weapon
      <select data-testid="loadout-weapon" value={weapon} disabled={disabled}
        onChange={event => void onChange(event.target.value as PrimaryWeapon, ability)}>
        {weapons.map(option => <option key={option.value} value={option.value}>{option.value}</option>)}
      </select>
    </label>
    <p>{weapons.find(option => option.value === weapon)?.description}</p>
    <label>Special ability
      <select data-testid="loadout-ability" value={ability} disabled={disabled}
        onChange={event => void onChange(weapon, event.target.value as SpecialAbility)}>
        {abilities.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    </label>
    <p>{abilities.find(option => option.value === ability)?.description}</p>
  </div>
}
