package com.pbemgs.game.rpg.combat;

import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.model.ResourceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class TargetingEngine {
    private final Map<Integer, CombatChar> charsById;
    private final Random rng;
    private final CombatLog combatLog;

    public TargetingEngine(Map<Integer, CombatChar> charsById, Random rng, CombatLog combatLog) {
        this.charsById = charsById;
        this.rng = rng;
        this.combatLog = combatLog;
    }

    /**
     * Computes threat weights for specified characters.
     * Returns a map of unitId to threat weight (default 100.0, modified by status effects).
     */
    private Map<Integer, Double> computeThreatWeights(Set<Integer> unitIds) {
        Map<Integer, Double> threatWeights = new HashMap<>();
        for (Integer unitId : unitIds) {
            CombatChar ch = charsById.get(unitId);
            if (ch != null && ch.isAlive()) {
                // Default threat is 100, modified by status effects
                double weight = Math.max(100.0d * ch.getStatusEffects().computeAggroWeightMultiplier(combatLog), 0.0d);
                threatWeights.put(ch.getUnitId(), weight);
            }
        }
        return threatWeights;
    }

    public Set<Integer> selectTargets(CombatChar actor, Action action) {
        Set<Integer> targets = new HashSet<>();
        Map<Integer, Double> threatWeights = new HashMap<>();

        // Determine potential targets based on action's friendliness
        Set<Integer> potentialTargets = new HashSet<>();
        if (!action.isActionFriendly()) {
            // Enemy targeting: compute threat weights for enemies only
            potentialTargets = charsById.values().stream()
                    .filter(ch -> ch.getSide() != actor.getSide() && ch.isAlive())
                    .map(CombatChar::getUnitId)
                    .collect(Collectors.toSet());
            threatWeights = computeThreatWeights(potentialTargets);
        } else {
            // Ally targeting: no threat weights needed
            potentialTargets = charsById.values().stream()
                    .filter(ch -> ch.getSide() == actor.getSide() && ch.isAlive())
                    .map(CombatChar::getUnitId)
                    .collect(Collectors.toSet());
        }

        switch (action.getTargetType()) {
            case SELF:
                targets.add(actor.getUnitId());
                break;
            case RANDOM_ENEMY:
                Integer randomEnemyId = findWeightedEnemyTarget(actor, threatWeights, potentialTargets);
                if (randomEnemyId != null) targets.add(randomEnemyId);
                break;
            case RANDOM_ENEMY_TRUE:
                Integer randomTrueEnemyId = findRandomEnemyTrueTarget(actor, threatWeights, potentialTargets);
                if (randomTrueEnemyId != null) targets.add(randomTrueEnemyId);
                break;
            case RANDOM_ALLY:
                Integer randomAllyId = findRandomAllyTarget(actor, potentialTargets);
                if (randomAllyId != null) targets.add(randomAllyId);
                break;
            case ALL_ALLIES:
                targets.addAll(findAllTargets(actor, true, threatWeights, potentialTargets));
                break;
            case ALL_ENEMIES:
                targets.addAll(findAllTargets(actor, false, threatWeights, potentialTargets));
                break;
            case LOWEST_HPP_ALLY:
                Integer lowestHpAllyId = findLowestHpTarget(actor, true, threatWeights, potentialTargets);
                if (lowestHpAllyId != null) targets.add(lowestHpAllyId);
                break;
            case LOWEST_HPP_ENEMY:
                Integer lowestHpEnemyId = findLowestHpTarget(actor, false, threatWeights, potentialTargets);
                if (lowestHpEnemyId != null) targets.add(lowestHpEnemyId);
                break;
        }

        if (!targets.isEmpty() && action.getSplashCount() > 0) {
            addSplashTargets(actor, action, targets, threatWeights, potentialTargets);
        }

        return targets;
    }

    private Integer findWeightedEnemyTarget(CombatChar actor, Map<Integer, Double> threatWeights, Set<Integer> potentialTargets) {
        List<CombatChar> candidates = potentialTargets.stream()
                .map(charsById::get)
                .filter(ch -> threatWeights.getOrDefault(ch.getUnitId(), 0.0d) > 0.0d)
                .toList();

        if (candidates.isEmpty()) return null;

        List<Double> weights = candidates.stream()
                .map(ch -> threatWeights.get(ch.getUnitId()))
                .toList();

        double totalWeight = weights.stream().mapToDouble(Double::doubleValue).sum();

        StringBuilder targetLog = new StringBuilder("-- finding weighted target - ");
        for (CombatChar ch : candidates) {
            targetLog.append(ch.getName()).append(" (").append(threatWeights.get(ch.getUnitId())).append(") ");
        }
        combatLog.log(LogEvent.ATTACK, LogLevel.DEBUG, targetLog.toString());

        double roll = rng.nextDouble() * totalWeight;
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) {
                return candidates.get(i).getUnitId();
            }
        }
        return candidates.get(candidates.size() - 1).getUnitId(); // Fallback
    }

    private Integer findRandomEnemyTrueTarget(CombatChar actor, Map<Integer, Double> threatWeights, Set<Integer> potentialTargets) {
        List<Integer> enemies = potentialTargets.stream()
                .filter(id -> threatWeights.getOrDefault(id, 0.0d) > 0.0d)
                .toList();

        if (enemies.isEmpty()) return null;
        return enemies.get(rng.nextInt(enemies.size()));
    }

    private Integer findRandomAllyTarget(CombatChar actor, Set<Integer> potentialTargets) {
        List<Integer> allies = new ArrayList<>(potentialTargets);
        if (allies.isEmpty()) return null;
        return allies.get(rng.nextInt(allies.size()));
    }

    private Set<Integer> findAllTargets(CombatChar actor, boolean chooseAllies, Map<Integer, Double> threatWeights, Set<Integer> potentialTargets) {
        Set<Integer> targets = new HashSet<>();
        boolean hasValidTarget = false;

        for (Integer unitId : potentialTargets) {
            CombatChar ch = charsById.get(unitId);
            if (ch != null && ch.isAlive()) {
                if (!chooseAllies) {
                    // For enemies, check if this character has >0 threat
                    if (threatWeights.getOrDefault(ch.getUnitId(), 0.0d) > 0.0d) {
                        hasValidTarget = true;
                    }
                } else {
                    // For allies, all alive characters are valid
                    hasValidTarget = true;
                }

                // sanity check - if target side mismatches actor side and ally-choice side, setup is wrong.
                if ((actor.getSide() == ch.getSide()) != chooseAllies) {
                    throw new IllegalArgumentException("Invalid isFriendly setup for all-target selection!");
                }
                targets.add(ch.getUnitId());
            }
        }

        if (!chooseAllies && !hasValidTarget) {
            combatLog.log(LogEvent.TARGET_SELECT, LogLevel.DEBUG, "-- No valid AoE targets (all enemies <=0 threat)");
            return new HashSet<>();
        }
        return targets;
    }

    private Integer findLowestHpTarget(CombatChar actor, boolean chooseAlly, Map<Integer, Double> threatWeights, Set<Integer> potentialTargets) {
        return potentialTargets.stream()
                .map(charsById::get)
                .filter(ch -> chooseAlly || threatWeights.getOrDefault(ch.getUnitId(), 0.0d) > 0.0d)
                .min(Comparator.comparingDouble(ch -> ch.getResourcePct(ResourceType.HP)))
                .map(CombatChar::getUnitId)
                .orElse(null);
    }

    private void addSplashTargets(CombatChar actor, Action action, Set<Integer> targets, Map<Integer, Double> threatWeights, Set<Integer> potentialTargets) {
        Set<Integer> pool = new HashSet<>(potentialTargets);
        pool.removeAll(targets);

        if (!action.isActionFriendly()) {
            pool.removeIf(id -> threatWeights.getOrDefault(id, 0.0d) <= 0.0d);
        }

        combatLog.log(LogEvent.TARGET_SELECT, LogLevel.DEV, "-- Choosing splash targets from: " + pool.toString());

        int remaining = action.getSplashCount();
        while (remaining > 0 && !pool.isEmpty()) {
            List<Integer> temp = new ArrayList<>(pool);
            Integer splashId = temp.get(rng.nextInt(temp.size()));
            targets.add(splashId);
            pool.remove(splashId);
            --remaining;
        }
    }
}