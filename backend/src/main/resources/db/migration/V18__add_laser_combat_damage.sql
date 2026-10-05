ALTER TABLE vehicle ADD COLUMN damage INTEGER NOT NULL DEFAULT 0;
ALTER TABLE vehicle ADD CONSTRAINT chk_vehicle_damage_non_negative CHECK (damage >= 0);
ALTER TABLE game ADD COLUMN weapon_crash_score INTEGER NOT NULL DEFAULT 1;
