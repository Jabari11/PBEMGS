package com.pbemgs.game.rpg.combat.action;

/**
 * Action types (separate logic for each)
 */
public enum ActionType {
    ALL,  // for use on modification SEs, not individual actions!!
    WEAPON_ATTACK,
    NONWEAPON_ATTACK,
    ARMOR,
    HEALING,
    STATUS_APPLY,
    SUMMON,
    DISPEL, ADJUST_RESOURCE,
}
