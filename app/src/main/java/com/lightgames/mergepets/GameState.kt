package com.lightgames.mergepets

import android.content.Context
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class DropResult(
    val type: Int,
    val newLevel: Int = 0,
    val newPet: Boolean = false,
    val reward: Double = 0.0
) {
    companion object {
        const val NONE = 0
        const val MOVE = 1
        const val SWAP = 2
        const val MERGE = 3
    }
}

/** All game rules and persistent progress. No Android UI code in here. */
class GameState {

    companion object {
        const val COLS = 5
        const val ROWS = 6
        const val CELLS = COLS * ROWS
        const val GIFT = -1

        const val MAX_INCOME_LVL = 25
        const val MAX_SPAWN_LVL = 6

        const val OFFLINE_CAP_SEC = 4 * 3600.0
        const val OFFLINE_RATE = 0.5
        const val GIFT_LIFETIME_MS = 20_000L

        const val SPAWN_FAIL_FULL = -1
        const val SPAWN_FAIL_COINS = -2

        private const val PREFS = "game"
    }

    /** 0 = empty, 1..MAX_LEVEL = pet, GIFT = bonus gift. */
    val grid = IntArray(CELLS)
    val giftExpire = LongArray(CELLS)

    var coins = 60.0
    var incomeLvl = 0
    var spawnLvl = 0
    var maxLevel = 1
    var discovered = 1 shl 1 // bit N set = pet of level N discovered
    var totalMerges = 0

    var streak = 0
    var lastDailyDay = -1L

    var nextGiftAt = 0L
    var lastSave = System.currentTimeMillis()

    var sound = true
    var vibrate = true

    // ---------------------------------------------------------------- economy

    fun incomeMultiplier(): Double = 1.0 + 0.25 * incomeLvl

    fun incomePerSec(): Double {
        var sum = 0.0
        for (v in grid) if (v > 0) sum += Pets.income(v)
        return sum * incomeMultiplier()
    }

    fun spawnLevel(): Int = 1 + spawnLvl

    fun spawnCost(): Double = ceil(10.0 * 1.6.pow(maxLevel - 1) * 2.2.pow(spawnLvl))

    fun incomeUpgradeCost(): Double = ceil(150.0 * 2.3.pow(incomeLvl))

    fun spawnUpgradeCost(): Double = ceil(500.0 * 6.0.pow(spawnLvl))

    fun giftReward(): Double = max(50.0, incomePerSec() * 40.0)

    fun addIncome(dtSec: Double) {
        coins += incomePerSec() * dtSec
    }

    fun offlineEarnings(awayMs: Long): Double {
        val sec = min(awayMs / 1000.0, OFFLINE_CAP_SEC)
        return incomePerSec() * sec * OFFLINE_RATE
    }

    fun buyIncomeUpgrade(): Boolean {
        val cost = incomeUpgradeCost()
        if (incomeLvl >= MAX_INCOME_LVL || coins < cost) return false
        coins -= cost
        incomeLvl++
        return true
    }

    fun buySpawnUpgrade(): Boolean {
        val cost = spawnUpgradeCost()
        if (spawnLvl >= MAX_SPAWN_LVL || coins < cost) return false
        coins -= cost
        spawnLvl++
        markFound(spawnLevel())
        return true
    }

    // ------------------------------------------------------------------ board

    private fun emptyCells(): List<Int> = (0 until CELLS).filter { grid[it] == 0 }

    /** Returns cell index of the new pet, or a negative SPAWN_FAIL_* code. */
    fun spawn(): Int {
        val empties = emptyCells()
        if (empties.isEmpty()) return SPAWN_FAIL_FULL
        val cost = spawnCost()
        if (coins < cost) return SPAWN_FAIL_COINS
        coins -= cost
        val idx = empties[Random.nextInt(empties.size)]
        grid[idx] = spawnLevel()
        markFound(spawnLevel())
        return idx
    }

    private fun markFound(level: Int): Boolean {
        val bit = 1 shl level
        val isNew = (discovered and bit) == 0
        discovered = discovered or bit
        if (level > maxLevel) maxLevel = level
        return isNew
    }

    fun isFound(level: Int): Boolean = (discovered and (1 shl level)) != 0

    fun foundCount(): Int = (1..Pets.MAX_LEVEL).count { isFound(it) }

    fun drop(from: Int, to: Int): DropResult {
        if (from == to) return DropResult(DropResult.NONE)
        val a = grid[from]
        val b = grid[to]
        if (a <= 0) return DropResult(DropResult.NONE)
        return when {
            b == 0 -> {
                grid[to] = a
                grid[from] = 0
                DropResult(DropResult.MOVE)
            }
            b == a && a < Pets.MAX_LEVEL -> {
                val level = a + 1
                grid[to] = level
                grid[from] = 0
                totalMerges++
                val isNew = markFound(level)
                var reward = 0.0
                if (isNew) {
                    reward = max(50.0, Pets.income(level) * incomeMultiplier() * 120.0)
                    coins += reward
                }
                DropResult(DropResult.MERGE, level, isNew, reward)
            }
            b > 0 -> {
                grid[to] = a
                grid[from] = b
                DropResult(DropResult.SWAP)
            }
            else -> DropResult(DropResult.NONE) // dropped on a gift: bounce back
        }
    }

    fun collectGift(idx: Int): Double {
        if (grid[idx] != GIFT) return 0.0
        grid[idx] = 0
        val reward = giftReward()
        coins += reward
        return reward
    }

    /** Spawns/expires gifts. Returns the cell of a newly placed gift, or -1. */
    fun updateGifts(now: Long): Int {
        for (i in 0 until CELLS) {
            if (grid[i] == GIFT && now >= giftExpire[i]) grid[i] = 0
        }
        if (nextGiftAt == 0L) nextGiftAt = now + 30_000L
        if (now < nextGiftAt) return -1
        nextGiftAt = now + Random.nextLong(60_000L, 100_000L)
        val empties = emptyCells()
        if (empties.size < 2) return -1
        val idx = empties[Random.nextInt(empties.size)]
        grid[idx] = GIFT
        giftExpire[idx] = now + GIFT_LIFETIME_MS
        return idx
    }

    // ------------------------------------------------------------ daily bonus

    private fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    fun canClaimDaily(): Boolean = lastDailyDay != today()

    /** Day number 1..7 of the reward that is (or was last) available. */
    fun dailyDayIndex(): Int {
        val t = today()
        val s = when (lastDailyDay) {
            t -> streak
            t - 1 -> streak + 1
            else -> 1
        }
        return ((s - 1) % 7) + 1
    }

    fun dailyReward(dayIdx: Int): Double = (100.0 + incomePerSec() * 180.0) * dayIdx

    fun claimDaily(): Double {
        if (!canClaimDaily()) return 0.0
        val t = today()
        streak = if (lastDailyDay == t - 1) streak + 1 else 1
        lastDailyDay = t
        val reward = dailyReward(((streak - 1) % 7) + 1)
        coins += reward
        return reward
    }

    // ------------------------------------------------------------ persistence

    fun save(ctx: Context) {
        lastSave = System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("grid", grid.joinToString(",") { if (it < 0) "0" else it.toString() })
            putLong("coins", java.lang.Double.doubleToRawLongBits(coins))
            putInt("incomeLvl", incomeLvl)
            putInt("spawnLvl", spawnLvl)
            putInt("maxLevel", maxLevel)
            putInt("discovered", discovered)
            putInt("merges", totalMerges)
            putInt("streak", streak)
            putLong("lastDaily", lastDailyDay)
            putLong("lastSave", lastSave)
            putBoolean("sound", sound)
            putBoolean("vibrate", vibrate)
        }.apply()
    }

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val g = p.getString("grid", null)
        if (g == null) {
            // First launch: give the player two eggs so the very first merge is obvious.
            grid[12] = 1
            grid[17] = 1
            return
        }
        val parts = g.split(",")
        if (parts.size == CELLS) {
            for (i in 0 until CELLS) grid[i] = parts[i].toIntOrNull()?.coerceIn(0, Pets.MAX_LEVEL) ?: 0
        }
        coins = java.lang.Double.longBitsToDouble(p.getLong("coins", java.lang.Double.doubleToRawLongBits(60.0)))
        incomeLvl = p.getInt("incomeLvl", 0)
        spawnLvl = p.getInt("spawnLvl", 0)
        maxLevel = p.getInt("maxLevel", 1)
        discovered = p.getInt("discovered", 1 shl 1)
        totalMerges = p.getInt("merges", 0)
        streak = p.getInt("streak", 0)
        lastDailyDay = p.getLong("lastDaily", -1L)
        lastSave = p.getLong("lastSave", System.currentTimeMillis())
        sound = p.getBoolean("sound", true)
        vibrate = p.getBoolean("vibrate", true)
    }
}
