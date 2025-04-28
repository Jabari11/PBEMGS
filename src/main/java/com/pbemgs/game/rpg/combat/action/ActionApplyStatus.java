package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.StatusEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

public abstract class ActionApplyStatus extends Action {

    protected final String seName;
    protected final StatusEffect.Classification classification;
    protected final boolean appliesAsActor;
    protected final int duration;
    protected final int charges;

    public ActionApplyStatus(String name, TargetType targetType, boolean isFriendly, int splashCount, int chanceToHit,
                             String seName, StatusEffect.Classification classification, boolean appliesAsActor,
                             int duration, int charges, ActionConditional condition) {
        super(name, ActionType.STATUS_APPLY, isFriendly, targetType, splashCount, chanceToHit, condition);

        this.seName = seName;
        this.classification = classification;
        this.appliesAsActor = appliesAsActor;
        this.duration = duration;
        this.charges = charges;
    }

    public List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                    Queue<TriggeredAction> actionQueue, CombatLog logger) {
        StatusEffect se = createStatusEffect(actor);
        se.setHolder(target);
        target.getStatusEffects().attachStatusEffect(se);
        logger.log(LogEvent.SE, LogLevel.INFO, actor.getName() + " applied SE " + se.getName() + " to " + target.getName());
        return new ArrayList<>();
    }

    public abstract StatusEffect createStatusEffect(CombatChar actor);
}
