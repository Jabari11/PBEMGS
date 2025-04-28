package com.pbemgs.game.rpg.combat;

import com.pbemgs.game.rpg.combat.action.Action;
import com.pbemgs.game.rpg.combat.action.TriggeredAction;
import com.pbemgs.game.rpg.combat.card.Card;
import com.pbemgs.game.rpg.combat.card.CardManager;
import com.pbemgs.game.rpg.model.ResourceType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * The combat engine.
 */
public class CombatEngine {
    private final CardManager cardManager;
    private int round;
    private int nextCharId;
    private final Map<Integer, CombatChar> charsById;  // dead chars will be removed.
    private final CombatLog combatLog;
    private final Random rng;

    public CombatEngine(List<CombatChar> chars, CardManager cardManager) {
        this.cardManager = cardManager;
        round = 0;
        nextCharId = 0;
        charsById = new HashMap<>();
        combatLog = new CombatLog();

        rng = new Random();
        combatLog.addLogType(LogEvent.ALL);
        combatLog.setLogLevel(LogLevel.DEV);

        for (CombatChar thisChar : chars) {
            thisChar.setUnitId(nextCharId);
            thisChar.shuffleDeck();
            charsById.put(nextCharId, thisChar);
            ++nextCharId;
        }
    }

    /**
     * Execute the combat.
     * Returns the list of surviving non-temporary characters
     */
    public List<CombatChar> execute() {
        boolean done = false;

        while (!done) {
            round++;
            combatLog.log(LogEvent.PHASE, LogLevel.INFO, "\nStarting Combat Round: " + round);

            List<Integer> turnOrder = generateTurnOrder();
            processStartOfRoundTriggers();

            for (int unitId : turnOrder) {
                if (charsById.containsKey(unitId)) {
                    CombatChar actor = charsById.get(unitId);
                    combatLog.log(LogEvent.ACTION, LogLevel.INFO, "\nActing unitId: " + unitId + " - " + actor.getName());
                    if (!actor.getStatusEffects().isActionPrevented(rng, combatLog)) {
                        characterAction(actor);
                    } else {
                        combatLog.log(LogEvent.ACTION, LogLevel.INFO, "-- ACTION PREVENTED BY STATUS!");
                    }
                    actor.getStatusEffects().tickAfterCard(actor, combatLog);  // clear duration SEs on actor after card
                }
            }
            // No more chars to act
            processEndOfRoundTriggers();

            CharSide winSide = checkEndOfCombat();
            if (winSide != null) {
                combatLog.log(LogEvent.PHASE, LogLevel.INFO, "\nCombat over - winning side: " + winSide);
                done = true;
            }
        }

        // TODO: return the list of alive, non-temp chars
        return new ArrayList<>();
    }


    /**
     * Turn order determination.
     * Calculate effective speed for all units by taking base speed, applying SEs, then
     * adding a jitter (+/- 10%) to them.
     */
    private static final double SPEED_JITTER = 0.1d;

    private record CharSpeedData(int unitId, double speed) {
    }

    private List<Integer> generateTurnOrder() {
        combatLog.log(LogEvent.PHASE, LogLevel.INFO, "- Generating Turn Order...");
        List<CharSpeedData> speedData = new ArrayList<>();
        for (Integer unitId : charsById.keySet()) {
            CombatChar thisChar = charsById.get(unitId);
            int speed = thisChar.getSpeed();
            // TODO: SEs for SPD stat adjustment
            double finalSpeed = (double) speed * (1.0d + rng.nextDouble(-SPEED_JITTER, SPEED_JITTER));
            combatLog.log(LogEvent.PHASE, LogLevel.DEV, thisChar.getCharDevLog(finalSpeed));
            speedData.add(new CharSpeedData(unitId, finalSpeed));
        }
        speedData.sort(Comparator.comparingDouble(CharSpeedData::speed).reversed());
        return speedData.stream().map(CharSpeedData::unitId).toList();
    }

    /**
     * Execute a character's action.
     * There are two distinct things going on for actions and targeting.  The Card will have a list of
     * actions, which may trigger reaction-actions.  "normal" actions can also have a target-type of
     * "linked", which means to hit the same things as the last action did, but this linkage has to
     * ignore in-between triggered-actions.
     */
    private void characterAction(CombatChar cardActor) {
        // in-turn bookkeeping structures
        Set<Integer> unitsAffectedForCard = new HashSet<>();  // all units affected by a card - to tick down SEs
        Set<Integer> targetsHitPreviousAction = new HashSet<>(); // targets hit by previous action, for LINKED
        Set<Integer> targetsOfPreviousAction = new HashSet<>();  // targets selected by the previous action, for SAME
        Set<Integer> targetsHitThisAction = new HashSet<>();  // targets hit during this action

        boolean cantripUsed = false;

        // Card-level loop - a cantrip will allow a second Card to be played by this character
        boolean done = false;
        while (!done) {
            Card card = getNextCard(cardActor, combatLog);
            if (card == null) {
                break;
            }

            // Card replacement checks - Shaman card out-of-spirit
            if (card.getSpiritCost() > 0) {
                if (cardActor.getResourceVal(ResourceType.SPIRIT) < card.getSpiritCost()) {
                    combatLog.log(LogEvent.PHASE, LogLevel.INFO, "-- Flipped Shaman card costing " + card.getSpiritCost() +
                            ", only have " + cardActor.getResourceVal(ResourceType.SPIRIT) + " Spirit.  Replacing with Drained Spirit.");
                    card = cardManager.getCard("Drained Spirit");
                } else {
                    cardActor.adjustSpirit((float)(-card.getSpiritCost()));
                    combatLog.log(LogEvent.PHASE, LogLevel.DEV, "- Subtracting Spirit Cost: " + card.getSpiritCost() +
                            ", remaining: " + cardActor.getResourceVal(ResourceType.SPIRIT));
                }
            }

            combatLog.log(LogEvent.PHASE, LogLevel.INFO, "Card: " + card.getName());
            unitsAffectedForCard.clear();
            unitsAffectedForCard.add(cardActor.getUnitId());
            targetsHitPreviousAction.clear();
            targetsOfPreviousAction.clear();

            // A card can have several actions
            for (Action cardAction : card.getActions()) {
                // ActionTriggerSEs can queue additional actions during resolution of the current card-action.
                Queue<TriggeredAction> actionQueue = new ArrayDeque<>();
                actionQueue.add(new TriggeredAction(cardAction, cardActor.getUnitId(), true, null));
                while (!actionQueue.isEmpty()) {
                    TriggeredAction currAction = actionQueue.poll();
                    Action action = currAction.action();
                    CombatChar actor = charsById.get(currAction.actingUnitId());
                    if (actor == null || !actor.isAlive()) {
                        break;  // If a counter has killed the actor, break
                    }

                    // Target selection
                    Set<Integer> targetIds = new HashSet<>();
                    if (currAction.forcedTargetId() != null) {
                        targetIds.add(currAction.forcedTargetId());
                        targetIds.removeIf(id -> !charsById.containsKey(id));
                    } else if (action.getTargetType() == TargetType.LINKED) {
                        targetIds.addAll(targetsHitPreviousAction);
                        targetIds.removeIf(id -> !charsById.containsKey(id));
                    } else if (action.getTargetType() == TargetType.SAME) {
                        targetIds.addAll(targetsOfPreviousAction);
                        targetIds.removeIf(id -> !charsById.containsKey(id));
                    } else if (action.getTargetType() == TargetType.SELF_AFTER_HIT) {
                        if (!targetsHitPreviousAction.isEmpty()) {
                            targetIds.add(actor.getUnitId());
                        }
                    }
                    else {
                        TargetingEngine targeter = new TargetingEngine(charsById, rng, combatLog);
                        targetIds.addAll(targeter.selectTargets(actor, action));
                    }

                    combatLog.log(LogEvent.ACTION, LogLevel.INFO, "Acting Unit ID: " + actor.getUnitId() +
                            ", Action: " + currAction.action().getName() + " - Target selection: " + targetIds.toString());
                    unitsAffectedForCard.addAll(targetIds);

                    targetsHitThisAction.clear();
                    for (Integer targetId : targetIds) {
                        CombatChar target = charsById.get(targetId);

                        // Check action conditional
                        if (action.getActionConditional() != null &&
                            !action.getActionConditional().isActionValid(actor, target)) {
                            combatLog.log(LogEvent.ACTION, LogLevel.DEV, "-- Action not valid, skipped.");
                            continue;
                        }

                        int hitPct = actor.getSide().equals(target.getSide()) ? 100 :
                                action.getChanceToHit() +
                                        actor.getStatusEffects().computeToHitModifier(action.getActionType(), true, combatLog) +
                                        target.getStatusEffects().computeToHitModifier(action.getActionType(), false, combatLog);
                        int hitRoll = rng.nextInt(100) + 1;  // [1..100]
                        combatLog.log(LogEvent.ATTACK, LogLevel.DEV, "- Hit roll against target ID: " + targetId +
                                " - " + hitRoll + " (toHit <= " + hitPct + ")");

                        if (hitRoll <= hitPct) {
                            targetsHitThisAction.add(targetId);
                            List<CombatChar> summonedChars = action.process(actor, target, currAction.isCardAction(), actionQueue, combatLog);

                            if (!target.isAlive()) {
                                combatLog.log(LogEvent.ATTACK, LogLevel.INFO, "- Target killed!");
                                charsById.remove(targetId);
                            }

                            for (CombatChar newChar : summonedChars) {
                                newChar.setUnitId(nextCharId);
                                charsById.put(nextCharId, newChar);
                                combatLog.log(LogEvent.ACTION, LogLevel.INFO, "- New Unit Summoned - Name: " + newChar.getName() +
                                        ", ID: " + nextCharId + ", Side: " + newChar.getSide());
                                nextCharId++;
                            }
                        }
                    }  // end for (target of action)

                    if (currAction.isCardAction()) {
                        // Only set LINKED and SAME target lists for an "original" card action.
                        targetsHitPreviousAction.clear();
                        targetsHitPreviousAction.addAll(targetsHitThisAction);
                        targetsOfPreviousAction.clear();
                        targetsOfPreviousAction.addAll(targetIds);
                    }
                }  // end while (action queue not empty)
            }  // end for (actions on card)

            // Post-card charge-base SE usage reduction
            for (int unitId : unitsAffectedForCard) {
                if (charsById.containsKey(unitId)) {
                    charsById.get(unitId).getStatusEffects().tickChargeUsage(charsById.get(unitId), combatLog);
                }
            }

            // Cantrip processing
            if (card.isCantrip() && !cantripUsed) {
                cantripUsed = true;
                combatLog.log(LogEvent.PHASE, LogLevel.INFO, "-- Cantrip used, playing second card.");
            } else {
                done = true;
            }
        }  // end while (turn not done)

    }  // characterAction()

    /**
     * Get the next card for the active character, handling card replacement.
     */
    private Card getNextCard(CombatChar cardActor, CombatLog combatLog) {
        if (cardActor.getResourceVal(ResourceType.ENRAGE_TIMER) > 0) {
            combatLog.log(LogEvent.PHASE, LogLevel.INFO, "-- Character is Enraged! Using Wild Strike.");
            return cardManager.getCard("Wild Strike");
        }
        Card card = cardActor.drawCard();
        if (card == null) {
            combatLog.log(LogEvent.PHASE, LogLevel.INFO, "No Cards - reshuffling and skipping turn.");
            cardActor.shuffleDeck();
            return null;
        }
        if (card.getRageIncurred() > 0) {
            cardActor.addRage(card.getRageIncurred());
        }
        return card;
    }

    /**
     * Checks end of combat, also clearing off dead characters (from DoTs, etc)
     * Returns a CharSide if only one side remains, null if both still up.
     */
    private CharSide checkEndOfCombat() {
        Set<CharSide> seenSides = new HashSet<>();
        for (Integer unitId : new HashSet<>(charsById.keySet())) {
            if (charsById.get(unitId).isAlive()) {
                seenSides.add(charsById.get(unitId).getSide());
            } else {
                charsById.remove(unitId);
            }
        }

        if (seenSides.isEmpty()) {
            combatLog.log(LogEvent.PHASE, LogLevel.WARNING, "No chars left - full wipe?!");
            return CharSide.ENEMY;
        }
        if (seenSides.size() == 2) {
            return null;
        }
        return seenSides.iterator().next();  // single-element set
    }

    private void processStartOfRoundTriggers() {
 /*       combatLog.log(LogEvent.PHASE, LogLevel.DEV, "\nStarting Start of Round Triggers...");
        for (CombatChar character : charsById.values()) {
            // TODO: Start of turn effects (buff spreading, delayed actions, etc)
        }
        combatLog.log(LogEvent.PHASE, LogLevel.DEV, "Ending Start of Round Triggers.");
  */
    }

    private void processEndOfRoundTriggers() {
        combatLog.log(LogEvent.PHASE, LogLevel.DEV, "\nStarting End of Round Triggers...");
        List<Integer> charList = charsById.keySet().stream().toList();
        for (Integer unitId : charList) {
            CombatChar actor = charsById.get(unitId);
            if (actor.tickDuration()) {
                combatLog.log(LogEvent.PHASE, LogLevel.INFO, actor.getName() + " fades into mist.");
                charsById.remove(unitId);
            } else {
                actor.checkEnragedState();
                actor.regenerateSpirit();
                actor.getStatusEffects().processPeriodicDamageSEs(actor, combatLog);
                actor.getStatusEffects().tickEndOfRound(actor, combatLog);
            }
        }
        combatLog.log(LogEvent.PHASE, LogLevel.DEV, "Ending End of Round Triggers.");
    }

}
