ALTER TABLE vehicle ADD COLUMN primary_weapon VARCHAR(32) NOT NULL DEFAULT 'LASER';
ALTER TABLE vehicle ADD COLUMN special_ability VARCHAR(32) NOT NULL DEFAULT 'SHIELD';
ALTER TABLE vehicle ADD CONSTRAINT chk_vehicle_primary_weapon CHECK (primary_weapon IN ('LASER', 'REPULSOR', 'ROCKET'));
ALTER TABLE vehicle ADD CONSTRAINT chk_vehicle_special_ability CHECK (special_ability IN ('TURBO', 'SHIELD', 'SIDE_STEP', 'ANCHOR'));
