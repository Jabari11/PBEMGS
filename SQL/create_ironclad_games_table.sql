CREATE TABLE ironclad_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    white_user_id BIGINT NOT NULL,
    black_user_id BIGINT NULL,
    user_id_to_move BIGINT NULL,
    current_move_phase ENUM('OPEN_MOVE', 'FORCED_ROBOT', 'FORCED_STONE'),
    robot_board_state TEXT NOT NULL,  -- Serialized robot board state
    stone_board_state TEXT NOT NULL,  -- Serialized stone board state
    last_stone_moved VARCHAR(4) NULL, -- location of last stone moved
    half_move_text TEXT NULL,         -- partial-text storage if halves commanded separately
    forced_move_option ENUM('ENEMY', 'SELF'),
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_reminder_timestamp DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);