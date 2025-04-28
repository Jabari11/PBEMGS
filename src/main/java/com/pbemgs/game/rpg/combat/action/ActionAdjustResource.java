package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.model.CharacterResource;
import com.pbemgs.game.rpg.model.ResourceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static com.pbemgs.game.rpg.model.RpgConstants.LEVEL_SCALING;

/**
 * Action that adjusts a resource-mapped amount.
 */
public class ActionAdjustResource extends Action {

    private final ResourceType resource;
    private final int quantity;

    public ActionAdjustResource(String name, boolean friendly, TargetType targetType, int splashCount, int chanceToHit,
                                ResourceType resource, int quantity, ActionConditional condition) {
        super(name, ActionType.ADJUST_RESOURCE, friendly, targetType, splashCount, chanceToHit, condition);
        this.resource = resource;
        this.quantity = quantity;
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        if (resource == ResourceType.FOCUS) {
            target.addFocus(quantity);
        } else {
            throw new IllegalArgumentException("AdjustResource action not defined for type " + resource.toString());
        }
        logger.log(LogEvent.ACTION, LogLevel.DEV, "Resource adjustment for " + target.getName() +
                " " + resource.toString() + " += " + quantity + " -> " + target.getResourceVal(resource));
        return new ArrayList<>();
    }
}
