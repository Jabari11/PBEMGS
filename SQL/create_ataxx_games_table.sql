CREATE TABLE ataxx_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    num_players INT NOT NULL,
    user0_id BIGINT NOT NULL, -- User ID for x (game creator)
    user1_id BIGINT NULL,     -- User ID for o (NULL until joined)
    user2_id BIGINT NULL,     -- User ID for * (if 4P)
    user3_id BIGINT NULL,     -- User ID for @ (if 4P)
    turn_order VARCHAR(10) NOT NULL, -- turn ordering, comma separated list of player slots
    user_id_to_move BIGINT NULL,
    board_size INT NOT NULL,
    board_state TEXT NOT NULL, -- Serialized board state
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_reminder_timestamp DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    board_option ENUM('BLANK', 'STANDARD', 'RANDOM') NOT NULL,
    move_history TEXT          -- move history strings for reporting 4P moves
);
