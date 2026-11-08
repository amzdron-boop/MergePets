package com.lightgames.mergepets

import kotlin.math.pow

/** Static description of all pets (levels 1..MAX_LEVEL). */
object Pets {
    const val MAX_LEVEL = 14

    val emoji = arrayOf(
        "🥚", "🐣", "🐥", "🐹", "🐰", "🐱", "🐶",
        "🦊", "🐼", "🐨", "🦁", "🐯", "🐲", "🦄"
    )

    val nameRes = intArrayOf(
        R.string.pet_1, R.string.pet_2, R.string.pet_3, R.string.pet_4, R.string.pet_5,
        R.string.pet_6, R.string.pet_7, R.string.pet_8, R.string.pet_9, R.string.pet_10,
        R.string.pet_11, R.string.pet_12, R.string.pet_13, R.string.pet_14
    )

    /** Card color behind each pet. */
    val colors = intArrayOf(
        0xFFF4E9D8.toInt(), 0xFFFFE08A.toInt(), 0xFFFFC46B.toInt(), 0xFFFFAE7A.toInt(),
        0xFFFF9AA2.toInt(), 0xFFF78FB3.toInt(), 0xFFC99BFF.toInt(), 0xFF9BB5FF.toInt(),
        0xFF7FD1F5.toInt(), 0xFF6FE0C5.toInt(), 0xFF8EE88A.toInt(), 0xFFD6E86A.toInt(),
        0xFFFF7A6B.toInt(), 0xFFFFD700.toInt()
    )

    /** Base coins per second for one pet of [level] (before upgrades). Each level is 2.5x the previous. */
    fun income(level: Int): Double = 0.5 * 2.5.pow(level - 1)
}
