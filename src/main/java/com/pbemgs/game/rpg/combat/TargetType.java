package com.pbemgs.game.rpg.combat;

/**
 * Possible types of action targeting.  PRIMARY means the locked-on (primary) target.
 */
public enum TargetType {
    NONE,
    SAME,    // same targets as selected for previous action
    LINKED,  // same targets as what is hit by previous action
    REACTION, // target is hard-coded in the action queue (chase or counter)
    SELF,
    SELF_AFTER_HIT,  // targets self IF something was hit on the previous action
    RANDOM_ENEMY,       // Threat weighted
    RANDOM_ENEMY_TRUE,  // Non threat weighted
    ALL_ENEMIES,
    LOWEST_HPP_ENEMY,
    RANDOM_ALLY,
    ALL_ALLIES,
    LOWEST_HPP_ALLY,
}
