package com.lightgames.mergepets

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.min

class MainActivity : Activity(), BoardView.Listener {

    companion object {
        private const val GOLD = 0xFFFFD84D.toInt()
        private const val GREEN = 0xFF7CFF8A.toInt()
        private const val PANEL = 0xFF2B1B4A.toInt()
        private const val DIM = 0xFF4A3478.toInt()
        private var dailyShownThisProcess = false
    }

    private lateinit var state: GameState
    private lateinit var board: BoardView
    private lateinit var sfx: SoundFx

    private lateinit var coinsText: TextView
    private lateinit var incomeText: TextView
    private lateinit var hintText: TextView
    private lateinit var spawnBtn: LinearLayout
    private lateinit var spawnTitle: TextView
    private lateinit var spawnCost: TextView
    private lateinit var dailyBtn: TextView
    private lateinit var banner: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var lastTick = 0L
    private var lastAutosave = 0L
    private var activeDialog: Dialog? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    // ------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = GameState().also { it.load(this) }
        sfx = SoundFx(this)
        buildUi()
        updateHud()
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        lastTick = now
        lastAutosave = now
        board.running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
        checkOffline(now)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        board.running = false
        state.save(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeDialog?.dismiss()
        sfx.release()
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val dt = min(1.0, (now - lastTick) / 1000.0)
            lastTick = now
            state.addIncome(dt)
            val gift = state.updateGifts(now)
            if (gift >= 0) {
                board.popCell(gift)
                sfxPlay("coin", 1.3f)
            }
            updateHud()
            if (now - lastAutosave > 10_000) {
                state.save(this@MainActivity)
                lastAutosave = now
            }
            handler.postDelayed(this, 100)
        }
    }

    // ------------------------------------------------------------------ UI

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dpf(v: Float): Float = v * resources.displayMetrics.density

    private fun bg(color: Int, radiusDp: Float, strokeColor: Int = 0, strokeDp: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpf(radiusDp)
            if (strokeColor != 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun lp(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(l, t, r, b) }

    private fun tv(
        text: String, sp: Float, color: Int = Color.WHITE,
        bold: Boolean = false, center: Boolean = false
    ) = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (center) gravity = Gravity.CENTER
    }

    private fun pressable(v: View, onClick: () -> Unit) {
        v.isClickable = true
        v.setOnClickListener { onClick() }
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun button(text: String, color: Int, textColor: Int = Color.WHITE, onClick: () -> Unit) =
        tv(text, 17f, textColor, bold = true, center = true).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = bg(color, 16f)
            pressable(this, onClick)
        }

    @Suppress("DEPRECATION")
    private fun buildUi() {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF1B1035.toInt(), 0xFF3B1D6E.toInt())
            )
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            root.setOnApplyWindowInsetsListener { v, ins ->
                val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
                WindowInsets.CONSUMED
            }
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        // top bar: coins + income + settings
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        coinsText = tv("", 30f, GOLD, bold = true)
        incomeText = tv("", 15f, GREEN, bold = true)
        left.addView(coinsText)
        left.addView(incomeText)
        top.addView(left, lp(0, -2, 1f))
        val gear = tv("⚙️", 22f, center = true).apply {
            background = bg(0x33FFFFFF, 24f)
            pressable(this) { showSettings() }
        }
        top.addView(gear, lp(dp(46), dp(46)))
        column.addView(top, lp(-1, -2))

        // board
        board = BoardView(this, state).also { it.listener = this }
        column.addView(board, lp(-1, 0, 1f, t = dp(8), b = dp(4)))

        hintText = tv(getString(R.string.hint), 14f, 0xCCFFFFFF.toInt(), center = true)
        column.addView(hintText, lp(-1, -2, b = dp(8)))

        // big spawn button
        spawnBtn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFFF9F43.toInt(), 0xFFFF5C8A.toInt())
            ).apply { cornerRadius = dpf(20f) }
        }
        spawnTitle = tv("", 21f, bold = true, center = true)
        spawnCost = tv("", 16f, 0xFFFFF3C4.toInt(), bold = true, center = true)
        spawnBtn.addView(spawnTitle)
        spawnBtn.addView(spawnCost)
        pressable(spawnBtn) { onSpawnClicked() }
        column.addView(spawnBtn, lp(-1, -2))

        // bottom row
        val row = LinearLayout(this)
        val upg = button("⬆️ " + getString(R.string.upgrades), 0xFF3F7DE0.toInt()) { showUpgrades() }
        val pets = button("📖 " + getString(R.string.pets_book), 0xFF8E5BE0.toInt()) { showPets() }
        dailyBtn = button("🎁 " + getString(R.string.daily), 0xFF2FA97A.toInt()) { showDaily() }
        for (b in listOf(upg, pets, dailyBtn)) b.textSize = 14f
        row.addView(upg, lp(0, -2, 1f, r = dp(6)))
        row.addView(pets, lp(0, -2, 1f, l = dp(3), r = dp(3)))
        row.addView(dailyBtn, lp(0, -2, 1f, l = dp(6)))
        column.addView(row, lp(-1, -2, t = dp(10)))

        // floating banner
        banner = tv("", 16f, Color.WHITE, bold = true, center = true).apply {
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = bg(0xEE2B1B4A.toInt(), 22f, GOLD, 2)
            alpha = 0f
            elevation = dpf(8f)
        }
        root.addView(
            banner,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(120) }
        )

        setContentView(root)
    }

    private val hideBanner = Runnable { banner.animate().alpha(0f).setDuration(300).start() }

    private fun showBanner(text: String) {
        banner.text = text
        handler.removeCallbacks(hideBanner)
        banner.animate().cancel()
        banner.animate().alpha(1f).setDuration(150).start()
        handler.postDelayed(hideBanner, 2200)
    }

    private fun setIfChanged(t: TextView, s: String) {
        if (t.text.toString() != s) t.text = s
    }

    private fun updateHud() {
        setIfChanged(coinsText, "🪙 " + Format.num(state.coins))
        setIfChanged(incomeText, getString(R.string.income_hud, Format.num(state.incomePerSec())))
        setIfChanged(spawnTitle, Pets.emoji[state.spawnLevel() - 1] + "  " + getString(R.string.get_pet))
        val cost = state.spawnCost()
        setIfChanged(spawnCost, "🪙 " + Format.num(cost))
        spawnBtn.alpha = if (state.coins >= cost) 1f else 0.6f
        setIfChanged(
            dailyBtn,
            "🎁 " + getString(R.string.daily) + if (state.canClaimDaily()) " ❗" else ""
        )
        hintText.visibility = if (state.totalMerges < 3) View.VISIBLE else View.INVISIBLE
    }

    // --------------------------------------------------------- sound / haptic

    private fun sfxPlay(name: String, rate: Float = 1f) {
        if (state.sound) sfx.play(name, rate)
    }

    private fun haptic() {
        if (state.vibrate) board.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    // ------------------------------------------------------------ game events

    private fun onSpawnClicked() {
        val r = state.spawn()
        when {
            r >= 0 -> {
                board.popCell(r)
                board.burstAt(r, 0xFFFFFFFF.toInt())
                sfxPlay("spawn")
                haptic()
            }
            r == GameState.SPAWN_FAIL_FULL -> {
                showBanner(getString(R.string.board_full))
                sfxPlay("error")
            }
            else -> {
                showBanner(getString(R.string.not_enough))
                sfxPlay("error")
            }
        }
        updateHud()
    }

    override fun onMerged(level: Int, newPet: Boolean, reward: Double) {
        sfxPlay("merge", 0.8f + level * 0.06f)
        haptic()
        if (newPet) {
            showBanner(
                getString(R.string.new_pet, getString(Pets.nameRes[level - 1]), Format.num(reward))
            )
            sfxPlay("coin")
        }
        updateHud()
    }

    override fun onMoved() {
        sfxPlay("move")
    }

    override fun onGiftTaken(reward: Double) {
        sfxPlay("coin", 1.2f)
        haptic()
        showBanner(getString(R.string.gift_got, Format.num(reward)))
        updateHud()
    }

    // ---------------------------------------------------------------- dialogs

    private fun showDialog(
        title: String,
        closeLabel: String? = getString(R.string.close),
        onDismiss: (() -> Unit)? = null,
        content: (LinearLayout, Dialog) -> Unit
    ): Dialog {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = bg(PANEL, 26f, 0xFF6A4BB5.toInt(), 2)
        }
        panel.addView(tv(title, 22f, GOLD, bold = true, center = true), lp(-1, -2, b = dp(12)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(body, lp(-1, -2))
        content(body, dlg)
        if (closeLabel != null) {
            panel.addView(button(closeLabel, 0xFF6A4BB5.toInt()) { dlg.dismiss() }, lp(-1, -2, t = dp(14)))
        }
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dlg.setOnDismissListener {
            if (activeDialog === dlg) activeDialog = null
            onDismiss?.invoke()
        }
        activeDialog = dlg
        dlg.show()
        return dlg
    }

    private fun checkOffline(now: Long) {
        if (activeDialog?.isShowing == true) return
        val away = now - state.lastSave
        val earned = if (away > 60_000) state.offlineEarnings(away) else 0.0
        if (earned >= 1.0) {
            state.coins += earned
            state.lastSave = now
            updateHud()
            sfxPlay("coin")
            showDialog(
                getString(R.string.welcome_back),
                closeLabel = getString(R.string.collect),
                onDismiss = { maybeShowDaily() }
            ) { body, _ ->
                body.addView(tv("🪙", 56f, center = true))
                body.addView(
                    tv(getString(R.string.offline_text, Format.num(earned)), 17f, center = true),
                    lp(-1, -2, t = dp(4))
                )
            }
        } else {
            maybeShowDaily()
        }
    }

    private fun maybeShowDaily() {
        if (dailyShownThisProcess) return
        dailyShownThisProcess = true
        if (state.canClaimDaily()) showDaily()
    }

    private fun showDaily() {
        showDialog(getString(R.string.daily_title), onDismiss = { updateHud() }) { body, dlg ->
            val canClaim = state.canClaimDaily()
            val idx = state.dailyDayIndex()

            val days = LinearLayout(this)
            for (d in 1..7) {
                val claimed = d < idx || (d == idx && !canClaim)
                val today = d == idx && canClaim
                val chip = tv(if (claimed) "✓" else d.toString(), 16f, if (today) 0xFF3A2400.toInt() else Color.WHITE, bold = true, center = true)
                chip.background = bg(
                    when {
                        today -> GOLD
                        claimed -> 0xFF2FA97A.toInt()
                        else -> DIM
                    }, 12f
                )
                days.addView(chip, lp(0, dp(44), 1f, l = dp(2), r = dp(2)))
            }
            body.addView(days, lp(-1, -2))

            val reward = state.dailyReward(idx)
            body.addView(tv("🪙", 52f, center = true), lp(-1, -2, t = dp(12)))
            body.addView(tv(getString(R.string.daily_day, idx), 16f, 0xCCFFFFFF.toInt(), center = true))
            if (canClaim) {
                body.addView(
                    button(getString(R.string.daily_claim, Format.num(reward)), 0xFF2FA97A.toInt()) {
                        val got = state.claimDaily()
                        sfxPlay("coin")
                        haptic()
                        dlg.dismiss()
                        updateHud()
                        showBanner(getString(R.string.daily_got, Format.num(got)))
                    },
                    lp(-1, -2, t = dp(12))
                )
            } else {
                body.addView(
                    tv(getString(R.string.daily_claimed), 15f, 0xCCFFFFFF.toInt(), center = true),
                    lp(-1, -2, t = dp(12))
                )
            }
        }
    }

    private fun showPets() {
        showDialog(getString(R.string.pets_book)) { body, _ ->
            body.addView(
                tv(getString(R.string.found_count, state.foundCount(), Pets.MAX_LEVEL), 15f, GREEN, bold = true, center = true),
                lp(-1, -2, b = dp(8))
            )
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (level in 1..Pets.MAX_LEVEL) {
                val found = state.isFound(level)
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = bg(0x22FFFFFF, 14f)
                }
                val icon = tv(if (found) Pets.emoji[level - 1] else "❓", 28f, center = true).apply {
                    background = bg(if (found) Pets.colors[level - 1] else DIM, 12f)
                }
                row.addView(icon, lp(dp(50), dp(50)))
                val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                texts.addView(
                    tv(if (found) getString(Pets.nameRes[level - 1]) else getString(R.string.unknown_pet), 17f, bold = true)
                )
                texts.addView(tv(getString(R.string.lv, level), 13f, 0x99FFFFFF.toInt()))
                row.addView(texts, lp(0, -2, 1f, l = dp(12)))
                if (found) {
                    val inc = Pets.income(level) * state.incomeMultiplier()
                    row.addView(tv("🪙 " + getString(R.string.per_sec, Format.num(inc)), 14f, GOLD, bold = true))
                }
                list.addView(row, lp(-1, -2, b = dp(6)))
            }
            val scroll = ScrollView(this).apply { addView(list) }
            body.addView(scroll, lp(-1, (resources.displayMetrics.heightPixels * 0.5f).toInt()))
        }
    }

    private fun showUpgrades() {
        showDialog(getString(R.string.upgrades)) { body, _ -> renderUpgrades(body) }
    }

    private fun renderUpgrades(body: LinearLayout) {
        body.removeAllViews()
        body.addView(tv("🪙 " + Format.num(state.coins), 24f, GOLD, bold = true, center = true), lp(-1, -2, b = dp(10)))

        fun card(
            title: String, level: String, desc: String, cost: Double, maxed: Boolean, buy: () -> Boolean
        ) {
            val card = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = bg(0x22FFFFFF, 16f)
            }
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(tv(title, 17f, bold = true))
            texts.addView(tv(level, 13f, GREEN, bold = true))
            texts.addView(tv(desc, 13f, 0xCCFFFFFF.toInt()))
            card.addView(texts, lp(0, -2, 1f))
            val affordable = state.coins >= cost
            val btn = button(
                if (maxed) getString(R.string.upg_max) else "🪙 " + Format.num(cost),
                if (maxed) DIM else if (affordable) 0xFFFF9F43.toInt() else 0xFF5A4690.toInt()
            ) {
                if (!maxed) {
                    if (buy()) {
                        sfxPlay("coin")
                        haptic()
                        updateHud()
                        renderUpgrades(body)
                    } else {
                        sfxPlay("error")
                    }
                }
            }
            btn.textSize = 15f
            card.addView(btn, lp(-2, -2, l = dp(10)))
            body.addView(card, lp(-1, -2, b = dp(8)))
        }

        card(
            getString(R.string.upg_income),
            getString(R.string.upg_level, state.incomeLvl + 1),
            getString(R.string.upg_income_desc),
            state.incomeUpgradeCost(),
            state.incomeLvl >= GameState.MAX_INCOME_LVL
        ) { state.buyIncomeUpgrade() }

        val nextEmoji = Pets.emoji[min(state.spawnLevel(), Pets.MAX_LEVEL - 1)]
        card(
            getString(R.string.upg_spawn),
            getString(R.string.upg_level, state.spawnLvl + 1),
            getString(R.string.upg_spawn_desc) + "  → " + nextEmoji,
            state.spawnUpgradeCost(),
            state.spawnLvl >= GameState.MAX_SPAWN_LVL
        ) { state.buySpawnUpgrade() }
    }

    private fun showSettings() {
        showDialog(getString(R.string.settings)) { body, dlg ->
            fun toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
                val sw = Switch(this).apply {
                    text = label
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    isChecked = checked
                    setPadding(0, dp(8), 0, dp(8))
                    setOnCheckedChangeListener { _, v -> onChange(v) }
                }
                body.addView(sw, lp(-1, -2))
            }
            toggle(getString(R.string.sound), state.sound) { state.sound = it }
            toggle(getString(R.string.vibration), state.vibrate) { state.vibrate = it }

            body.addView(tv(getString(R.string.language), 16f, 0xCCFFFFFF.toInt(), bold = true), lp(-1, -2, t = dp(12), b = dp(6)))
            val langRow = LinearLayout(this)
            val current = LocaleHelper.get(this)
            val options = listOf(
                "" to getString(R.string.lang_system),
                "en" to getString(R.string.lang_en),
                "ru" to getString(R.string.lang_ru)
            )
            for ((code, label) in options) {
                val selected = code == current
                val chip = button(label, if (selected) 0xFFFF9F43.toInt() else DIM) {
                    if (code != current) {
                        LocaleHelper.set(this, code)
                        state.save(this)
                        dlg.dismiss()
                        recreate()
                    }
                }
                chip.textSize = 14f
                langRow.addView(chip, lp(0, -2, 1f, l = dp(3), r = dp(3)))
            }
            body.addView(langRow, lp(-1, -2))
        }
    }
}
