CREATE TABLE ataxx_victors (
    game_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    PRIMARY KEY (game_id, player_id),
    FOREIGN KEY (game_id) REFERENCES ataxx_games(game_id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES users(user_id) ON DELETE CASCADE
);