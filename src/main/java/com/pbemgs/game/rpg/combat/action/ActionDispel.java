package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.DamageSource;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.StatusEffect;
import com.pbemgs.game.rpg.model.WeaponType;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * A dispel action.  Side (beneficial or not), class, and count.
 */
public class ActionDispel extends Action {

    private final boolean clearsBeneficial;
    private final int count;
    private final Set<StatusEffect.Classification> classes;

    public ActionDispel(String name, TargetType targetType, int splashCount, int chanceToHit, boolean clearsBeneficial,
                        int count, Set<StatusEffect.Classification> classes, ActionConditional condition) {
        super(name, ActionType.DISPEL, !clearsBeneficial, targetType, splashCount, chanceToHit, condition);
        this.clearsBeneficial = clearsBeneficial;
        this.count = count;
        this.classes = classes;
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        logger.log(LogEvent.ACTION, LogLevel.DEV, "Dispel - " + actor.getName() + " -> " + target.getName());
        target.getStatusEffects().processDispel(clearsBeneficial, count, classes, logger);

        return new ArrayList<>();
    }

}
