package com.pbemgs.game.rpg.combat.status;

import com.pbemgs.game.rpg.combat.CombatChar;
import com.pbemgs.game.rpg.combat.CombatLog;
import com.pbemgs.game.rpg.combat.DamagePacket;
import com.pbemgs.game.rpg.combat.DamageType;
import com.pbemgs.game.rpg.combat.LogEvent;
import com.pbemgs.game.rpg.combat.LogLevel;
import com.pbemgs.game.rpg.combat.action.ActionType;
import com.pbemgs.game.rpg.combat.action.TriggeredAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static com.pbemgs.game.rpg.model.RpgConstants.INFINITE;

/**
 * A collection of Status Effects for a single CombatChar.
 * This holds a list of the different effect types, and methods that the CombatEngine uses
 * to apply effects to objects or events, as well as handle a "time tick" to reduce
 * durations across the board.
 */
public class StatusContainer {
    private final List<PeriodicDamageSE> periodicDamageSEs;
    private final List<DamageModificationSE> damageModSEs;
    private final List<AggroModificationSE> aggroModSEs;
    private final List<ChanceToHitModificationSE> toHitModSEs;
    private final List<ActionPreventionSE> stunChanceSEs;
    private final List<ActionTriggerSE> actionTriggerSEs;

    private final Set<StatusEffect> chargeUsage;  // SEs that need to have a charge removed at the end of Card.

    public StatusContainer() {
        periodicDamageSEs = new ArrayList<>();
        damageModSEs = new ArrayList<>();
        aggroModSEs = new ArrayList<>();
        toHitModSEs = new ArrayList<>();
        stunChanceSEs = new ArrayList<>();
        actionTriggerSEs = new ArrayList<>();

        chargeUsage = new HashSet<>();
    }

    /**
     * Adds the status effect to the given list.
     * Checks for stackability, replacing the existing SE in the list if needed.
     */
    private <T extends StatusEffect> void addStatusEffect(List<T> seList, T se) {
        Optional<T> existing = seList.stream()
                .filter(e -> e.getSourceId().equals(se.getSourceId()) && e.getName().equals(se.getName()))
                .findFirst();
        existing.ifPresent(seList::remove);
        seList.add(se);
    }

    /**
     * Ticks duration down for the given list.
     * Removes expired effects.
     */
    private <T extends StatusEffect> void tickAndClean(List<T> seList, CombatChar self, CombatLog logger) {
        Iterator<T> iterator = seList.iterator();
        while (iterator.hasNext()) {
            T se = iterator.next();
            boolean expired = se.tick() || !se.isActive();
            if (expired) {
                logger.log(LogEvent.SE_CHECK, LogLevel.INFO, "Status " + se.getName() + " has faded from " + self.getName() + ".");
                iterator.remove();
            }
        }
    }

    /**
     * tick duration on appropriate lists after character's turn
     */
    public void tickAfterCard(CombatChar self, CombatLog logger) {
        tickAndClean(stunChanceSEs, self, logger);
    }

    /**
     * tick duration on appropriate lists at end of round
     */
    public void tickEndOfRound(CombatChar self, CombatLog logger) {
        tickAndClean(periodicDamageSEs, self, logger);
        tickAndClean(aggroModSEs, self, logger);
        tickAndClean(damageModSEs, self, logger);
        tickAndClean(toHitModSEs, self, logger);
        tickAndClean(actionTriggerSEs, self, logger);
    }

    /**
     * Remove a use from all SEs that have reported being used during this Card.
     */
    public void tickChargeUsage(CombatChar self, CombatLog logger) {
        for (StatusEffect se : chargeUsage) {
            logger.log(LogEvent.SE_CHECK, LogLevel.DEV, "- Removing a charge of " + se.getName() + " from " + self.getName());
            se.removeUse();
        }
        chargeUsage.clear();
    }

    public void reportChargeUsage(StatusEffect se) {
        chargeUsage.add(se);
    }

    public void attachStatusEffect(StatusEffect se) {
        if (se instanceof PeriodicDamageSE pdse) {
            addStatusEffect(periodicDamageSEs, pdse);
        } else if (se instanceof DamageModificationSE dmodse) {
            addStatusEffect(damageModSEs, dmodse);
        } else if (se instanceof AggroModificationSE aggroSe) {
            addStatusEffect(aggroModSEs, aggroSe);
        } else if (se instanceof ChanceToHitModificationSE tohitse) {
            addStatusEffect(toHitModSEs, tohitse);
        } else if (se instanceof ActionPreventionSE apse) {
            addStatusEffect(stunChanceSEs, apse);
        } else if (se instanceof ActionTriggerSE rse) {
            addStatusEffect(actionTriggerSEs, rse);
        } else {
            throw new IllegalArgumentException("Unknown StatusEffect type: " + se.getClass().getSimpleName());
        }
    }

    // Processing

    public void processPeriodicDamageSEs(CombatChar self, CombatLog logger) {
        for (PeriodicDamageSE se : periodicDamageSEs) {
            se.applyPeriodicSE(self, logger);
        }
    }

    /**
     * Calculates and returns an updated DP per status effects.
     */
    public DamagePacket processDamagePacketModifiers(boolean isActor, DamagePacket dp, CombatLog logger) {
        DamagePacket currentDP = dp.copy();
        for (DamageModificationSE se : damageModSEs) {
            currentDP = se.applyDPModifier(currentDP, isActor, logger);
        }
        return currentDP;
    }

    public double computeAggroWeightMultiplier(CombatLog logger) {
        double result = 1.0;
        for (AggroModificationSE se : aggroModSEs) {
            result *= se.getAggroMultiplier(logger);
        }
        return result;
    }

    public int computeToHitModifier(ActionType actionType, boolean isActor, CombatLog logger) {
        int result = 0;
        for (ChanceToHitModificationSE se : toHitModSEs) {
            result += se.applyToHitModifier(actionType, isActor, logger);
        }
        return result;
    }

    public boolean isActionPrevented(Random rng, CombatLog logger) {
        for (ActionPreventionSE se : stunChanceSEs) {
            int roll = rng.nextInt(100) + 1;
            logger.log(LogEvent.SE, LogLevel.DEBUG, "- Checking Stun Chance of " + se.getStunChance() + " from: " + se.getName() + " - rolled: " + roll);
            if (roll <= se.getStunChance()) {
                se.removeUse();  // direct usage removal here
                return true;
            }
        }
        return false;
    }

    public List<TriggeredAction> getTriggeredActions(ActionTriggerSE.TriggerTypes type, ActionType actionType, Set<DamageType> damageTypes,
                                                     int thisCharUnitId, int otherCharUnitId, CombatLog logger) {
        List<TriggeredAction> reacts = new ArrayList<>();

        for (ActionTriggerSE se : actionTriggerSEs) {
            if (se.isActive()) {
                reacts.addAll(se.checkReaction(type, actionType, damageTypes, thisCharUnitId, otherCharUnitId, logger));
            }
        }
        return reacts;
    }

    public void removeStatusEffect(StatusEffect se) {
        periodicDamageSEs.remove(se);
        damageModSEs.remove(se);
        aggroModSEs.remove(se);
        toHitModSEs.remove(se);
        stunChanceSEs.remove(se);
        actionTriggerSEs.remove(se);
    }

    public void processDispel(boolean clearsBeneficial, int count, Set<StatusEffect.Classification> classes, CombatLog logger) {
        List<StatusEffect> allEffects = getAllEffects();

        Random rng = new Random();

        List<StatusEffect> eligible = new ArrayList<>();
        for (StatusEffect se : allEffects) {
            if (se.isBeneficial() == clearsBeneficial &&
                    (classes == null || classes.isEmpty() || classes.contains(se.getClassification()))) {
                eligible.add(se);
            }
        }

        int toRemoveCount = (count == INFINITE) ? eligible.size() : Math.min(count, eligible.size());
        if (toRemoveCount > 0) {
            Collections.shuffle(eligible, rng);
            List<StatusEffect> toRemove = eligible.subList(0, toRemoveCount);
            for (StatusEffect se : toRemove) {
                removeStatusEffect(se);
                logger.log(LogEvent.SE, LogLevel.INFO, "- Dispelled " + se.getName());
            }
        }
    }

    private List<StatusEffect> getAllEffects() {
        List<StatusEffect> allEffects = new ArrayList<>();
        allEffects.addAll(periodicDamageSEs);
        allEffects.addAll(damageModSEs);
        allEffects.addAll(aggroModSEs);
        allEffects.addAll(toHitModSEs);
        allEffects.addAll(stunChanceSEs);
        allEffects.addAll(actionTriggerSEs);
        return allEffects;
    }

    @Override
    public String toString() {
        return getAllEffects().stream()
                .map(StatusEffect::getDisplay)
                .collect(Collectors.joining(", ", "-- Status Effects Active: ", ""));
    }

}
