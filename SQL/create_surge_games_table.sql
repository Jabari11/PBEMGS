CREATE TABLE surge_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    num_players INT NOT NULL,
    command_limit INT NOT NULL,  -- command limit option
    game_timezone ENUM('ET', 'GMT', 'TK', 'SH'),
    ticks_per_day INT NOT NULL,
    board_rows INT NOT NULL,
    board_cols INT NOT NULL,
    board_state TEXT NOT NULL,  -- Serialized map state
    geyser_state TEXT NOT NULL, -- Serialized geyser locations/values
    pressure_state TEXT NOT NULL, -- Serialized pressure data
    momentum_state TEXT NOT NULL, -- Serialized momentum data
    last_time_step TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);