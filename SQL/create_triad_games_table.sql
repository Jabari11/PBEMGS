-- Game table for Triad Cubed.
-- Data for card state of each player will be kept in the player table.
CREATE TABLE triad_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    game_phase ENUM('HAND_SELECTION', 'GAMEPLAY', 'WAITING') NOT NULL DEFAULT 'WAITING',  -- phase of game
    current_action_userid BIGINT NULL, -- User whose action is due
    board_state TEXT NULL,  -- serialized board state
    option_faceup BIT NOT NULL,    -- true = hand open
    option_elemental BIT NOT NULL, -- true = elemental squares active
    option_samerule BIT NOT NULL,  -- true = SAME rule in effect
    option_plusrule BIT NOT NULL,  -- true = PLUS rule in effect
    current_subgame INT NOT NULL DEFAULT 1,  -- game # in match
    first_turn_user_id BIGINT NULL,   -- first/third game first player
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_reminder_timestamp DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);