-- Triad Cubed player data table
-- This keeps the player-seat data (game ID, user ID, player #) as well
-- as the in-game card data per player (in hand, undrafted)

CREATE TABLE collapsi_players (
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    player_seat INT NOT NULL, -- Seat at table (0-1)
    PRIMARY KEY (game_id, user_id)
);