package com.lightgames.mergepets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** The merge board: draws pets, handles drag & drop, plays merge effects. */
class BoardView(ctx: Context, private val state: GameState) : View(ctx) {

    interface Listener {
        fun onMerged(level: Int, newPet: Boolean, reward: Double)
        fun onMoved()
        fun onGiftTaken(reward: Double)
    }

    var listener: Listener? = null

    /** Frame loop runs only while the screen is visible. */
    var running = false
        set(value) {
            field = value
            if (value) {
                lastFrame = SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }
        }

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )

    private class FloatText(val text: String, var x: Float, var y: Float, var life: Float)

    private val particles = ArrayList<Particle>()
    private val texts = ArrayList<FloatText>()

    private val cols = GameState.COLS
    private val rows = GameState.ROWS
    private var cell = 0f
    private var ox = 0f
    private var oy = 0f
    private var lastFrame = 0L

    private val popAt = LongArray(GameState.CELLS)

    private var dragFrom = -1
    private var dragX = 0f
    private var dragY = 0f

    private val rect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        isFakeBoldText = true
    }
    private val floatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFE066.toInt()
        isFakeBoldText = true
        setShadowLayer(6f, 0f, 2f, 0xFF000000.toInt())
    }

    // ------------------------------------------------------------ public API

    fun popCell(idx: Int) {
        popAt[idx] = SystemClock.uptimeMillis()
    }

    fun burstAt(idx: Int, color: Int) {
        val cx = ox + (idx % cols + 0.5f) * cell
        val cy = oy + (idx / cols + 0.5f) * cell
        repeat(14) {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val speed = cell * (1.2f + Random.nextFloat() * 2.2f)
            val life = 0.45f + Random.nextFloat() * 0.35f
            particles.add(
                Particle(
                    cx, cy, cos(ang) * speed, sin(ang) * speed - cell * 0.6f,
                    life, life, color, cell * (0.04f + Random.nextFloat() * 0.05f)
                )
            )
        }
    }

    fun floatText(idx: Int, text: String) {
        val cx = ox + (idx % cols + 0.5f) * cell
        val cy = oy + (idx / cols + 0.5f) * cell
        texts.add(FloatText(text, cx, cy, 1.2f))
    }

    // ---------------------------------------------------------------- layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cell = min(w.toFloat() / cols, h.toFloat() / rows)
        ox = (w - cell * cols) / 2f
        oy = (h - cell * rows) / 2f
        emojiPaint.textSize = cell * 0.5f
        badgeText.textSize = cell * 0.16f
        floatPaint.textSize = cell * 0.3f
        stroke.strokeWidth = max(3f, cell * 0.04f)
    }

    private fun cellAt(x: Float, y: Float): Int {
        val c = ((x - ox) / cell).toInt()
        val r = ((y - oy) / cell).toInt()
        if (x < ox || y < oy || c !in 0 until cols || r !in 0 until rows) return -1
        return r * cols + c
    }

    // --------------------------------------------------------------- drawing

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }

    private fun popScale(idx: Int, now: Long): Float {
        val t = (now - popAt[idx]) / 380f
        return if (t in 0f..1f) 0.4f + 0.6f * easeOutBack(t) else 1f
    }

    private fun drawPet(c: Canvas, cx: Float, cy: Float, level: Int, scale: Float) {
        val half = cell * 0.5f - cell * 0.07f
        val h = half * scale
        rect.set(cx - h, cy - h, cx + h, cy + h)
        fill.color = Pets.colors[level - 1]
        c.drawRoundRect(rect, cell * 0.18f * scale, cell * 0.18f * scale, fill)

        emojiPaint.textSize = cell * 0.5f * scale
        val fm = emojiPaint.fontMetrics
        c.drawText(Pets.emoji[level - 1], cx, cy - (fm.ascent + fm.descent) / 2f - cell * 0.02f, emojiPaint)

        // level badge (bottom right)
        val br = cell * 0.11f * scale
        val bx = cx + h - br - cell * 0.01f
        val by = cy + h - br - cell * 0.01f
        fill.color = 0xCC2A1646.toInt()
        c.drawCircle(bx, by, br, fill)
        badgeText.textSize = cell * 0.15f * scale
        val bfm = badgeText.fontMetrics
        c.drawText(level.toString(), bx, by - (bfm.ascent + bfm.descent) / 2f, badgeText)
    }

    private fun drawGift(c: Canvas, idx: Int, cx: Float, cy: Float, now: Long) {
        val remaining = state.giftExpire[idx] - now
        if (remaining < 5000 && (now / 250) % 2 == 0L) return // blink before disappearing
        val pulse = 1f + 0.08f * sin(now / 180f)
        val s = popScale(idx, now) * pulse
        val half = (cell * 0.5f - cell * 0.07f) * s
        rect.set(cx - half, cy - half, cx + half, cy + half)
        fill.color = 0xFFFF5C8A.toInt()
        c.drawRoundRect(rect, cell * 0.18f, cell * 0.18f, fill)
        emojiPaint.textSize = cell * 0.5f * s
        val fm = emojiPaint.fontMetrics
        c.drawText("🎁", cx, cy - (fm.ascent + fm.descent) / 2f, emojiPaint)
    }

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = min(0.05f, (now - lastFrame) / 1000f)
        lastFrame = now

        val dragLevel = if (dragFrom >= 0) state.grid[dragFrom] else 0
        // the dragged pet floats above the finger, so the drop cell is measured from the pet center
        val target = if (dragFrom >= 0) cellAt(dragX, dragY - cell * 0.25f) else -1

        for (i in 0 until GameState.CELLS) {
            val col = i % cols
            val row = i / cols
            val g = cell * 0.04f
            rect.set(ox + col * cell + g, oy + row * cell + g, ox + (col + 1) * cell - g, oy + (row + 1) * cell - g)

            fill.color = 0x26FFFFFF
            c.drawRoundRect(rect, cell * 0.18f, cell * 0.18f, fill)

            if (i == target && i != dragFrom) {
                val canMerge = state.grid[i] == dragLevel && dragLevel < Pets.MAX_LEVEL
                stroke.color = if (canMerge) 0xFF7CFF8A.toInt() else 0x80FFFFFF.toInt()
                c.drawRoundRect(rect, cell * 0.18f, cell * 0.18f, stroke)
            }

            val v = state.grid[i]
            if (v == 0 || i == dragFrom) continue
            val cx = rect.centerX()
            val bob = sin(now / 450f + i * 1.7f) * cell * 0.012f
            val cy = rect.centerY() + bob
            if (v == GameState.GIFT) drawGift(c, i, cx, cy, now) else drawPet(c, cx, cy, v, popScale(i, now))
        }

        if (dragFrom >= 0 && dragLevel > 0) {
            drawPet(c, dragX, dragY - cell * 0.25f, dragLevel, 1.15f)
        }

        // particles
        val pi = particles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dt
            if (p.life <= 0f) { pi.remove(); continue }
            p.vy += cell * 4f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            val a = (p.life / p.maxLife)
            fill.color = (p.color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
            c.drawCircle(p.x, p.y, p.size * (0.4f + a), fill)
        }

        // floating texts
        val ti = texts.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            t.life -= dt
            if (t.life <= 0f) { ti.remove(); continue }
            t.y -= cell * 0.6f * dt
            floatPaint.alpha = (min(1f, t.life * 2f) * 255).toInt()
            c.drawText(t.text, t.x, t.y, floatPaint)
        }

        if (running) postInvalidateOnAnimation()
    }

    // ----------------------------------------------------------------- touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = cellAt(e.x, e.y)
                if (idx < 0) return true
                val v = state.grid[idx]
                if (v == GameState.GIFT) {
                    val reward = state.collectGift(idx)
                    burstAt(idx, 0xFFFFE066.toInt())
                    floatText(idx, "+" + Format.num(reward))
                    listener?.onGiftTaken(reward)
                } else if (v > 0) {
                    dragFrom = idx
                    dragX = e.x
                    dragY = e.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                dragX = e.x
                dragY = e.y
            }
            MotionEvent.ACTION_UP -> {
                if (dragFrom >= 0) {
                    dragX = e.x
                    dragY = e.y
                    finishDrop(dragFrom, cellAt(dragX, dragY - cell * 0.25f))
                }
                dragFrom = -1
            }
            MotionEvent.ACTION_CANCEL -> dragFrom = -1
        }
        return true
    }

    private fun finishDrop(from: Int, to: Int) {
        if (to < 0) return
        val res = state.drop(from, to)
        when (res.type) {
            DropResult.MERGE -> {
                popCell(to)
                burstAt(to, Pets.colors[res.newLevel - 1])
                if (res.newPet) floatText(to, "NEW!")
                listener?.onMerged(res.newLevel, res.newPet, res.reward)
            }
            DropResult.MOVE -> {
                popCell(to)
                listener?.onMoved()
            }
            DropResult.SWAP -> {
                popCell(to)
                popCell(from)
                listener?.onMoved()
            }
        }
    }
}
