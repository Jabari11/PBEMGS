CREATE TABLE loa_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Primary Key, auto-incrementing
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN', -- Game state
    x_player_id BIGINT NOT NULL, -- Player ID for X (game creator)
    o_player_id BIGINT NULL, -- Player ID for O (NULL until joined)
    player_id_to_move BIGINT NULL, -- Player ID for the current action
    board_state TEXT NOT NULL, -- Serialized board state
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, -- Timestamp of last move
    last_reminder_timestamp DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,  -- Record creation timestamp
    starting_player_id BIGINT,   -- ID of first player, for stat tracking
    victor_player_id BIGINT NULL -- Winner's ID: NULL = ongoing
);