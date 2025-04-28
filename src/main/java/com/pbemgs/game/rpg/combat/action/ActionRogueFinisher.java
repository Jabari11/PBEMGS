package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.model.ResourceType;

import java.util.List;
import java.util.Queue;

/**
 * Rogue finishers are coded using a Decorator pattern.
 * This RogueFinisher Action type “wraps” an Action of a different type that can be scaled by Focus points
 * (weapon attack, summon, etc) – this object holds the scaling data, and creates a copy of the embedded action
 * with the correct (scaled) values, executes that, then clears the Focus points on the actor.
 */
public class ActionRogueFinisher extends Action {
    private final Action inner;
    private final List<Integer> scales; // 4 entries: focus 0-3

    public ActionRogueFinisher(Action inner, List<Integer> scales) {
        super(inner.getName(), inner.getActionType(), inner.isActionFriendly(),
                inner.getTargetType(), inner.getSplashCount(),
                inner.getChanceToHit(), inner.getActionConditional());
        this.inner  = inner;
        this.scales = scales;
    }

    @Override
    public List<CombatChar> process(CombatChar actor, CombatChar target,
                                    boolean isCardAction,
                                    Queue<TriggeredAction> q, CombatLog log) {

        int f = actor.getResourceVal(ResourceType.FOCUS);
        int scaleFactor = scales.get(f);

        // clone + scale
        Action scaled = cloneWithScale(inner, scaleFactor);

        List<CombatChar> out = scaled.process(actor, target, isCardAction, q, log);

        actor.clearFocus();
        log.log(LogEvent.ACTION, LogLevel.DEV,
                "-- Finisher consumed focus " + f + " (x" + scaleFactor + "%)");

        return out;
    }

    private Action cloneWithScale(Action base, int scalar) {
        if (base instanceof ActionWeaponAttack wa) {
            return wa.withNewMultiplier(scalar / 100f);
        }
        if (base instanceof ActionSummon su) {
            return su.withSummonCount(scalar);
        }
        // …extend as needed
        throw new IllegalStateException("Finisher cannot scale action " + base);
    }
}
