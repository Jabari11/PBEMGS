-- Game table for Collapsi
CREATE TABLE collapsi_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    current_action_userid BIGINT NULL, -- User whose action is due
    board_state TEXT NULL,  -- serialized board state
    first_turn_user_id BIGINT NULL,   -- user id of player that went first
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_reminder_timestamp DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);