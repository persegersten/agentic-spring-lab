ALTER TABLE player ADD COLUMN shield_consumed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE vehicle DROP CONSTRAINT IF EXISTS chk_vehicle_damage_non_negative;
ALTER TABLE vehicle DROP CONSTRAINT IF EXISTS chk_vehicle_primary_weapon;
ALTER TABLE vehicle DROP CONSTRAINT IF EXISTS chk_vehicle_special_ability;
ALTER TABLE vehicle DROP COLUMN damage;
ALTER TABLE vehicle DROP COLUMN rocket_ammo;
ALTER TABLE vehicle DROP COLUMN primary_weapon;
ALTER TABLE vehicle DROP COLUMN special_ability;

ALTER TABLE player DROP COLUMN score;

ALTER TABLE game DROP COLUMN checkpoint_score;
ALTER TABLE game DROP COLUMN control_point_score;
ALTER TABLE game DROP COLUMN crash_penalty;
ALTER TABLE game DROP COLUMN push_crash_score;
ALTER TABLE game DROP COLUMN weapon_crash_score;

ALTER TABLE game_round DROP COLUMN initial_scores_payload;
