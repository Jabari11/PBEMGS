CREATE TABLE ninetac_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    x_user_id BIGINT NOT NULL, -- user ID for X (game creator)
    o_user_id BIGINT NULL, -- Player ID for O (NULL until joined)
    user_id_to_move BIGINT NULL, -- Player ID for the current turn
    board_state VARCHAR(250) NOT NULL, -- Serialized board state
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, -- Timestamp of last move
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,  -- Record creation timestamp
    starting_user_id BIGINT,   -- ID of first player, for stat tracking
    board_option ENUM('DEFAULT_27') NOT NULL,
    last_reminder_timestamp DATETIME DEFAUlT NULL
);