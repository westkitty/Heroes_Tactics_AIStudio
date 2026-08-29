package com.example.core

/**
 * Result of advancing the turn queue.
 */
sealed class TurnAdvanceResult {
    data class ActiveStack(val stack: CombatStack, val isExtraMoraleTurn: Boolean = false) : TurnAdvanceResult()
    data class RoundCompleted(val roundNumber: Int) : TurnAdvanceResult()
    data class CombatEnded(val winningSide: CombatSide) : TurnAdvanceResult()
}

/**
 * Deterministic Turn Order and Initiative Queue Engine.
 * Follows HoMM3 initiative rules:
 * 1. Units sorted strictly by effective speed descending.
 * 2. Ties broken deterministically by ATTACKER > DEFENDER, then by slotIndex ascending.
 * 3. Units that execute 'WAIT' are moved to the end of the round into a secondary waiting queue.
 * 4. Units can only wait once per round.
 * 5. High morale triggers immediate bonus actions deterministically.
 */
class TurnOrderQueue(
    private val rng: DeterministicRng = DeterministicRng()
) {
    var roundNumber: Int = 1
        private set

    private val activeStacks = mutableListOf<CombatStack>()
    private var currentQueue = mutableListOf<CombatStack>()
    private val waitingQueue = mutableListOf<CombatStack>()
    var currentActiveStack: CombatStack? = null
        private set

    /**
     * Sets the combat stacks participating in battle and initiates round 1.
     */
    fun initializeCombat(stacks: List<CombatStack>) {
        activeStacks.clear()
        activeStacks.addAll(stacks.filter { it.isAlive })
        roundNumber = 1
        startRound()
    }

    /**
     * Starts a new combat round, resetting turn flags and sorting the initiative queue.
     */
    fun startRound() {
        activeStacks.removeAll { !it.isAlive }
        activeStacks.forEach { it.startNewRound() }
        waitingQueue.clear()
        currentQueue = sortInitiativeOrder(activeStacks).toMutableList()
        currentActiveStack = null
    }

    /**
     * Deterministic initiative sorting comparison.
     */
    fun sortInitiativeOrder(stacks: List<CombatStack>): List<CombatStack> {
        return stacks.sortedWith(
            compareByDescending<CombatStack> { it.effectiveSpeed }
                .thenBy { if (it.side == CombatSide.ATTACKER) 0 else 1 }
                .thenBy { it.slotIndex }
        )
    }

    /**
     * Advances to the next active stack in turn order.
     */
    fun advanceTurn(): TurnAdvanceResult {
        // Check victory / defeat condition
        val aliveAttackers = activeStacks.filter { it.side == CombatSide.ATTACKER && it.isAlive }
        val aliveDefenders = activeStacks.filter { it.side == CombatSide.DEFENDER && it.isAlive }

        if (aliveAttackers.isEmpty()) {
            return TurnAdvanceResult.CombatEnded(CombatSide.DEFENDER)
        }
        if (aliveDefenders.isEmpty()) {
            return TurnAdvanceResult.CombatEnded(CombatSide.ATTACKER)
        }

        // Pop next available stack from main queue
        while (currentQueue.isNotEmpty()) {
            val candidate = currentQueue.removeAt(0)
            if (candidate.isAlive && !candidate.hasActed) {
                // Check morale freeze (negative morale: -1 -> 1/12 (8%), -2 -> 2/12 (16%), -3 -> 3/12 (25%))
                if (candidate.moraleScore < 0) {
                    val freezeChance = kotlin.math.abs(candidate.moraleScore) * 8
                    if (rng.checkChance(freezeChance)) {
                        // Stack freezes due to low morale and loses turn
                        candidate.hasActed = true
                        continue
                    }
                }
                currentActiveStack = candidate
                return TurnAdvanceResult.ActiveStack(candidate)
            }
        }

        // Main queue empty, check waiting queue
        while (waitingQueue.isNotEmpty()) {
            val candidate = waitingQueue.removeAt(0)
            if (candidate.isAlive && !candidate.hasActed) {
                currentActiveStack = candidate
                return TurnAdvanceResult.ActiveStack(candidate)
            }
        }

        // Both queues empty -> Round ends
        roundNumber++
        startRound()
        return TurnAdvanceResult.RoundCompleted(roundNumber)
    }

    /**
     * Handles the 'WAIT' action for the current active stack.
     * Moves the unit to the waiting queue (sorted by speed descending) if it hasn't waited yet.
     */
    fun handleWait(): Boolean {
        val stack = currentActiveStack ?: return false
        if (stack.hasWaited || stack.hasActed) return false

        stack.hasWaited = true
        waitingQueue.add(stack)
        // Keep waiting queue sorted by speed descending
        waitingQueue.sortWith(
            compareByDescending<CombatStack> { it.effectiveSpeed }
                .thenBy { if (it.side == CombatSide.ATTACKER) 0 else 1 }
                .thenBy { it.slotIndex }
        )
        currentActiveStack = null
        return true
    }

    /**
     * Handles the 'DEFEND' action for the current active stack.
     */
    fun handleDefend(): Boolean {
        val stack = currentActiveStack ?: return false
        stack.isDefending = true
        stack.hasActed = true
        currentActiveStack = null
        return true
    }

    /**
     * Completes action for current stack and evaluates positive morale bonus action.
     */
    fun finishStackAction(): TurnAdvanceResult? {
        val stack = currentActiveStack ?: return null
        stack.hasActed = true

        // Positive morale bonus check (+1 -> 8%, +2 -> 16%, +3 -> 25%)
        if (stack.isAlive && stack.moraleScore > 0) {
            val bonusChance = stack.moraleScore * 8
            if (rng.checkChance(bonusChance)) {
                // Bonus morale turn awarded immediately!
                return TurnAdvanceResult.ActiveStack(stack, isExtraMoraleTurn = true)
            }
        }

        currentActiveStack = null
        return null
    }

    /**
     * Restores state of the turn queue for save/load serialization.
     */
    fun restoreTurnOrderState(
        savedRoundNumber: Int,
        allCombatStacks: List<CombatStack>,
        currentQueueStackIds: List<String>,
        waitingQueueStackIds: List<String>,
        activeStackId: String?
    ) {
        roundNumber = savedRoundNumber
        activeStacks.clear()
        activeStacks.addAll(allCombatStacks.filter { it.isAlive })

        val stackMap = allCombatStacks.associateBy { it.id }
        currentQueue = currentQueueStackIds.mapNotNull { stackMap[it] }.filter { it.isAlive }.toMutableList()
        waitingQueue.clear()
        waitingQueue.addAll(waitingQueueStackIds.mapNotNull { stackMap[it] }.filter { it.isAlive })
        currentActiveStack = if (activeStackId != null) stackMap[activeStackId] else null
    }

    /**
     * Returns an ordered list of upcoming turns for UI preview.
     */
    fun getUpcomingTurnOrder(): List<CombatStack> {
        val upcoming = mutableListOf<CombatStack>()
        currentActiveStack?.let { upcoming.add(it) }
        upcoming.addAll(currentQueue.filter { it.isAlive && !it.hasActed })
        upcoming.addAll(waitingQueue.filter { it.isAlive && !it.hasActed })
        return upcoming
    }
}
