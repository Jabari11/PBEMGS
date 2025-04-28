CREATE TABLE sfe_games (
    game_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_state ENUM('OPEN', 'IN_PROGRESS', 'COMPLETE') NOT NULL DEFAULT 'OPEN',
    num_players INT NOT NULL CHECK (num_players BETWEEN 3 AND 5),
    turn_phase ENUM('ACTION_CHOICE', 'BIDDING_ROUND', 'WAITING') NOT NULL DEFAULT 'WAITING',  -- phase of round
    current_action_userid BIGINT NULL, -- User whose action is due
    current_territory_id INT NULL, -- Territory currently being fought over
    current_round_initiative_seat INT NULL, -- player with the button (first to act)
    tiebreak_flagpole TEXT NOT NULL, -- Comma-separated list of player seats
    deck_action_order TEXT NOT NULL, -- Comma-separated list of action card IDs (remaining draw deck)
    deck_territory_order TEXT NOT NULL, -- Comma-separated list of territory card IDs (remaining draw deck)
    abandoned_territories TEXT NULL, -- Optional, stores lost territories
    last_move_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_reminder_timestamp DATETIME DEFAULT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);