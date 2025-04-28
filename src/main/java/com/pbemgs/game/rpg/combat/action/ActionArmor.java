package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.model.ResourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static com.pbemgs.game.rpg.model.RpgConstants.LEVEL_SCALING;

/**
 * Action that adds (or removes?) armor directly from a character.
 */
public class ActionArmor extends Action {

    private final float baseQuantity;

    public ActionArmor(String name, boolean friendly, TargetType targetType, int splashCount, int chanceToHit,
                       float baseQuantity, ActionConditional condition) {
        super(name, ActionType.ARMOR, friendly, targetType, splashCount, chanceToHit, condition);
        this.baseQuantity = baseQuantity;
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        int update = Math.round(baseQuantity + (1.0f + LEVEL_SCALING * actor.getLevel()));
        // TODO: SEs for armor adjustments?
        target.addArmor(update);
        logger.log(LogEvent.ATTACK, LogLevel.DEBUG, "Armor adjustment for " + target.getName() +
                " - qty: " + update + " - result: " + target.getResourceVal(ResourceType.ARMOR));
        return new ArrayList<>();
    }
}
