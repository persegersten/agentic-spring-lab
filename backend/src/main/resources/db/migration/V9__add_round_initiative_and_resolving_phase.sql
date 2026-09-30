ALTER TABLE game_round ADD COLUMN initiative_payload VARCHAR(20000) NOT NULL DEFAULT '';

ALTER TABLE game_round DROP CONSTRAINT chk_round_phase;
UPDATE game_round SET phase = 'RESOLVING' WHERE phase IN ('MOVEMENT_ACTIONS', 'BOARD_EFFECTS');
ALTER TABLE game_round ADD CONSTRAINT chk_round_phase
    CHECK (phase IN ('PLANNING', 'RESOLVING', 'PLAYBACK'));
