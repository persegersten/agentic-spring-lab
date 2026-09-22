ALTER TABLE game_round DROP CONSTRAINT chk_round_phase;
UPDATE game_round SET phase = 'PLANNING' WHERE phase = 'PROGRAMMING';
ALTER TABLE game_round ADD CONSTRAINT chk_round_phase
    CHECK (phase IN ('PLANNING', 'RESOLVING', 'PLAYBACK'));
