package com.pbemgs.game.rpg.combat.action;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.TargetType;
import com.pbemgs.game.rpg.combat.status.ActionTriggerSE;
import com.pbemgs.game.rpg.model.ResourceType;

import java.util.List;
import java.util.Queue;

/**
 * Action data and processing.  All data is "base" data, and will be affected by SEs, stats, etc.
 */
public abstract class Action {

    protected final String name;
    protected final ActionType type;
    protected final boolean friendly;
    protected final TargetType targetType;
    protected final int splashCount;
    protected final int chanceToHit;
    protected final ActionConditional conditional;

    public Action(String name, ActionType type, boolean friendly, TargetType targetType,
                  int splashCount, int chanceToHit, ActionConditional conditional) {
        this.name = name;
        this.type = type;
        this.friendly = friendly;
        this.targetType = targetType;
        this.splashCount = splashCount;
        this.chanceToHit = chanceToHit;
        this.conditional = conditional;
    }

    // Getters

    public String getName() {
        return name;
    }

    public ActionType getActionType() {
        return type;
    }

    public boolean isActionFriendly() {
        return friendly;
    }

    public Integer getChanceToHit() {
        return chanceToHit;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public int getSplashCount() {
        return splashCount;
    }

    public ActionConditional getActionConditional() {
        return conditional;
    }

    public abstract List<CombatChar> process(CombatChar actor, CombatChar target, boolean isCardAction,
                                             Queue<TriggeredAction> actionQueue, CombatLog logger);

    /**
     * Helper function to process a raw damage packet by applying SEs.  Returns the updated packet.
     */
    public static DamagePacket processDamagePacketMods(final CombatChar actor, final CombatChar target, final DamagePacket dp, CombatLog logger) {
        if (actor != null) {
            DamagePacket afterActor = actor.getStatusEffects().processDamagePacketModifiers(true, dp, logger);
            DamagePacket afterTarget = target.getStatusEffects().processDamagePacketModifiers(false, afterActor, logger);
            afterTarget.finalizeAdjustments();
            return afterTarget;
        }
        DamagePacket afterTarget = target.getStatusEffects().processDamagePacketModifiers(false, dp, logger);
        afterTarget.finalizeAdjustments();
        return afterTarget;
    }

    /**
     * Applies a damagepacket to a character.
     */
    public static void applyDamagePacket(CombatChar target, final DamagePacket dp, CombatLog logger) {
        target.applyDamagePacket(dp);
        logger.log(LogEvent.ATTACK, LogLevel.DEV, "Applying final DP to target: " + dp.toString() +
                " -- Result: " + target.getResourceVal(ResourceType.HP) + "/" + target.getResourceVal(ResourceType.ARMOR));
    }

    /**
     * Checks for triggered actions from a landed attack.  Actor is first, then the target.
     */
    protected void checkDamageTriggers(CombatChar actor, CombatChar target, DamagePacket dp,
                                       boolean isCardAction, Queue<TriggeredAction> actionQueue, CombatLog logger) {
        if (isCardAction) {
            // Actor chases
            actionQueue.addAll(actor.getStatusEffects().getTriggeredActions(ActionTriggerSE.TriggerTypes.ON_HIT,
                    getActionType(), dp.getDamageTypes(), actor.getUnitId(), target.getUnitId(), logger));

            if (actor.getSide() != target.getSide()) {  // target reactions
                actionQueue.addAll(target.getStatusEffects().getTriggeredActions(ActionTriggerSE.TriggerTypes.ON_GET_HIT,
                        getActionType(), dp.getDamageTypes(), target.getUnitId(), actor.getUnitId(), logger));
            }
        }
    }
}
