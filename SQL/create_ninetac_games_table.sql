CREATE TABLE ninetac_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Primary Key, auto-incrementing
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE', 'ABANDONED') NOT NULL DEFAULT 'OPEN', -- Game state
    x_player_id BIGINT NOT NULL, -- Player ID for X (game creator)
    o_player_id BIGINT NULL, -- Player ID for O (NULL until joined)
    player_id_to_move BIGINT NULL, -- Player ID for the current turn
    board_state VARCHAR(250) NOT NULL, -- Serialized board state
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, -- Timestamp of last move
    victor_player_id BIGINT NULL, -- Winner's ID: NULL = ongoing, -1 = draw
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,  -- Record creation timestamp
    starting_player_id BIGINT,   -- ID of first player, for stat tracking    
    rules_version INT NOT NULL,
    board_option ENUM('DEFAULT_27') NOT NULL,
    last_reminder_timestamp DATETIME DEFAUlT NULL
);