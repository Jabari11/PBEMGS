-- Skirmish for Earth player data table
-- This keeps both the player-seating data (game ID, user ID, seat #) as well
-- as the in-game status data per player (cards in hand, reserve, etc)

CREATE TABLE sfe_players (
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    player_seat INT NOT NULL, -- Seat at table (0..4)
    ships_available INT NOT NULL, -- Reserve bidding pool
    hand TEXT NOT NULL, -- Comma-separated list of action card IDs
    played_card_id INT NULL, -- Card currently face-down
    bid_amount INT NULL, -- Current bid amount in the round
    current_turn_actions TEXT NOT NULL, -- list of actions (BID/PASS)
    territories TEXT NOT NULL, -- Territories won by this player
    PRIMARY KEY (game_id, user_id)
);