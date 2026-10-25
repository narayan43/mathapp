package com.example.core

import com.example.model.MathOp
import com.example.model.Problem
import com.example.model.SkillOp
import com.example.model.SkillState
import java.time.LocalDate
import kotlin.math.roundToInt

data class SmartPracticeItem(
    val problem: Problem,
    val category: String,
    val source: String // 'data-practice-mistake' | 'data-practice-new'
)

data class SmartPracticePlan(
    val items: List<SmartPracticeItem>,
    val categoryFilter: String?,
    val usedFallbackForFewMistakes: Boolean,
    val mistakeSlotCount: Int,
    val newSlotCount: Int,
    val beforeCategoryAccuracy: Map<String, Double>
)

data class CategoryBeforeAfterDelta(
    val categoryId: String,
    val beforeAccuracy: Double?, // null if category had 0 prior attempts
    val afterAccuracy: Double,
    val sessionCorrect: Int,
    val sessionTotal: Int
)

object SmartPracticeService {

    /**
     * Builds a 50/50 Smart Practice session of [questionCount] questions:
     * - 50% from the Mistake Book (prioritized by due spaced-repetition date, missCount, recency, and category weakness).
     *   If fewer mistakes exist than needed, fills the remainder with new questions from the user's weakest categories
     *   and flags [usedFallbackForFewMistakes] = true.
     * - 50% new questions using the explore/exploit rule:
     *   ~60% from top 3 weakest categories, ~25% from low-data categories (< 10 attempts),
     *   ~15% from a slightly harder level of strong categories.
     * - Deduplicates questions within the session and enforces max 2 identical categories in a row.
     */
    fun build(
        questionCount: Int = 20,
        categoryFilter: String? = null,
        mistakeBook: List<MistakeBookEntry>,
        categoryStats: List<CategoryStat>,
        skills: Map<SkillOp, SkillState> = emptyMap(),
        recentNormalizedKeys: Set<String> = emptySet(),
        todayStr: String = LocalDate.now().toString(),
        prng: SeededPrng = SeededPrng(System.nanoTime())
    ): SmartPracticePlan {
        val total = questionCount.coerceIn(4, 60)
        val targetMistakes = total / 2
        val targetNew = total - targetMistakes

        val statsByCat = categoryStats.associateBy { it.categoryId }
        val beforeAccMap = categoryStats.associate { it.categoryId to it.accuracy }

        // 1. Select up to targetMistakes from unresolved Mistake Book
        val unresolvedMistakes = mistakeBook.filter { entry ->
            !entry.isMastered && (categoryFilter == null || entry.category == categoryFilter)
        }

        // Prioritize due mistakes (nextReviewDay <= todayStr) first, then by missCount, category weakness, and recency
        val prioritizedMistakes = unresolvedMistakes.sortedWith(
            compareByDescending<MistakeBookEntry> { it.nextReviewDay <= todayStr }
                .thenByDescending { it.missCount }
                .thenByDescending { statsByCat[it.category]?.weaknessScore ?: 0.5 }
                .thenByDescending { it.lastMissedAt }
        )

        val usedNormalizedKeys = mutableSetOf<String>()
        val selectedItems = mutableListOf<SmartPracticeItem>()

        for (entry in prioritizedMistakes) {
            if (selectedItems.size >= targetMistakes) break
            if (entry.normalizedKey !in usedNormalizedKeys) {
                usedNormalizedKeys.add(entry.normalizedKey)
                selectedItems.add(
                    SmartPracticeItem(
                        problem = entry.problem.copy(id = "dp_mistake_${selectedItems.size}_${prng.int(100, 9999)}"),
                        category = entry.category,
                        source = "data-practice-mistake"
                    )
                )
            }
        }

        val actualMistakeCount = selectedItems.size
        val shortfall = (targetMistakes - actualMistakeCount).coerceAtLeast(0)
        val usedFallback = shortfall > 0

        // Determine weakest, low-data, and strong categories
        val allCoreCats = CategoryRegistry.CORE_CATEGORIES.map { it.id }
        val rankedWeakest = categoryStats
            .filter { it.attempts > 0 }
            .sortedByDescending { it.weaknessScore }
            .map { it.categoryId }
            .ifEmpty { listOf("add_2d_carry", "sub_2d_borrow", "mul_2dx1d") }

        val top3Weakest = rankedWeakest.take(3).ifEmpty { listOf("add_2d_carry", "sub_2d_borrow", "mul_table_6_9") }

        val lowDataCategories = allCoreCats.filter { catId ->
            (statsByCat[catId]?.attempts ?: 0) < 10
        }.ifEmpty { allCoreCats }

        val strongCategories = categoryStats
            .filter { it.attempts >= 5 && it.accuracy >= 0.85 }
            .sortedByDescending { it.accuracy }
            .map { it.categoryId }
            .ifEmpty { top3Weakest }

        // Helper to generate a non-duplicate new question in a target category
        fun generateUniqueForCategory(catId: String, levelBump: Int = 0): SmartPracticeItem {
            val catInfo = CategoryRegistry.getCategoryInfo(catId)
            val baseLevel = resolveSkillLevelForOp(catInfo.mathOp, skills)
            val targetLevel = (baseLevel + levelBump).coerceIn(1, 10)

            var chosen: Problem? = null
            for (attempt in 0 until 25) {
                val candidate = CategoryRegistry.generateProblemForCategory(catId, targetLevel, prng)
                val notInSession = candidate.normalizedKey !in usedNormalizedKeys
                val notRecent = candidate.normalizedKey !in recentNormalizedKeys || attempt > 12
                if (notInSession && notRecent) {
                    chosen = candidate
                    break
                }
            }
            val finalProb = chosen ?: CategoryRegistry.generateProblemForCategory(catId, targetLevel, prng)
            usedNormalizedKeys.add(finalProb.normalizedKey)
            return SmartPracticeItem(
                problem = finalProb.copy(id = "dp_new_${selectedItems.size}_${prng.int(100, 9999)}"),
                category = catId,
                source = "data-practice-new"
            )
        }

        // Fill any mistake shortfall from weakest categories (or categoryFilter)
        for (i in 0 until shortfall) {
            val catId = categoryFilter ?: top3Weakest[i % top3Weakest.size]
            selectedItems.add(generateUniqueForCategory(catId, levelBump = 0))
        }

        // 2. Fill the 50% New questions using the 60% weakest / 25% low-data / 15% strong+harder rule
        if (categoryFilter != null) {
            for (i in 0 until targetNew) {
                val bump = if (i % 4 == 3) 1 else 0
                selectedItems.add(generateUniqueForCategory(categoryFilter, levelBump = bump))
            }
        } else {
            val weakCount = (targetNew * 0.60).roundToInt().coerceAtLeast(1).coerceAtMost(targetNew)
            val lowDataCount = (targetNew * 0.25).roundToInt().coerceAtMost((targetNew - weakCount).coerceAtLeast(0))
            val strongHarderCount = (targetNew - weakCount - lowDataCount).coerceAtLeast(0)

            for (i in 0 until weakCount) {
                val catId = top3Weakest[i % top3Weakest.size]
                selectedItems.add(generateUniqueForCategory(catId, levelBump = 0))
            }
            for (i in 0 until lowDataCount) {
                val catId = lowDataCategories[prng.int(0, lowDataCategories.lastIndex)]
                selectedItems.add(generateUniqueForCategory(catId, levelBump = 0))
            }
            for (i in 0 until strongHarderCount) {
                val catId = strongCategories[i % strongCategories.size]
                selectedItems.add(generateUniqueForCategory(catId, levelBump = 2))
            }
        }

        // 3. Shuffle and enforce max 2 identical categories in a row
        val arranged = arrangeWithMaxTwoSameCategoryInARow(selectedItems, prng)

        return SmartPracticePlan(
            items = arranged,
            categoryFilter = categoryFilter,
            usedFallbackForFewMistakes = usedFallback,
            mistakeSlotCount = actualMistakeCount,
            newSlotCount = arranged.size - actualMistakeCount,
            beforeCategoryAccuracy = beforeAccMap
        )
    }

    /**
     * Shuffles [items] and repairs any run of 3+ identical categories in a row when multiple categories exist.
     */
    fun arrangeWithMaxTwoSameCategoryInARow(
        items: List<SmartPracticeItem>,
        prng: SeededPrng
    ): List<SmartPracticeItem> {
        if (items.size <= 2) return items
        val list = prng.shuffle(items).toMutableList()
        val distinctCategories = list.map { it.category }.toSet()
        if (distinctCategories.size <= 1) return list

        for (pass in 0 until 8) {
            var changed = false
            for (i in 2 until list.size) {
                if (list[i].category == list[i - 1].category && list[i].category == list[i - 2].category) {
                    val swapIdx = (0 until list.size).firstOrNull { j ->
                        list[j].category != list[i].category &&
                            (j == 0 || list[j - 1].category != list[i].category) &&
                            (j == list.lastIndex || list[j + 1].category != list[i].category)
                    }
                    if (swapIdx != null) {
                        val tmp = list[i]
                        list[i] = list[swapIdx]
                        list[swapIdx] = tmp
                        changed = true
                    }
                }
            }
            if (!changed) break
        }
        return list
    }

    private fun resolveSkillLevelForOp(
        op: MathOp?,
        skills: Map<SkillOp, SkillState>
    ): Int {
        val skillOp = when (op) {
            MathOp.ADD -> SkillOp.ADD
            MathOp.SUB -> SkillOp.SUB
            MathOp.MUL -> SkillOp.MUL
            MathOp.DIV -> SkillOp.DIV
            MathOp.SQRT -> SkillOp.SQRT
            MathOp.POW, MathOp.CBRT -> SkillOp.POW
            null -> SkillOp.ADD
        }
        return skills[skillOp]?.level ?: 3
    }
}
