package com.uplink.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

class TerminalView(ctx: Context) : View(ctx) {
    private val invalidator: () -> Unit = { postInvalidateOnAnimation() }

    var session: TermSession? = null
        set(v) {
            if (field === v) return
            if (field?.onChange === invalidator) field?.onChange = null
            field = v
            if (isAttachedToWindow) v?.onChange = invalidator
            lastHist = v?.term?.history?.size ?: 0
            fit()
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = FontPrefs.mono.typeface(ctx)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TermZoom.sp, ctx.resources.displayMetrics)
    }
    private val fill = Paint()
    private var cellW = 1f
    private var cellH = 1f
    private var base = 0f
    private var lastHist = 0
    private var lastFirst = 0
    private var runBuf = CharArray(256)
    private val one = CharArray(1)
    private var selA = -1L
    private var selB = -1L
    private var selecting = false
    private var scrollAcc = 0f
    private var composed = ""
    private var menu: PopupWindow? = null

    private var shownSp = TermZoom.sp
    private var pinching = false

    private val pinch = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            pinching = true
            closeMenu()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (shownSp * detector.scaleFactor).coerceIn(TermZoom.MIN, TermZoom.MAX)
            if (kotlin.math.abs(next - shownSp) >= 0.25f) {
                TermZoom.set(context, next)
                setSizeSp(next)
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            pinching = false
        }
    })

    private fun measure() {
        val fm = paint.fontMetrics
        cellW = paint.measureText("M")
        cellH = Math.ceil((fm.descent - fm.ascent).toDouble()).toFloat()
        base = -fm.ascent
    }

    fun setSizeSp(sp: Float) {
        if (kotlin.math.abs(sp - shownSp) < 0.01f) return
        shownSp = sp
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
        measure()
        fit()
        invalidate()
    }

    init {
        measure()
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session?.onChange = invalidator
    }

    override fun onDetachedFromWindow() {
        closeMenu()
        if (session?.onChange === invalidator) session?.onChange = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fit()
    }

    private fun fit() {
        if (width <= 0 || height <= 0) return
        session?.resize(maxOf(2, (width / cellW).toInt()), maxOf(1, (height / cellH).toInt()))
    }

    private fun fgOf(l: Line, j: Int): Int {
        val inv = l.fl[j] and 4 != 0
        return if (inv) (if (l.bg[j] == 0) DEF_BG else l.bg[j]) else (if (l.fg[j] == 0) DEF_FG else l.fg[j])
    }

    private fun bgOf(l: Line, j: Int): Int {
        val inv = l.fl[j] and 4 != 0
        return if (inv) (if (l.fg[j] == 0) DEF_FG else l.fg[j]) else l.bg[j]
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(DEF_BG)
        val s = session ?: return
        val t = s.term
        synchronized(t) {
            val hs = t.history.size
            if (s.scrollOff > 0 && hs > lastHist) s.scrollOff += hs - lastHist
            lastHist = hs
            s.scrollOff = s.scrollOff.coerceIn(0, hs)
            val first = hs - s.scrollOff
            lastFirst = first
            if (runBuf.size < t.cols) runBuf = CharArray(t.cols)
            val a = minOf(selA, selB)
            val b = maxOf(selA, selB)
            for (r in 0 until t.rows) {
                val idx = first + r
                if (idx >= hs + t.rows) break
                val l = t.lineAt(idx)
                val y = r * cellH
                val n = minOf(l.ch.size, t.cols)
                var j = 0
                while (j < n) {
                    val bg = bgOf(l, j)
                    var k = j + 1
                    while (k < n && bgOf(l, k) == bg) k++
                    if (bg != 0) { fill.color = bg; canvas.drawRect(j * cellW, y, k * cellW, y + cellH, fill) }
                    j = k
                }
                if (selA >= 0) {
                    val ia = (a / 4096).toInt()
                    val ib = (b / 4096).toInt()
                    if (idx in ia..ib) {
                        val c0 = if (idx == ia) (a % 4096).toInt() else 0
                        val c1 = if (idx == ib) (b % 4096).toInt() else t.cols - 1
                        fill.color = SEL
                        canvas.drawRect(c0 * cellW, y, (c1 + 1) * cellW, y + cellH, fill)
                    }
                }
                j = 0
                while (j < n) {
                    val code = l.ch[j]
                    if (code <= 32) { j++; continue }
                    val fg = fgOf(l, j)
                    val bold = l.fl[j] and 1 != 0
                    val ul = l.fl[j] and 2 != 0
                    paint.color = fg
                    paint.isFakeBoldText = bold
                    paint.isUnderlineText = ul
                    if (code > 126) {
                        one[0] = code.toChar()
                        canvas.drawText(one, 0, 1, j * cellW, y + base, paint)
                        j++
                        continue
                    }
                    var k = j
                    var len = 0
                    while (k < n) {
                        val cc = l.ch[k]
                        if (cc < 32 || cc > 126) break
                        if (fgOf(l, k) != fg || (l.fl[k] and 1 != 0) != bold || (l.fl[k] and 2 != 0) != ul) break
                        runBuf[len++] = cc.toChar()
                        k++
                    }
                    canvas.drawText(runBuf, 0, len, j * cellW, y + base, paint)
                    j = k
                }
            }
            if (s.scrollOff == 0 && t.cursorVisible && hasFocus()) {
                fill.color = CURSOR
                canvas.drawRect(t.cx * cellW, t.cy * cellH, (t.cx + 1) * cellW, (t.cy + 1) * cellH, fill)
            }
        }
    }

    private val scroller = android.widget.OverScroller(ctx)
    private var flingLast = 0

    private val flingStep = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) return
            val y = scroller.currY
            applyScroll((y - flingLast).toFloat())
            flingLast = y
            postOnAnimation(this)
        }
    }

    private fun applyScroll(px: Float) {
        scrollAcc += px
        val lines = (scrollAcc / cellH).toInt()
        if (lines != 0) {
            scrollAcc -= lines * cellH
            session?.scroll(-lines)
        }
    }

    private var anchorX = 0f
    private var anchorY = 0f

    private fun wordAt(key: Long): Pair<Long, Long>? {
        val t = session?.term ?: return null
        val idx = (key / 4096).toInt()
        val col = (key % 4096).toInt()
        synchronized(t) {
            if (idx >= t.history.size + t.rows) return null
            val l = t.lineAt(idx)
            val n = minOf(l.ch.size, t.cols)
            if (col >= n || l.ch[col] <= 32) return null
            var a = col
            var b = col
            while (a > 0 && l.ch[a - 1] > 32) a--
            while (b < n - 1 && l.ch[b + 1] > 32) b++
            return (idx * 4096L + a) to (idx * 4096L + b)
        }
    }

    private fun closeMenu() {
        menu?.dismiss()
        menu = null
    }

    private fun clearSelection() {
        selA = -1
        selB = -1
        invalidate()
    }

    private fun pasteFromClipboard() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) session?.paste(text)
    }

    private fun menuItem(label: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(0xFFE5E7EB.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = FontPrefs.mono.typeface(context)
        val h = (16 * resources.displayMetrics.density).toInt()
        val v = (11 * resources.displayMetrics.density).toInt()
        setPadding(h, v, h, v)
        setOnClickListener { closeMenu(); action() }
    }

    private fun openMenu() {
        closeMenu()
        val density = resources.displayMetrics.density
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(0xFF1B2026.toInt())
                setStroke((1 * density).toInt(), 0xFF262B31.toInt())
                cornerRadius = 12 * density
            }
            elevation = 8 * density
        }
        if (selA >= 0) {
            bar.addView(menuItem(tr("Copy")) { copySelection(); clearSelection() })
        }
        bar.addView(menuItem(tr("Paste")) { clearSelection(); pasteFromClipboard() })
        if (selA >= 0) {
            bar.addView(menuItem(tr("Select all")) { selectScreen() })
        }
        bar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val popup = PopupWindow(bar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popup.isOutsideTouchable = false
        popup.isFocusable = false
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val rowTop = if (selA >= 0) (minOf(selA, selB) / 4096 - lastFirst) * cellH else anchorY
        val x = (loc[0] + anchorX - bar.measuredWidth / 2f).toInt()
            .coerceIn(loc[0], maxOf(loc[0], loc[0] + width - bar.measuredWidth))
        val y = (loc[1] + rowTop - bar.measuredHeight - 12 * density).toInt().coerceAtLeast(loc[1])
        popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        menu = popup
    }

    private fun selectScreen() {
        val t = session?.term ?: return
        selA = lastFirst * 4096L
        selB = (lastFirst + t.rows - 1) * 4096L + (t.cols - 1)
        invalidate()
        openMenu()
    }

    private fun keyAt(x: Float, y: Float): Long {
        val t = session?.term ?: return -1
        val row = (y / cellH).toInt().coerceIn(0, t.rows - 1)
        val col = (x / cellW).toInt().coerceIn(0, t.cols - 1)
        return (lastFirst + row) * 4096L + col
    }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            scroller.forceFinished(true)
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            flingLast = 0
            scroller.fling(0, 0, 0, (-vy * 0.9f).toInt(), 0, 0, -1000000, 1000000)
            postOnAnimation(flingStep)
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            closeMenu()
            if (selA >= 0) { selA = -1; selB = -1; invalidate() }
            requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(this@TerminalView, InputMethodManager.SHOW_IMPLICIT)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            closeMenu()
            applyScroll(dy * 1.8f)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            closeMenu()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            val hit = keyAt(e.x, e.y)
            val word = wordAt(hit)
            if (word != null) {
                selA = word.first
                selB = word.second
            } else {
                selA = -1
                selB = -1
            }
            anchorX = e.x
            anchorY = e.y
            selecting = true
            invalidate()
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        pinch.onTouchEvent(e)
        if (pinching || e.pointerCount > 1) return true
        if (selecting) {
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (selA >= 0) { selB = keyAt(e.x, e.y); invalidate() }
                    anchorX = e.x
                    anchorY = e.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    selecting = false
                    openMenu()
                }
            }
            return true
        }
        gestures.onTouchEvent(e)
        return true
    }

    private fun copySelection() {
        val t = session?.term ?: return
        val a = minOf(selA, selB)
        val b = maxOf(selA, selB)
        if (a < 0) return
        val ia = (a / 4096).toInt()
        val ib = (b / 4096).toInt()
        val sb = StringBuilder()
        synchronized(t) {
            for (idx in ia..ib) {
                if (idx >= t.history.size + t.rows) break
                val l = t.lineAt(idx)
                val c0 = if (idx == ia) (a % 4096).toInt() else 0
                val c1 = minOf(if (idx == ib) (b % 4096).toInt() else l.ch.size - 1, l.ch.size - 1)
                val row = StringBuilder()
                for (c in c0..c1) row.append(if (l.ch[c] < 32) ' ' else l.ch[c].toChar())
                sb.append(row.toString().trimEnd())
                if (idx < ib) sb.append('\n')
            }
        }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("terminal", sb.toString()))
        Toast.makeText(context, tr("Copied"), Toast.LENGTH_SHORT).show()
        closeMenu()
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        composed = ""
        return object : BaseInputConnection(this, true) {
            private fun replaceComposed(t: String) {
                val s = session ?: return
                var p = 0
                while (p < composed.length && p < t.length && composed[p] == t[p]) p++
                if (composed.length > p) s.send(ByteArray(composed.length - p) { 0x7f })
                if (t.length > p) s.type(t.substring(p))
            }

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                replaceComposed(text.toString()); composed = ""; return true
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                replaceComposed(text.toString()); composed = text.toString(); return true
            }

            override fun finishComposingText(): Boolean { composed = ""; return true }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                composed = composed.dropLast(beforeLength)
                session?.send(ByteArray(beforeLength.coerceAtLeast(0)) { 0x7f })
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event.keyCode, event)
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(keyCode, event) || super.onKeyDown(keyCode, event)

    private fun handleKey(keyCode: Int, ev: KeyEvent): Boolean {
        val s = session ?: return false
        val seq: String? = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007f"
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001b[3~"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001b"
            KeyEvent.KEYCODE_DPAD_UP -> if (s.term.appCursor) "\u001bOA" else "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> if (s.term.appCursor) "\u001bOB" else "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (s.term.appCursor) "\u001bOC" else "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> if (s.term.appCursor) "\u001bOD" else "\u001b[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001b[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001b[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001b[5~"
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001b[6~"
            KeyEvent.KEYCODE_INSERT -> "\u001b[2~"
            else -> null
        }
        if (seq != null) { s.send(seq.toByteArray()); return true }
        val u = ev.getUnicodeChar(if (ev.isCtrlPressed) 0 else ev.metaState)
        if (u == 0 || u and KeyCharacterMap.COMBINING_ACCENT != 0) return false
        if (ev.isCtrlPressed) {
            val c = u.toChar().lowercaseChar()
            if (c in 'a'..'z') s.send(byteArrayOf((c - 'a' + 1).toByte()))
            return true
        }
        s.type(String(Character.toChars(u)))
        return true
    }

    companion object {
        private val DEF_BG = 0xFF0B0E11.toInt()
        private val DEF_FG = 0xFFE5E7EB.toInt()
        private val SEL = 0x66E8B04A
        private val CURSOR = 0xB0E8B04A.toInt()
    }
}

object TermZoom {
    const val MIN = 8f
    const val MAX = 28f
    private const val FILE = "noxora_ui"

    var sp by mutableFloatStateOf(13f)
        private set

    fun load(ctx: Context) {
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat("term_sp", 13f).coerceIn(MIN, MAX)
    }

    fun set(ctx: Context, value: Float) {
        sp = value.coerceIn(MIN, MAX)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat("term_sp", sp).apply()
    }

    fun step(ctx: Context, delta: Float) = set(ctx, sp + delta)
}
