package com.lightgames.mergepets

import java.util.Locale

object Format {
    private val suffixes = arrayOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp")

    fun num(v: Double): String {
        if (v.isNaN() || v <= 0.0) return "0"
        if (v < 1000) {
            return if (v < 10) String.format(Locale.US, "%.1f", v).removeSuffix(".0")
            else v.toLong().toString()
        }
        var x = v
        var i = 0
        while (x >= 999.995 && i < suffixes.size - 1) {
            x /= 1000.0
            i++
        }
        val pattern = when {
            x < 10 -> "%.2f"
            x < 100 -> "%.1f"
            else -> "%.0f"
        }
        return String.format(Locale.US, pattern, x) + suffixes[i]
    }
}
