ALTER TABLE game RENAME COLUMN board_walls TO board_edge_walls;

-- A legacy occupied wall cell does not identify which of its four edges was blocked.
-- Discard those ambiguous values rather than silently assigning different game rules.
UPDATE game SET board_edge_walls = '';
