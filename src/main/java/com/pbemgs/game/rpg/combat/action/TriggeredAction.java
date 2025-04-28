package com.pbemgs.game.rpg.combat.action;

/**
 * A Triggered Action in the queue needs to know the action itself as well as a forced target unit ID
 * if there is one.  i.e.:
 * - a counterattack needs to hit the attacker (not a random enemy), so the target ID will be set
 * - a chase will usually be of type LINKED, so forcedTargetId will be null.
 */
public record TriggeredAction(Action action, int actingUnitId, boolean isCardAction, Integer forcedTargetId) {
}
