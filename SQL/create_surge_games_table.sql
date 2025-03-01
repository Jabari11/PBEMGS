CREATE TABLE surge_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Primary Key, auto-incrementing
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE', 'ABANDONED') NOT NULL DEFAULT 'OPEN', -- Game state
    num_players INT NOT NULL,    -- number of players
    command_limit INT NOT NULL,
    game_timezone ENUM('ET', 'GMT', 'TK', 'SH'),
    ticks_per_day INT NOT NULL,
    board_rows INT NOT NULL,
    board_cols INT NOT NULL,
    board_state TEXT NOT NULL,  -- Stores board state (formatted like before)
    geyser_state TEXT NOT NULL, -- Stores geyser state (up to 30 geysers)
    pressure_state TEXT NOT NULL, -- Stores pressure data
    momentum_state TEXT NOT NULL, -- Stores momentum data
    last_time_step TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);