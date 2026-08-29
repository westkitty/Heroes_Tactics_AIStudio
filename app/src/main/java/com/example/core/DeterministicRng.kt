package com.example.core

/**
 * Deterministic Pseudo-Random Number Generator (PRNG) using XorShift64.
 * Guarantees 100% reproducible combat outcomes across all platforms and threads.
 */
class DeterministicRng(seed: Long = 133742069L) {
    private var state: Long = if (seed == 0L) 133742069L else seed

    /**
     * Generates next deterministic 64-bit Long.
     */
    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x
    }

    /**
     * Returns a pseudo-random integer in range [min, max] inclusive.
     */
    fun nextInt(min: Int, max: Int): Int {
        if (min >= max) return min
        val range = (max - min + 1).toLong()
        val raw = nextLong() and 0x7FFFFFFFFFFFFFFFL
        return (min + (raw % range)).toInt()
    }

    /**
     * Returns true if a percentage roll (0..100) succeeds against given probability [0..100].
     */
    fun checkChance(chancePercent: Int): Boolean {
        if (chancePercent <= 0) return false
        if (chancePercent >= 100) return true
        return nextInt(1, 100) <= chancePercent
    }

    /**
     * Returns the current seed state for serialization or snapshotting.
     */
    fun getSeedState(): Long = state

    /**
     * Restores state from snapshot.
     */
    fun setSeedState(seed: Long) {
        state = if (seed == 0L) 133742069L else seed
    }
}
