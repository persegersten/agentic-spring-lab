ALTER TABLE game ADD COLUMN board_spawn_points VARCHAR(20000) NOT NULL DEFAULT '';

ALTER TABLE vehicle ADD COLUMN spawn_x INTEGER NOT NULL DEFAULT 0;
ALTER TABLE vehicle ADD COLUMN spawn_y INTEGER NOT NULL DEFAULT 0;
ALTER TABLE vehicle ADD COLUMN spawn_direction VARCHAR(50) NOT NULL DEFAULT 'SOUTH';
UPDATE vehicle SET spawn_x = CAST(position_x AS INTEGER), spawn_y = CAST(position_y AS INTEGER),
                   spawn_direction = direction;

ALTER TABLE player ADD COLUMN crashes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE player ADD CONSTRAINT chk_player_crashes_nonnegative CHECK (crashes >= 0);

ALTER TABLE game_round ADD COLUMN start_events_payload VARCHAR(30000) NOT NULL DEFAULT '';
