ALTER TABLE game RENAME COLUMN cards_per_round TO program_size;
ALTER TABLE game DROP CONSTRAINT chk_game_cards_per_round;
ALTER TABLE game ADD CONSTRAINT chk_game_program_size CHECK (program_size BETWEEN 1 AND 5);
ALTER TABLE game_round ADD COLUMN planning_deadline TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
