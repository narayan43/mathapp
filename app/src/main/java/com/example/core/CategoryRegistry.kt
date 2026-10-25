package com.example.core

import com.example.model.Difficulty
import com.example.model.MathOp
import com.example.model.Problem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CategoryInfo(
    val id: String,
    val labelKey: String,
    val defaultTitle: String,
    val opCode: String, // 'add'|'sub'|'mul'|'div'|'sqrt'|'pow'|'cbrt'|'chain'
    val mathOp: MathOp?,
    val badgeSymbol: String,
    val isTableCategory: Boolean = false,
    val tableNumber: Int? = null,
    val labelParams: Map<String, String> = emptyMap()
)

object CategoryRegistry {

    val CORE_CATEGORIES: List<CategoryInfo> = listOf(
        // Addition
        CategoryInfo("add_1d", "cat.add_1d", "1-Digit Addition", "add", MathOp.ADD, "+1d"),
        CategoryInfo("add_2d_nocarry", "cat.add_2d_nocarry", "2-Digit Add (No Carry)", "add", MathOp.ADD, "+2d"),
        CategoryInfo("add_2d_carry", "cat.add_2d_carry", "2-Digit Add (With Carry)", "add", MathOp.ADD, "+2c"),
        CategoryInfo("add_3d", "cat.add_3d", "3-Digit Add (No Carry)", "add", MathOp.ADD, "+3d"),
        CategoryInfo("add_3d_carry", "cat.add_3d_carry", "3-Digit Add (With Carry)", "add", MathOp.ADD, "+3c"),
        CategoryInfo("add_large", "cat.add_large", "Large Addition (4+ Digits)", "add", MathOp.ADD, "+4d"),

        // Subtraction
        CategoryInfo("sub_1d", "cat.sub_1d", "1-Digit Subtraction", "sub", MathOp.SUB, "−1d"),
        CategoryInfo("sub_2d_noborrow", "cat.sub_2d_noborrow", "2-Digit Sub (No Borrow)", "sub", MathOp.SUB, "−2d"),
        CategoryInfo("sub_2d_borrow", "cat.sub_2d_borrow", "2-Digit Sub (With Borrow)", "sub", MathOp.SUB, "−2b"),
        CategoryInfo("sub_3d", "cat.sub_3d", "3-Digit Sub (No Borrow)", "sub", MathOp.SUB, "−3d"),
        CategoryInfo("sub_3d_borrow", "cat.sub_3d_borrow", "3-Digit Sub (With Borrow)", "sub", MathOp.SUB, "−3b"),
        CategoryInfo("sub_large", "cat.sub_large", "Large Subtraction", "sub", MathOp.SUB, "−4d"),

        // Multiplication
        CategoryInfo("mul_table_2_5", "cat.mul_table_2_5", "Tables 2–5", "mul", MathOp.MUL, "×2-5"),
        CategoryInfo("mul_table_6_9", "cat.mul_table_6_9", "Tables 6–9", "mul", MathOp.MUL, "×6-9"),
        CategoryInfo("mul_table_10_12", "cat.mul_table_10_12", "Tables 10–12", "mul", MathOp.MUL, "×12"),
        CategoryInfo("mul_by_10_100", "cat.mul_by_10_100", "Multiply by 10 / 100", "mul", MathOp.MUL, "×10"),
        CategoryInfo("mul_2dx1d", "cat.mul_2dx1d", "2-Digit × 1-Digit", "mul", MathOp.MUL, "2×1"),
        CategoryInfo("mul_2dx2d", "cat.mul_2dx2d", "2-Digit × 2-Digit", "mul", MathOp.MUL, "2×2"),
        CategoryInfo("mul_3dx2d", "cat.mul_3dx2d", "3-Digit × 2-Digit", "mul", MathOp.MUL, "3×2"),
        CategoryInfo("mul_large", "cat.mul_large", "Large Multiplication", "mul", MathOp.MUL, "×Lg"),

        // Division
        CategoryInfo("div_by_1d", "cat.div_by_1d", "Divide by 1-Digit", "div", MathOp.DIV, "÷1d"),
        CategoryInfo("div_by_2d", "cat.div_by_2d", "Divide by 2-Digit", "div", MathOp.DIV, "÷2d"),
        CategoryInfo("div_large", "cat.div_large", "Large Division", "div", MathOp.DIV, "÷Lg"),

        // Roots & Exponents
        CategoryInfo("sqrt_small", "cat.sqrt_small", "Square Roots (≤ 12)", "sqrt", MathOp.SQRT, "√Sm"),
        CategoryInfo("sqrt_medium", "cat.sqrt_medium", "Square Roots (13–25)", "sqrt", MathOp.SQRT, "√Md"),
        CategoryInfo("sqrt_large", "cat.sqrt_large", "Square Roots (26+)", "sqrt", MathOp.SQRT, "√Lg"),
        CategoryInfo("pow2_small", "cat.pow2_small", "Squares (≤ 15²)", "pow", MathOp.POW, "x²"),
        CategoryInfo("pow2_large", "cat.pow2_large", "Large Squares (16²+)", "pow", MathOp.POW, "X²"),
        CategoryInfo("pow3", "cat.pow3", "Cubes (x³)", "pow", MathOp.POW, "x³"),
        CategoryInfo("pow4", "cat.pow4", "Higher Powers (x⁴+)", "pow", MathOp.POW, "x⁴"),
        CategoryInfo("cbrt_all", "cat.cbrt_all", "Cube Roots (∛)", "cbrt", MathOp.CBRT, "∛"),

        // Memory Chains
        CategoryInfo("chain_short", "cat.chain_short", "Memory Chain (Short)", "chain", null, "⛓S"),
        CategoryInfo("chain_long", "cat.chain_long", "Memory Chain (Long)", "chain", null, "⛓L")
    )

    private val coreById: Map<String, CategoryInfo> = CORE_CATEGORIES.associateBy { it.id }

    fun getCategoryInfo(categoryId: String): CategoryInfo {
        coreById[categoryId]?.let { return it }
        if (categoryId.startsWith("table_")) {
            val num = categoryId.removePrefix("table_").toIntOrNull() ?: 7
            return CategoryInfo(
                id = categoryId,
                labelKey = "cat.table_n",
                defaultTitle = "Table of $num",
                opCode = "mul",
                mathOp = MathOp.MUL,
                badgeSymbol = "×$num",
                isTableCategory = true,
                tableNumber = num,
                labelParams = mapOf("n" to num.toString())
            )
        }
        return CategoryInfo(
            id = categoryId,
            labelKey = "cat.$categoryId",
            defaultTitle = categoryId.replace('_', ' ').replaceFirstChar { it.uppercase() },
            opCode = "add",
            mathOp = MathOp.ADD,
            badgeSymbol = "#"
        )
    }

    fun allTrackableCategories(includeCommonTables: Boolean = false): List<CategoryInfo> {
        if (!includeCommonTables) return CORE_CATEGORIES
        val tables = (2..20).map { getCategoryInfo("table_$it") }
        return CORE_CATEGORIES + tables
    }

    /**
     * Pure classification function:
     * Classifies any question based on its operation, operands, and optional mode context.
     */
    fun classify(
        op: MathOp?,
        operands: List<Int>,
        isMemoryChain: Boolean = false,
        singleTableNumber: Int? = null
    ): String {
        if (isMemoryChain || op == null) {
            val stepsCount = operands.size.coerceAtLeast(1)
            return if (stepsCount <= 6) "chain_short" else "chain_long"
        }
        if (singleTableNumber != null && op == MathOp.MUL) {
            return "table_$singleTableNumber"
        }

        val a = abs(operands.getOrElse(0) { 0 })
        val b = abs(operands.getOrElse(1) { 0 })
        val maxOp = max(a, b)
        val minOp = min(a, b)

        return when (op) {
            MathOp.ADD -> {
                when {
                    maxOp < 10 -> "add_1d"
                    maxOp < 100 -> if (hasAdditionCarry(a, b)) "add_2d_carry" else "add_2d_nocarry"
                    maxOp < 1000 -> if (hasAdditionCarry(a, b)) "add_3d_carry" else "add_3d"
                    else -> "add_large"
                }
            }
            MathOp.SUB -> {
                val top = max(a, b)
                val bottom = min(a, b)
                when {
                    top < 10 -> "sub_1d"
                    top < 100 -> if (hasSubtractionBorrow(top, bottom)) "sub_2d_borrow" else "sub_2d_noborrow"
                    top < 1000 -> if (hasSubtractionBorrow(top, bottom)) "sub_3d_borrow" else "sub_3d"
                    else -> "sub_large"
                }
            }
            MathOp.MUL -> {
                val isPowerOfTenTrick = (a in listOf(10, 100, 1000) || b in listOf(10, 100, 1000)) ||
                    ((a >= 20 && a % 10 == 0 || b >= 20 && b % 10 == 0) && maxOp >= 10)
                when {
                    a in listOf(10, 100, 1000) || b in listOf(10, 100, 1000) -> "mul_by_10_100"
                    minOp <= 5 && maxOp <= 9 -> "mul_table_2_5"
                    minOp in 6..9 && maxOp <= 9 -> "mul_table_6_9"
                    maxOp <= 12 && minOp <= 12 -> "mul_table_10_12"
                    isPowerOfTenTrick && minOp <= 12 -> "mul_by_10_100"
                    maxOp < 100 && minOp < 10 -> "mul_2dx1d"
                    maxOp < 100 && minOp in 10..99 -> "mul_2dx2d"
                    maxOp in 100..999 && minOp < 100 -> "mul_3dx2d"
                    else -> "mul_large"
                }
            }
            MathOp.DIV -> {
                // In division problems, a is dividend and b is divisor
                val divisor = b.coerceAtLeast(1)
                val dividend = a
                when {
                    divisor < 10 && dividend < 120 -> "div_by_1d"
                    divisor < 100 && dividend < 2000 -> "div_by_2d"
                    else -> "div_large"
                }
            }
            MathOp.SQRT -> {
                val root = kotlin.math.sqrt(a.toDouble()).toInt()
                when {
                    root <= 12 -> "sqrt_small"
                    root <= 25 -> "sqrt_medium"
                    else -> "sqrt_large"
                }
            }
            MathOp.POW -> {
                val base = a
                val exp = b
                when {
                    exp <= 2 && base <= 15 -> "pow2_small"
                    exp <= 2 -> "pow2_large"
                    exp == 3 -> "pow3"
                    else -> "pow4"
                }
            }
            MathOp.CBRT -> "cbrt_all"
        }
    }

    /**
     * Checks if adding [a] + [b] produces a carry in any base-10 digit position.
     */
    fun hasAdditionCarry(a: Int, b: Int): Boolean {
        var x = abs(a)
        var y = abs(b)
        var carry = 0
        while (x > 0 || y > 0) {
            val sum = (x % 10) + (y % 10) + carry
            if (sum >= 10) return true
            carry = sum / 10
            x /= 10
            y /= 10
        }
        return false
    }

    /**
     * Checks if subtracting [bottom] from [top] (where top >= bottom) requires borrowing in any digit column.
     */
    fun hasSubtractionBorrow(top: Int, bottom: Int): Boolean {
        var x = max(abs(top), abs(bottom))
        var y = min(abs(top), abs(bottom))
        while (x > 0 || y > 0) {
            if ((x % 10) < (y % 10)) return true
            x /= 10
            y /= 10
        }
        return false
    }

    fun mathOpToOpCode(op: MathOp?): String = when (op) {
        MathOp.ADD -> "add"
        MathOp.SUB -> "sub"
        MathOp.MUL -> "mul"
        MathOp.DIV -> "div"
        MathOp.SQRT -> "sqrt"
        MathOp.POW -> "pow"
        MathOp.CBRT -> "cbrt"
        null -> "chain"
    }

    fun opCodeToSymbol(opCode: String): String = when (opCode.lowercase()) {
        "add" -> "+"
        "sub" -> "−"
        "mul" -> "×"
        "div" -> "÷"
        "sqrt" -> "√"
        "pow" -> "^"
        "cbrt" -> "∛"
        "chain" -> "⛓"
        else -> "•"
    }

    fun classify(
        op: String,
        operands: List<Int>,
        singleTableNumber: Int? = null,
        chainStepCount: Int? = null
    ): String {
        val code = op.lowercase()
        if (code == "chain") {
            val steps = chainStepCount ?: operands.size.coerceAtLeast(1)
            return if (steps <= 6) "chain_short" else "chain_long"
        }
        val mathOp = when (code) {
            "add" -> MathOp.ADD
            "sub" -> MathOp.SUB
            "mul" -> MathOp.MUL
            "div" -> MathOp.DIV
            "sqrt" -> MathOp.SQRT
            "pow" -> MathOp.POW
            "cbrt" -> MathOp.CBRT
            else -> MathOp.ADD
        }
        return classify(
            op = mathOp,
            operands = operands,
            isMemoryChain = false,
            singleTableNumber = singleTableNumber
        )
    }

    /**
     * Generates a fresh Problem specifically tailored to [categoryId] at adaptive [level] (1..10).
     */
    fun generateProblemForCategory(
        categoryId: String,
        level: Int = 3,
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): Problem {
        if (categoryId.startsWith("table_")) {
            val tableNum = categoryId.removePrefix("table_").toIntOrNull()?.coerceIn(1, 50) ?: 7
            val mult = prng.int(2, 12)
            return QuestionGenerator.buildStandardProblem(
                id = "cat_${categoryId}_${prng.int(1000, 999999)}",
                op = MathOp.MUL,
                a = tableNum,
                b = mult,
                answer = tableNum.toLong() * mult.toLong(),
                level = level
            )
        }

        val id = "cat_${categoryId}_${prng.int(1000, 999999)}"
        return when (categoryId) {
            "add_1d" -> {
                val a = prng.int(2, 9)
                val b = prng.int(2, 9)
                QuestionGenerator.buildStandardProblem(id, MathOp.ADD, a, b, (a + b).toLong(), level)
            }
            "add_2d_nocarry" -> {
                val tensA = prng.int(1, 7)
                val tensB = prng.int(1, (9 - tensA).coerceAtLeast(1))
                val unitsA = prng.int(1, 7)
                val unitsB = prng.int(1, (9 - unitsA).coerceAtLeast(1))
                val a = tensA * 10 + unitsA
                val b = tensB * 10 + unitsB
                QuestionGenerator.buildStandardProblem(id, MathOp.ADD, a, b, (a + b).toLong(), level)
            }
            "add_2d_carry" -> {
                val tensA = prng.int(1, 8)
                val tensB = prng.int(1, 8)
                val unitsA = prng.int(4, 9)
                val unitsB = prng.int((10 - unitsA).coerceAtLeast(2), 9)
                val a = tensA * 10 + unitsA
                val b = tensB * 10 + unitsB
                QuestionGenerator.buildStandardProblem(id, MathOp.ADD, a, b, (a + b).toLong(), level)
            }
            "add_3d" -> {
                val hA = prng.int(1, 6)
                val hB = prng.int(1, (9 - hA).coerceAtLeast(1))
                val tA = prng.int(1, 6)
                val tB = prng.int(1, (9 - tA).coerceAtLeast(1))
                val uA = prng.int(1, 6)
                val uB = prng.int(1, (9 - uA).coerceAtLeast(1))
                val a = hA * 100 + tA * 10 + uA
                val b = hB * 100 + tB * 10 + uB
                QuestionGenerator.buildStandardProblem(id, MathOp.ADD, a, b, (a + b).toLong(), level)
            }
            "add_3d_carry" -> {
                val a = prng.int(125, 895)
                val unitsA = a % 10
                val bUnits = prng.int((10 - unitsA).coerceIn(1, 9), 9)
                val b = prng.int(12, 79) * 10 + bUnits
                QuestionGenerator.buildStandardProblem(id, MathOp.ADD, a, b, (a + b).toLong(), level)
            }
            "add_large" -> {
                val a = prng.int(1050, 4800)
                val b = prng.int(1020, 4500)
                QuestionGenerator.buildStandardProblem(id, MathOp.ADD, a, b, (a + b).toLong(), level)
            }
            "sub_1d" -> {
                val a = prng.int(4, 9)
                val b = prng.int(1, a - 1)
                QuestionGenerator.buildStandardProblem(id, MathOp.SUB, a, b, (a - b).toLong(), level)
            }
            "sub_2d_noborrow" -> {
                val tensA = prng.int(3, 9)
                val tensB = prng.int(1, tensA - 1)
                val unitsA = prng.int(3, 9)
                val unitsB = prng.int(1, unitsA)
                val a = tensA * 10 + unitsA
                val b = tensB * 10 + unitsB
                QuestionGenerator.buildStandardProblem(id, MathOp.SUB, a, b, (a - b).toLong(), level)
            }
            "sub_2d_borrow" -> {
                val tensA = prng.int(3, 9)
                val tensB = prng.int(1, tensA - 1)
                val unitsA = prng.int(1, 6)
                val unitsB = prng.int(unitsA + 1, 9)
                val a = tensA * 10 + unitsA
                val b = tensB * 10 + unitsB
                QuestionGenerator.buildStandardProblem(id, MathOp.SUB, a, b, (a - b).toLong(), level)
            }
            "sub_3d" -> {
                val hA = prng.int(3, 9)
                val hB = prng.int(1, hA - 1)
                val tA = prng.int(3, 9)
                val tB = prng.int(1, tA)
                val uA = prng.int(3, 9)
                val uB = prng.int(1, uA)
                val a = hA * 100 + tA * 10 + uA
                val b = hB * 100 + tB * 10 + uB
                QuestionGenerator.buildStandardProblem(id, MathOp.SUB, a, b, (a - b).toLong(), level)
            }
            "sub_3d_borrow" -> {
                val hA = prng.int(3, 9)
                val hB = prng.int(1, hA - 1)
                val tA = prng.int(1, 7)
                val tB = prng.int(tA + 1, 9)
                val uA = prng.int(1, 6)
                val uB = prng.int(uA + 1, 9)
                val a = hA * 100 + tA * 10 + uA
                val b = hB * 100 + tB * 10 + uB
                QuestionGenerator.buildStandardProblem(id, MathOp.SUB, a, b, (a - b).toLong(), level)
            }
            "sub_large" -> {
                val a = prng.int(1500, 6500)
                val b = prng.int(1050, a - 120)
                QuestionGenerator.buildStandardProblem(id, MathOp.SUB, a, b, (a - b).toLong(), level)
            }
            "mul_table_2_5" -> {
                val a = prng.int(2, 5)
                val b = prng.int(2, 5)
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, (a * b).toLong(), level)
            }
            "mul_table_6_9" -> {
                val a = prng.int(6, 9)
                val b = prng.int(3, 9)
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, (a * b).toLong(), level)
            }
            "mul_table_10_12" -> {
                val a = prng.int(10, 12)
                val b = prng.int(3, 12)
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, (a * b).toLong(), level)
            }
            "mul_by_10_100" -> {
                val a = prng.int(12, 95)
                val b = if (prng.int(0, 1) == 0) 10 else 100
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, a.toLong() * b.toLong(), level)
            }
            "mul_2dx1d" -> {
                val a = prng.int(13, 98)
                val b = prng.int(3, 9)
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, (a * b).toLong(), level)
            }
            "mul_2dx2d" -> {
                val a = prng.int(12, 49)
                val b = prng.int(12, 39)
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, a.toLong() * b.toLong(), level)
            }
            "mul_3dx2d" -> {
                val a = prng.int(105, 350)
                val b = prng.int(12, 35)
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, a.toLong() * b.toLong(), level)
            }
            "mul_large" -> {
                val a = prng.int(120, 499)
                val b = prng.int(100, 250)
                QuestionGenerator.buildStandardProblem(id, MathOp.MUL, a, b, a.toLong() * b.toLong(), level)
            }
            "div_by_1d" -> {
                val divisor = prng.int(2, 9)
                val quotient = prng.int(3, 12)
                val dividend = divisor * quotient
                QuestionGenerator.buildStandardProblem(id, MathOp.DIV, dividend, divisor, quotient.toLong(), level)
            }
            "div_by_2d" -> {
                val divisor = prng.int(11, 25)
                val quotient = prng.int(4, 18)
                val dividend = divisor * quotient
                QuestionGenerator.buildStandardProblem(id, MathOp.DIV, dividend, divisor, quotient.toLong(), level)
            }
            "div_large" -> {
                val divisor = prng.int(26, 65)
                val quotient = prng.int(15, 45)
                val dividend = divisor * quotient
                QuestionGenerator.buildStandardProblem(id, MathOp.DIV, dividend, divisor, quotient.toLong(), level)
            }
            "sqrt_small" -> {
                val root = prng.int(2, 12)
                val sq = root * root
                Problem(id, MathOp.SQRT, sq, 2, root.toLong(), "√$sq", level = level, normalizedKey = "SQRT:$sq")
            }
            "sqrt_medium" -> {
                val root = prng.int(13, 25)
                val sq = root * root
                Problem(id, MathOp.SQRT, sq, 2, root.toLong(), "√$sq", level = level, normalizedKey = "SQRT:$sq")
            }
            "sqrt_large" -> {
                val root = prng.int(26, 45)
                val sq = root * root
                Problem(id, MathOp.SQRT, sq, 2, root.toLong(), "√$sq", level = level, normalizedKey = "SQRT:$sq")
            }
            "pow2_small" -> {
                val base = prng.int(2, 15)
                Problem(id, MathOp.POW, base, 2, (base * base).toLong(), "$base", superscriptExponent = "2", level = level, normalizedKey = "POW:$base:2")
            }
            "pow2_large" -> {
                val base = prng.int(16, 32)
                Problem(id, MathOp.POW, base, 2, (base * base).toLong(), "$base", superscriptExponent = "2", level = level, normalizedKey = "POW:$base:2")
            }
            "pow3" -> {
                val base = prng.int(2, 12)
                Problem(id, MathOp.POW, base, 3, (base * base * base).toLong(), "$base", superscriptExponent = "3", level = level, normalizedKey = "POW:$base:3")
            }
            "pow4" -> {
                val base = prng.int(2, 6)
                val ans = base.toLong() * base * base * base
                Problem(id, MathOp.POW, base, 4, ans, "$base", superscriptExponent = "4", level = level, normalizedKey = "POW:$base:4")
            }
            "cbrt_all" -> {
                val root = prng.int(2, 12)
                val cube = root * root * root
                Problem(id, MathOp.CBRT, cube, 3, root.toLong(), "∛$cube", level = level, normalizedKey = "CBRT:$cube")
            }
            else -> {
                val diff = Difficulty.fromIndex(((level - 1) / 2).coerceIn(0, 4))
                QuestionGenerator.generateCoreOpProblem(MathOp.ADD, diff, 0, prng)
            }
        }
    }
}
