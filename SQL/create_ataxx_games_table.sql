CREATE TABLE ataxx_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Primary Key, auto-incrementing
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE', 'ABANDONED') NOT NULL DEFAULT 'OPEN', -- Game state
    num_players INT NOT NULL,    -- number of players
    player0_id BIGINT NOT NULL, -- Player ID for x (game creator)
    player1_id BIGINT NULL,     -- Player ID for o (NULL until joined)
    player2_id BIGINT NULL,     -- Player ID for * (if 4P)
    player3_id BIGINT NULL,     -- Player ID for $ (if 4P)
    turn_order VARCHAR(10) NOT NULL, -- turn ordering, comma separate list of ints
    player_id_to_move BIGINT NULL,   -- Player ID for the current turn
    board_size INT NOT NULL,         -- board size (square)
    board_state VARCHAR(150) NOT NULL, -- Serialized board state (max 11x11)
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, -- Timestamp of last move
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,  -- Record creation timestamp
    board_option ENUM('BLANK', 'STANDARD', 'RANDOM') NOT NULL,
    move_history VARCHAR(500)        -- move history strings for reporting 4P moves
);
