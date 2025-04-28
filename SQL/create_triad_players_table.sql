-- Triad Cubed player data table
-- This keeps the player-seat data (game ID, user ID, player #) as well
-- as the in-game card data per player (in hand, undrafted)

CREATE TABLE triad_players (
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    player_seat INT NOT NULL, -- Seat at table (0-1)
    cards_in_hand TEXT NOT NULL,  -- Comma-separated card IDs, "" if none
    undrafted_cards TEXT NOT NULL, -- Comma-separated card IDs, "" if none
    subgame_count INT NOT NULL DEFAULT 0,  -- number of subgames won
    PRIMARY KEY (game_id, user_id)
);