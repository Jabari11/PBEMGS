CREATE TABLE gomoku_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Primary Key, auto-incrementing
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN', -- Game state
    x_player_id BIGINT NOT NULL, -- Player ID for X
    o_player_id BIGINT NULL, -- Player ID for O
    player_id_to_move BIGINT NULL, -- Player ID for the current action
    board_size INT NOT NULL,  -- board size (square)
    board_state TEXT NOT NULL, -- Serialized board state
    swap2_state ENUM('AWAITING_INITIAL_PLACEMENT', 'AWAITING_TSP_CHOICE', 'AWAITING_TFP_SWAP', 'GAMEPLAY'),
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, -- Timestamp of last move
    last_reminder_timestamp DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,  -- Record creation timestamp
    victor_player_id BIGINT NULL -- Winner's ID: NULL = ongoing
);