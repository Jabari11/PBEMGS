-- Individual game result tracking - for stat-keeping only
CREATE TABLE triad_game_victors (
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    subgame_id INT NOT NULL,
    first_player_won BIT NOT NULL,
    PRIMARY KEY (game_id, subgame_id)
);