package com.example.core

import kotlin.math.floor

/**
 * Pure, deterministic, seedable PRNG (Mulberry32 algorithm) for reproducible Daily Workouts
 * and unit-tested question generation.
 */
class SeededPrng(seedInput: Long) {
    private var state: Int = seedInput.toInt()

    constructor(seedString: String) : this(hashString(seedString))

    init {
        if (state == 0) {
            state = 0x6D2B79F5
        }
    }

    /** Returns a pseudo-random Double in [0.0, 1.0). */
    fun next(): Double {
        state += 0x6D2B79F5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        val unsigned = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return unsigned.toDouble() / 4294967296.0
    }

    /** Returns a random integer in [min, max] inclusive. */
    fun int(min: Int, max: Int): Int {
        if (max <= min) return min
        val span = (max.toLong() - min.toLong() + 1L)
        return (min.toLong() + floor(next() * span).toLong()).toInt().coerceIn(min, max)
    }

    /** Picks a random element from a non-empty list. */
    fun <T> pick(items: List<T>): T {
        require(items.isNotEmpty()) { "Cannot pick from an empty list" }
        return items[int(0, items.size - 1)]
    }

    /** Returns a new list with elements shuffled using Fisher-Yates. */
    fun <T> shuffle(items: List<T>): List<T> {
        val copy = items.toMutableList()
        for (i in copy.size - 1 downTo 1) {
            val j = int(0, i)
            val tmp = copy[i]
            copy[i] = copy[j]
            copy[j] = tmp
        }
        return copy
    }

    /** Picks an index based on positive weights. */
    fun weightedIndex(weights: List<Double>): Int {
        require(weights.isNotEmpty()) { "Weights cannot be empty" }
        val total = weights.sum()
        if (total <= 0.0) return int(0, weights.size - 1)
        val r = next() * total
        var acc = 0.0
        for (i in weights.indices) {
            acc += weights[i]
            if (r <= acc) return i
        }
        return weights.lastIndex
    }

    companion object {
        fun hashString(str: String): Long {
            var h = 1125899906842597L
            for (ch in str) {
                h = 31L * h + ch.code.toLong()
            }
            return h
        }
    }
}
