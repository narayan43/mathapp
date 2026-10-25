package com.example.core

import com.example.model.CustomConfig
import com.example.model.Difficulty
import com.example.model.MathOp
import com.example.model.MemoryChain
import com.example.model.MemoryStep
import com.example.model.Problem
import com.example.model.QuizMode
import kotlin.math.sqrt

/**
 * Pure, unit-tested Question Generator for all Core Quiz Modes, Square Root, Exponents,
 * Cube Root, Multiplication Tables, Customizable Mode, and Arithmetic Memory Chains.
 */
object QuestionGenerator {

    fun generateQuizQuestions(
        mode: QuizMode,
        difficulty: Difficulty,
        count: Int,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): List<Problem> {
        val result = mutableListOf<Problem>()
        var lastPair: Pair<Int, Int>? = null
        for (i in 0 until count) {
            var candidate: Problem
            var attempts = 0
            do {
                candidate = generateSingleByMode(mode, difficulty, i, prng)
                attempts++
            } while (attempts < 25 && lastPair != null && lastPair.first == candidate.a && lastPair.second == candidate.b)
            lastPair = candidate.a to candidate.b
            result.add(candidate)
        }
        return result
    }

    fun generateSingleByMode(
        mode: QuizMode,
        difficulty: Difficulty,
        index: Int = 0,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): Problem {
        return when (mode) {
            QuizMode.ADDITION -> generateCoreOpProblem(MathOp.ADD, difficulty, index, prng)
            QuizMode.SUBTRACTION -> generateCoreOpProblem(MathOp.SUB, difficulty, index, prng)
            QuizMode.MULTIPLICATION -> generateCoreOpProblem(MathOp.MUL, difficulty, index, prng)
            QuizMode.DIVISION -> generateCoreOpProblem(MathOp.DIV, difficulty, index, prng)
            QuizMode.MIXED -> {
                val op = prng.pick(listOf(MathOp.ADD, MathOp.SUB, MathOp.MUL, MathOp.DIV))
                generateCoreOpProblem(op, difficulty, index, prng)
            }
            QuizMode.ADD_SUB -> {
                val op = if (prng.next() < 0.5) MathOp.ADD else MathOp.SUB
                generateCoreOpProblem(op, difficulty, index, prng)
            }
            QuizMode.MULT_DIV -> {
                val op = if (prng.next() < 0.5) MathOp.MUL else MathOp.DIV
                generateCoreOpProblem(op, difficulty, index, prng)
            }
            QuizMode.SQUARE_ROOT -> generateSquareRootProblem(difficulty, index, prng)
            QuizMode.EXPONENTS -> generateExponentsProblem(difficulty, index, prng)
            QuizMode.CUBE_ROOT -> generateCubeRootProblem(difficulty, index, prng)
            else -> generateCoreOpProblem(MathOp.ADD, difficulty, index, prng)
        }
    }

    fun generateCoreOpProblem(
        op: MathOp,
        difficulty: Difficulty,
        index: Int = 0,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): Problem {
        return when (op) {
            MathOp.ADD -> {
                val (min, max) = additionRange(difficulty)
                val a = prng.int(min, max)
                val b = prng.int(min, max)
                val ans = a.toLong() + b.toLong()
                buildStandardProblem("q_$index", MathOp.ADD, a, b, ans, difficulty.index + 1)
            }
            MathOp.SUB -> {
                val (min, max) = subtractionRange(difficulty)
                val minuend = prng.int(min, max)
                val subtrahend = prng.int(min, minuend)
                val ans = (minuend - subtrahend).toLong()
                buildStandardProblem("q_$index", MathOp.SUB, minuend, subtrahend, ans, difficulty.index + 1)
            }
            MathOp.MUL -> {
                val (min, max) = multiplicationRange(difficulty)
                val a = prng.int(min, max)
                val b = prng.int(min, max)
                val ans = a.toLong() * b.toLong()
                buildStandardProblem("q_$index", MathOp.MUL, a, b, ans, difficulty.index + 1)
            }
            MathOp.DIV -> {
                val (min, max) = divisionRange(difficulty)
                val (dividend, divisor) = pickCompositeAndNonTrivialDivisor(min, max, prng)
                val ans = (dividend / divisor).toLong()
                buildStandardProblem("q_$index", MathOp.DIV, dividend, divisor, ans, difficulty.index + 1)
            }
            MathOp.SQRT -> generateSquareRootProblem(difficulty, index, prng)
            MathOp.POW -> generateExponentsProblem(difficulty, index, prng)
            MathOp.CBRT -> generateCubeRootProblem(difficulty, index, prng)
        }
    }

    fun additionRange(difficulty: Difficulty): Pair<Int, Int> = when (difficulty) {
        Difficulty.EASY -> 1 to 11
        Difficulty.MEDIUM -> 10 to 88
        Difficulty.HARD -> 59 to 230
        Difficulty.CHALLENGING -> 500 to 1500
        Difficulty.EXPERT -> 300000 to 900000
    }

    fun subtractionRange(difficulty: Difficulty): Pair<Int, Int> = when (difficulty) {
        Difficulty.EASY -> 1 to 23
        Difficulty.MEDIUM -> 1 to 113
        Difficulty.HARD -> 1 to 450
        Difficulty.CHALLENGING -> 500 to 3000
        Difficulty.EXPERT -> 111111 to 999999
    }

    fun multiplicationRange(difficulty: Difficulty): Pair<Int, Int> = when (difficulty) {
        Difficulty.EASY -> 1 to 9
        Difficulty.MEDIUM -> 2 to 12
        Difficulty.HARD -> 5 to 18
        Difficulty.CHALLENGING -> 21 to 99
        Difficulty.EXPERT -> 233 to 999
    }

    fun divisionRange(difficulty: Difficulty): Pair<Int, Int> = when (difficulty) {
        Difficulty.EASY -> 1 to 50
        Difficulty.MEDIUM -> 2 to 121
        Difficulty.HARD -> 7 to 324
        Difficulty.CHALLENGING -> 21 to 3000
        Difficulty.EXPERT -> 211 to 999999
    }

    /**
     * Picks a dividend in [min, max] that has at least 3 divisors (i.e. at least 1 non-trivial divisor)
     * and returns (dividend, nonTrivialDivisor).
     */
    fun pickCompositeAndNonTrivialDivisor(
        min: Int,
        max: Int,
        prng: SeededPrng
    ): Pair<Int, Int> {
        val safeMin = min.coerceAtLeast(4)
        val safeMax = max.coerceAtLeast(safeMin)
        for (attempt in 0 until 80) {
            val candidate = prng.int(safeMin, safeMax)
            val nonTrivial = findNonTrivialDivisors(candidate)
            if (nonTrivial.isNotEmpty()) {
                return candidate to prng.pick(nonTrivial)
            }
        }
        // Fallback: construct a guaranteed composite number inside [safeMin, safeMax]
        var candidate = if (safeMin % 2 == 0 && safeMin > 2) safeMin else safeMin + 1
        if (candidate > safeMax) candidate = 4
        val divisors = findNonTrivialDivisors(candidate)
        return if (divisors.isNotEmpty()) {
            candidate to prng.pick(divisors)
        } else {
            12 to 3
        }
    }

    /**
     * Returns all non-trivial divisors of [n] (excluding 1 and n).
     * If a number has >= 3 total divisors, this list is non-empty.
     */
    fun findNonTrivialDivisors(n: Int): List<Int> {
        if (n < 4) return emptyList()
        val limit = sqrt(n.toDouble()).toInt()
        val list = mutableListOf<Int>()
        for (d in 2..limit) {
            if (n % d == 0) {
                list.add(d)
                val pair = n / d
                if (pair != d && pair != n) {
                    list.add(pair)
                }
            }
        }
        return list
    }

    fun generateSquareRootProblem(
        difficulty: Difficulty,
        index: Int = 0,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): Problem {
        val (minRoot, maxRoot) = when (difficulty) {
            Difficulty.EASY -> 1 to 10
            Difficulty.MEDIUM -> 5 to 20
            Difficulty.HARD -> 10 to 30
            Difficulty.CHALLENGING -> 12 to 40
            Difficulty.EXPERT -> 15 to 50
        }
        val root = prng.int(minRoot, maxRoot)
        val square = root * root
        return Problem(
            id = "sqrt_$index",
            op = MathOp.SQRT,
            a = square,
            b = 2,
            answer = root.toLong(),
            displayBase = "√$square",
            superscriptExponent = null,
            level = difficulty.index + 1,
            normalizedKey = "SQRT:$square"
        )
    }

    fun generateCubeRootProblem(
        difficulty: Difficulty,
        index: Int = 0,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): Problem {
        val (minRoot, maxRoot) = when (difficulty) {
            Difficulty.EASY -> 1 to 8
            Difficulty.MEDIUM -> 2 to 12
            Difficulty.HARD -> 4 to 16
            Difficulty.CHALLENGING -> 6 to 20
            Difficulty.EXPERT -> 8 to 25
        }
        val root = prng.int(minRoot, maxRoot)
        val cube = root * root * root
        return Problem(
            id = "cbrt_$index",
            op = MathOp.CBRT,
            a = cube,
            b = 3,
            answer = root.toLong(),
            displayBase = "∛$cube",
            superscriptExponent = null,
            level = difficulty.index + 1,
            normalizedKey = "CBRT:$cube"
        )
    }

    fun generateExponentsProblem(
        difficulty: Difficulty,
        index: Int = 0,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): Problem {
        val (base, exp) = when (difficulty) {
            Difficulty.EASY -> prng.int(2, 10) to 2
            Difficulty.MEDIUM -> prng.int(11, 20) to 2
            Difficulty.HARD -> prng.int(2, 10) to 3
            Difficulty.CHALLENGING -> prng.int(21, 35) to 2
            Difficulty.EXPERT -> {
                if (prng.next() < 0.6) prng.int(21, 50) to 2 else prng.int(2, 10) to 4
            }
        }
        var ans = 1L
        repeat(exp) { ans *= base.toLong() }
        return Problem(
            id = "pow_$index",
            op = MathOp.POW,
            a = base,
            b = exp,
            answer = ans,
            displayBase = "$base",
            superscriptExponent = "$exp",
            level = difficulty.index + 1,
            normalizedKey = "POW:$base^$exp"
        )
    }

    fun generateTableQuestions(
        selectedTables: Set<Int>,
        count: Int,
        maxMultiplier: Int = 12,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): List<Problem> {
        val tables = if (selectedTables.isEmpty()) listOf(2, 3, 4, 5) else selectedTables.sorted()
        val problems = mutableListOf<Problem>()
        var lastPair: Pair<Int, Int>? = null
        for (i in 0 until count) {
            var n: Int
            var k: Int
            var attempts = 0
            do {
                n = prng.pick(tables)
                k = prng.int(1, maxMultiplier)
                attempts++
            } while (attempts < 20 && lastPair != null && lastPair.first == n && lastPair.second == k)
            lastPair = n to k
            problems.add(
                buildStandardProblem("tbl_$i", MathOp.MUL, n, k, n.toLong() * k.toLong(), 1)
            )
        }
        return problems
    }

    fun generateCustomQuestions(
        config: CustomConfig,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): List<Problem> {
        val ops = if (config.ops.isEmpty()) listOf(MathOp.ADD) else config.ops.toList()
        val minA = config.minA.coerceAtLeast(1)
        val maxA = config.maxA.coerceAtLeast(minA)
        val minB = config.minB.coerceAtLeast(1)
        val maxB = config.maxB.coerceAtLeast(minB)

        val list = mutableListOf<Problem>()
        var lastPair: Pair<Int, Int>? = null
        for (i in 0 until config.questionCount) {
            var problem: Problem
            var tries = 0
            do {
                val op = prng.pick(ops)
                problem = when (op) {
                    MathOp.ADD -> {
                        val a = prng.int(minA, maxA)
                        val b = prng.int(minB, maxB)
                        buildStandardProblem("cust_$i", MathOp.ADD, a, b, a.toLong() + b.toLong(), 1)
                    }
                    MathOp.SUB -> {
                        val rawA = prng.int(minA, maxA)
                        val rawB = prng.int(minB, maxB)
                        val a = maxOf(rawA, rawB)
                        val b = minOf(rawA, rawB)
                        buildStandardProblem("cust_$i", MathOp.SUB, a, b, (a - b).toLong(), 1)
                    }
                    MathOp.MUL -> {
                        val a = prng.int(minA, maxA)
                        val b = prng.int(minB, maxB)
                        buildStandardProblem("cust_$i", MathOp.MUL, a, b, a.toLong() * b.toLong(), 1)
                    }
                    MathOp.DIV -> {
                        val quotient = prng.int(minA, maxA)
                        val divisor = prng.int(minB.coerceAtLeast(2), maxB.coerceAtLeast(2))
                        val dividend = quotient * divisor
                        buildStandardProblem("cust_$i", MathOp.DIV, dividend, divisor, quotient.toLong(), 1)
                    }
                    else -> {
                        val a = prng.int(minA, maxA)
                        val b = prng.int(minB, maxB)
                        buildStandardProblem("cust_$i", MathOp.ADD, a, b, a.toLong() + b.toLong(), 1)
                    }
                }
                tries++
            } while (tries < 20 && lastPair != null && lastPair.first == problem.a && lastPair.second == problem.b)
            lastPair = problem.a to problem.b
            list.add(problem)
        }
        return list
    }

    /**
     * Generates an Arithmetic Memory ("Follow the chain") sequence adhering strictly
     * to the running-value caps, zero rules, "too big" thresholds, exact division, and non-repeating steps.
     */
    fun generateMemoryChain(
        difficulty: Difficulty,
        stepsCount: Int = 6,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): MemoryChain {
        val (startMin, startMax) = when (difficulty) {
            Difficulty.EASY -> 5 to 10
            Difficulty.MEDIUM -> 8 to 11
            Difficulty.HARD -> 10 to 18
            Difficulty.CHALLENGING -> 100 to 200
            Difficulty.EXPERT -> 600 to 1000
        }
        val multiplyCap = when (difficulty) {
            Difficulty.EASY -> 9
            Difficulty.MEDIUM -> 12
            Difficulty.HARD -> 18
            Difficulty.CHALLENGING -> 500
            Difficulty.EXPERT -> 1000
        }
        val tooBigThreshold = when (difficulty) {
            Difficulty.EASY -> 68
            Difficulty.MEDIUM -> 121
            Difficulty.HARD -> 380
            Difficulty.CHALLENGING -> 1000
            Difficulty.EXPERT -> 4000
        }

        val startNumber = prng.int(startMin, startMax)
        var running = startNumber
        val steps = mutableListOf<MemoryStep>()
        var lastStepKey: String? = null

        val safeCount = stepsCount.coerceIn(5, 10)
        for (i in 0 until safeCount) {
            val allowedOps = when {
                running == 0 -> listOf(MathOp.ADD, MathOp.DIV)
                running > tooBigThreshold -> listOf(MathOp.SUB, MathOp.DIV)
                running > multiplyCap -> listOf(MathOp.ADD, MathOp.SUB, MathOp.DIV)
                else -> listOf(MathOp.ADD, MathOp.SUB, MathOp.MUL, MathOp.DIV)
            }

            var chosenStep: MemoryStep? = null
            for (attempt in 0 until 30) {
                var op = prng.pick(allowedOps)
                var operand: Int
                var nextVal: Int

                if (op == MathOp.DIV) {
                    if (running == 0) {
                        operand = prng.int(2, 9)
                        nextVal = 0
                    } else {
                        val divisors = findNonTrivialDivisors(running)
                        if (divisors.isEmpty()) {
                            // Fall back to subtraction if no non-trivial divisor exists
                            op = MathOp.SUB
                            val (subMin, subMax) = subtractionRange(difficulty)
                            val maxSub = minOf(subMax, running).coerceAtLeast(1)
                            val minSub = minOf(subMin, maxSub)
                            operand = prng.int(minSub, maxSub)
                            nextVal = (running - operand).coerceAtLeast(0)
                        } else {
                            operand = prng.pick(divisors)
                            nextVal = running / operand
                        }
                    }
                } else if (op == MathOp.SUB) {
                    if (running <= 0) {
                        op = MathOp.ADD
                        val (addMin, addMax) = additionRange(difficulty)
                        operand = prng.int(addMin, addMax.coerceAtMost(tooBigThreshold / 2).coerceAtLeast(addMin))
                        nextVal = running + operand
                    } else {
                        val (subMin, subMax) = subtractionRange(difficulty)
                        val maxSub = minOf(subMax, running).coerceAtLeast(1)
                        val minSub = minOf(subMin, maxSub)
                        operand = prng.int(minSub, maxSub)
                        nextVal = running - operand
                    }
                } else if (op == MathOp.MUL) {
                    val (mulMin, mulMax) = multiplicationRange(difficulty)
                    val cappedMax = mulMax.coerceAtMost(12).coerceAtLeast(mulMin)
                    operand = prng.int(mulMin.coerceAtLeast(2), cappedMax)
                    nextVal = running * operand
                } else {
                    // ADD
                    val (addMin, addMax) = additionRange(difficulty)
                    val cappedMax = addMax.coerceAtMost(tooBigThreshold).coerceAtLeast(addMin)
                    operand = prng.int(addMin, cappedMax)
                    nextVal = running + operand
                }

                val key = "${op.name}:$operand"
                if (key != lastStepKey || attempt == 29) {
                    chosenStep = MemoryStep(
                        op = op,
                        operand = operand,
                        previousValue = running,
                        runningValue = nextVal
                    )
                    lastStepKey = key
                    break
                }
            }

            val finalStep = chosenStep ?: MemoryStep(
                op = MathOp.ADD,
                operand = 3,
                previousValue = running,
                runningValue = running + 3
            )
            running = finalStep.runningValue
            steps.add(finalStep)
        }

        return MemoryChain(
            startNumber = startNumber,
            steps = steps,
            finalAnswer = running,
            difficulty = difficulty
        )
    }

    fun buildStandardProblem(
        id: String,
        op: MathOp,
        a: Int,
        b: Int,
        answer: Long,
        level: Int
    ): Problem {
        val normKey = when (op) {
            MathOp.ADD, MathOp.MUL -> {
                val first = minOf(a, b)
                val second = maxOf(a, b)
                "${op.name}:$first:$second"
            }
            else -> "${op.name}:$a:$b"
        }
        return Problem(
            id = id,
            op = op,
            a = a,
            b = b,
            answer = answer,
            displayBase = "$a ${op.symbol} $b",
            superscriptExponent = null,
            level = level,
            normalizedKey = normKey
        )
    }
}
