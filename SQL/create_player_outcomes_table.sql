CREATE TABLE player_outcomes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    game_name VARCHAR(16) NOT NULL,
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    outcome ENUM('WIN', 'LOSS', 'DRAW') NOT NULL,
    place int NULL,       -- placement if > 2P game
    went_first BIT NULL,  -- true if this player went first, for stat tracking
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uniq_player_outcome (game_name, game_id, user_id),
    INDEX idx_game_lookup (game_name, game_id),
    INDEX idx_user_lookup (user_id)
);