package com.example.focusflow.system

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.focusflow.MainActivity

/**
 * A full-screen warning drawn over a distracting app during a focus session.
 *
 * It sits on top of the other app, so that app cannot be used until the person leaves it
 * (back to AMU, the home screen) or ends the session. Needs the "Display over other apps"
 * permission; check [canShow] first. All view work happens on the main thread, so [show] and
 * [hide] are safe to call from any thread.
 */
class BlockOverlay(private val ctx: Context, private val onEndSession: () -> Unit) {

    companion object {
        fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        private val BG = Color.parseColor("#F20C0E08")      // app's dark background, almost opaque
        private val GREEN = Color.parseColor("#B4CC6A")
        private val AMBER = Color.parseColor("#F2B84B")
        private val TEXT = Color.parseColor("#F1F5F9")
        private val MUTED = Color.parseColor("#9CA3AF")
        private val ON_GREEN = Color.parseColor("#1D2807")
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm: WindowManager by lazy { ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    // Only touched on the main thread.
    private var root: View? = null
    private var message: TextView? = null
    private var timeLeft: TextView? = null

    /** After the person taps a button, don't pop back up while Android is still switching apps. */
    @Volatile private var quietUntil = 0L

    /** Shows the warning, or just refreshes its text if it is already showing. */
    fun show(appName: String, left: String) {
        if (SystemClock.elapsedRealtime() < quietUntil) return
        main.post {
            if (SystemClock.elapsedRealtime() < quietUntil) return@post
            if (root != null) {
                message?.text = "$appName is blocked while your focus session is running."
                timeLeft?.text = left
                return@post
            }
            try {
                val view = build(appName, left)
                wm.addView(view, params())
                root = view
            } catch (e: Exception) {
                // Permission was taken away or the window could not be added: nothing to block with.
                root = null; message = null; timeLeft = null
            }
        }
    }

    fun hide() {
        main.post { removeNow() }
    }

    private fun removeNow() {
        root?.let { v -> try { wm.removeView(v) } catch (e: Exception) { /* already gone */ } }
        root = null; message = null; timeLeft = null
    }

    private fun params(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable on purpose: that is how this window receives the Back key.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    /* ---------------------------------- actions ---------------------------------- */

    private fun leave(then: () -> Unit) {
        quietUntil = SystemClock.elapsedRealtime() + 2000
        removeNow()
        try { then() } catch (e: Exception) { /* could not switch apps */ }
    }

    private fun goHome() = leave {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openFocusFlow() = leave {
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("route", "focus")
        )
    }

    private fun endSession() = leave { onEndSession() }

    /* ------------------------------------ view ------------------------------------ */

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private class Root(c: Context, private val onBack: () -> Unit) : FrameLayout(c) {
        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (e.keyCode == KeyEvent.KEYCODE_BACK) {
                if (e.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(e)
        }
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(ctx).apply {
        text = s
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun build(appName: String, left: String): View {
        val r = Root(ctx) { goHome() }
        r.setBackgroundColor(BG)
        r.isFocusable = true
        r.isFocusableInTouchMode = true
        r.isClickable = true            // swallow every touch so nothing reaches the app underneath

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(72), dp(32), dp(72))
        }

        val tag = text("FOCUS MODE", 13f, AMBER, bold = true).apply { letterSpacing = 0.15f }
        val title = text("Stay focused", 32f, TEXT, bold = true)
        val msg = text("$appName is blocked while your focus session is running.", 17f, MUTED)
        val time = text(left, 44f, GREEN, bold = true)

        val primary = text("Back to AMU", 17f, ON_GREEN, bold = true).apply {
            background = GradientDrawable().apply { setColor(GREEN); cornerRadius = dp(32).toFloat() }
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setOnClickListener { openFocusFlow() }
        }
        val home = text("Go to home screen", 15f, TEXT).apply {
            setPadding(dp(24), dp(14), dp(24), dp(14))
            setOnClickListener { goHome() }
        }
        val end = text("End focus session", 14f, MUTED).apply {
            setPadding(dp(24), dp(14), dp(24), dp(14))
            setOnClickListener { endSession() }
        }

        fun lp(top: Int, matchWidth: Boolean = false) = LinearLayout.LayoutParams(
            if (matchWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

        col.addView(tag, lp(0))
        col.addView(title, lp(12))
        col.addView(msg, lp(12))
        col.addView(time, lp(28))
        col.addView(primary, lp(40, matchWidth = true))
        col.addView(home, lp(8))
        col.addView(end, lp(0))

        r.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        message = msg
        timeLeft = time
        return r
    }
}
