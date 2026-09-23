ALTER TABLE game ADD COLUMN board_walls VARCHAR(20000) NOT NULL DEFAULT '';
ALTER TABLE vehicle ADD COLUMN damage INTEGER NOT NULL DEFAULT 0;
ALTER TABLE vehicle ADD CONSTRAINT chk_vehicle_damage_non_negative CHECK (damage >= 0);
