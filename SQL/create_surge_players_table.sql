CREATE TABLE surge_players (
    user_id BIGINT NOT NULL,
    game_id BIGINT NOT NULL,
    player_number INT NOT NULL,
    status ENUM('ACTIVE', 'ELIMINATED') NOT NULL DEFAULT 'ACTIVE',
    current_command VARCHAR(255) DEFAULT NULL, -- Stores serialized command string (e.g., "OA12E,CA10S")
    last_command_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, game_id)
);
