CREATE TABLE gomoku_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    x_user_id BIGINT NOT NULL,
    o_user_id BIGINT NULL,
    user_id_to_move BIGINT NULL,
    board_size INT NOT NULL,
    board_state TEXT NOT NULL, -- Serialized board state
    swap2_state ENUM('AWAITING_INITIAL_PLACEMENT', 'AWAITING_TSP_CHOICE', 'AWAITING_TFP_SWAP', 'GAMEPLAY'),
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_reminder_timestamp DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);