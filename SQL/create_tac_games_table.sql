CREATE TABLE tac_games (
    game_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    board_state VARCHAR(9) NOT NULL,
    game_state ENUM('IN_PROGRESS', 'COMPLETE') NOT NULL,
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,  -- Timestamp of last move
    last_reminder_timestamp DATETIME DEFAULT NULL
);
