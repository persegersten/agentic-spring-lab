ALTER TABLE vehicle ADD COLUMN rocket_ammo INTEGER NOT NULL DEFAULT 1;
ALTER TABLE vehicle ADD CONSTRAINT chk_vehicle_rocket_ammo CHECK (rocket_ammo BETWEEN 0 AND 1);
