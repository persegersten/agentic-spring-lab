ALTER TABLE game DROP CONSTRAINT chk_game_status;
ALTER TABLE game ADD CONSTRAINT chk_game_status
    CHECK (status IN ('WAITING_FOR_PLAYERS', 'RUNNING', 'FINISHED'));

ALTER TABLE game ADD COLUMN max_players INTEGER NOT NULL DEFAULT 12;
ALTER TABLE game ADD COLUMN join_timeout_seconds INTEGER NOT NULL DEFAULT 300;
ALTER TABLE game ADD COLUMN cards_per_round INTEGER NOT NULL DEFAULT 3;
ALTER TABLE game ADD COLUMN planning_timeout_seconds INTEGER NOT NULL DEFAULT 120;
ALTER TABLE game ADD COLUMN join_deadline TIMESTAMP WITH TIME ZONE NOT NULL
    DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE game ADD CONSTRAINT chk_game_max_players CHECK (max_players > 0);
ALTER TABLE game ADD CONSTRAINT chk_game_join_timeout CHECK (join_timeout_seconds > 0);
ALTER TABLE game ADD CONSTRAINT chk_game_cards_per_round CHECK (cards_per_round BETWEEN 3 AND 10);
ALTER TABLE game ADD CONSTRAINT chk_game_planning_timeout CHECK (planning_timeout_seconds > 0);
ALTER TABLE player ADD CONSTRAINT uq_player_game_name UNIQUE (game_id, name);
