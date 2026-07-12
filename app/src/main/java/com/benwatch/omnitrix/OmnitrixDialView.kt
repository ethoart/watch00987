package com.benwatch.omnitrix

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import kotlin.math.*

/**
 * Full Omnitrix dial matching the reference video UI:
 *   - galvin_symbol.jpg shown as the outer ring background (rotates with touch)
 *   - Green hourglass symbol drawn on top of the centre disc
 *   - 10 alien icons arranged in a circle on the ring; rotate with the ring
 *   - Tap the centre: pulse glow and call [onActivate]
 *   - Long-press centre: cycle mode (NORMAL → DNA_SCAN → LOW_POWER)
 *   - DNA_SCAN: yellow sweep line rotates over the dial
 *   - LOW_POWER: red dim pulse, minimal animation
 */
class OmnitrixDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onActivate: (() -> Unit)? = null
    var onModeCycle: ((WatchMode) -> Unit)? = null
    var onAlienSelected: ((Int) -> Unit)? = null   // called with alien slot (1-10) when ring tapped

    var mode: WatchMode = WatchMode.NORMAL
        set(value) {
            field = value
            rebuildGlowShader()
            restartAmbientAnimation()
            invalidate()
        }

    // ── bitmaps ──────────────────────────────────────────────────────────────
    private var galvinBmp: Bitmap? = null
    private val alienBitmaps = mutableMapOf<Int, Bitmap>()   // slot → bitmap
    private var hourglassBmp: Bitmap? = null

    // ── paints ────────────────────────────────────────────────────────────────
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val coreBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D1A0D")
    }
    private val coreRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val alienLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 14f
        color = Color.parseColor("#39FF14")
    }
    private val selectionRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // ── animation state ───────────────────────────────────────────────────────
    private var ringAngleDeg = 0f      // driven by touch
    private var glowAlpha = 0f         // 0..255 tap pulse
    private var ambientAngle = 0f      // ambient sweep angle
    private var glowShader: RadialGradient? = null
    private var glowAnimator: ValueAnimator? = null
    private var ambientAnimator: ValueAnimator? = null

    // currently highlighted alien slot on the ring (-1 = none)
    private var highlightedSlot = -1

    // ── touch ──────────────────────────────────────────────────────────────────
    private var touchDownX = 0f; private var touchDownY = 0f
    private var touchLastX = 0f; private var touchLastY = 0f
    private var isDragging = false
    private var longPressFired = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longHandler = Handler(Looper.getMainLooper())
    private var longRunnable: Runnable? = null

    // ── matrix reused each frame ───────────────────────────────────────────────
    private val matrix = Matrix()
    private val tempRectF = RectF()

    init {
        loadBitmaps()
        restartAmbientAnimation()
    }

    // ── bitmap loading ────────────────────────────────────────────────────────

    private fun loadBitmaps() {
        val res = context.resources

        // Galvin symbol / omnitrix outer disc
        val galvinId = res.getIdentifier("galvin_symbol", "drawable", context.packageName)
        if (galvinId != 0) {
            galvinBmp = BitmapFactory.decodeResource(res, galvinId)
        }

        // Hourglass core symbol
        val hgId = res.getIdentifier("omnitrix_hourglass", "drawable", context.packageName)
        if (hgId != 0) hourglassBmp = BitmapFactory.decodeResource(res, hgId)

        // Alien icons (up to 10)
        for (alien in AlienRoster.ALIENS) {
            val id = AlienRoster.resolveDrawable(context, alien.imageResName)
            if (id != R.drawable.ic_alien_placeholder) {
                alienBitmaps[alien.slot] = BitmapFactory.decodeResource(res, id)
            }
        }
    }

    // ── measure ───────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGlowShader()
    }

    private fun rebuildGlowShader() {
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f * 0.48f
        if (r <= 0f) return
        val col = accentColor()
        glowShader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.argb(220, Color.red(col), Color.green(col), Color.blue(col)),
                Color.argb(80, Color.red(col), Color.green(col), Color.blue(col)),
                Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ── draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r = min(width, height) / 2f * 0.96f
        if (r <= 0f) return

        val coreR = r * 0.42f
        val alienRingR = r * 0.72f   // radius where alien icons sit

        // 1. Outer galvin disc (rotates with finger)
        drawGalvinDisc(canvas, cx, cy, r)

        // 2. Mode ambient effect (over the ring, under the core)
        when (mode) {
            WatchMode.DNA_SCAN -> drawDnaSweep(canvas, cx, cy, alienRingR)
            WatchMode.LOW_POWER -> drawLowPowerPulse(canvas, cx, cy, alienRingR)
            WatchMode.NORMAL -> {}
        }

        // 3. Alien icons on the ring (rotate with ring)
        drawAlienRing(canvas, cx, cy, alienRingR)

        // 4. Centre disc + hourglass
        drawCoreDisc(canvas, cx, cy, coreR)

        // 5. Glow pulse (tap feedback, on top of everything)
        if (glowAlpha > 0f) {
            glowPaint.shader = glowShader
            glowPaint.alpha = glowAlpha.toInt()
            canvas.drawCircle(cx, cy, coreR * 1.35f, glowPaint)
            glowPaint.shader = null
        }
    }

    private fun drawGalvinDisc(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.save()
        canvas.rotate(ringAngleDeg, cx, cy)
        galvinBmp?.let { bmp ->
            val size = r * 2f
            tempRectF.set(cx - r, cy - r, cx + r, cy + r)
            val src = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, src, tempRectF, bmpPaint)
        } ?: run {
            // Fallback — draw a plain dark circle with tick marks
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1C2B1C"); style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, r, rimPaint)
            coreRimPaint.color = accentColor()
            canvas.drawCircle(cx, cy, r * 0.97f, coreRimPaint)
        }
        canvas.restore()
    }

    private fun drawAlienRing(canvas: Canvas, cx: Float, cy: Float, ringR: Float) {
        val count = AlienRoster.ALIENS.size   // 10
        val iconSize = (ringR * 0.38f).toInt().coerceAtLeast(24)

        canvas.save()
        canvas.rotate(ringAngleDeg, cx, cy)

        for ((index, alien) in AlienRoster.ALIENS.withIndex()) {
            val angleDeg = (360f / count * index) - 90f   // start at top
            val rad = Math.toRadians(angleDeg.toDouble())
            val ax = (cx + ringR * cos(rad)).toFloat()
            val ay = (cy + ringR * sin(rad)).toFloat()

            val bmp = alienBitmaps[alien.slot]
            val half = iconSize / 2f

            // Highlight ring
            if (alien.slot == highlightedSlot) {
                selectionRingPaint.color = accentColor()
                canvas.drawCircle(ax, ay, half * 1.25f, selectionRingPaint)
            }

            if (bmp != null) {
                tempRectF.set(ax - half, ay - half, ax + half, ay + half)
                canvas.drawBitmap(bmp, null, tempRectF, bmpPaint)
            } else {
                // Placeholder circle
                val fallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1A2E1A"); style = Paint.Style.FILL
                }
                canvas.drawCircle(ax, ay, half, fallPaint)
                fallPaint.color = accentColor()
                fallPaint.style = Paint.Style.STROKE
                fallPaint.strokeWidth = 2f
                canvas.drawCircle(ax, ay, half, fallPaint)
            }
        }
        canvas.restore()
    }

    private fun drawCoreDisc(canvas: Canvas, cx: Float, cy: Float, coreR: Float) {
        // Solid dark disc
        canvas.drawCircle(cx, cy, coreR, coreBgPaint)

        // Rim ring
        coreRimPaint.color = accentColor()
        coreRimPaint.alpha = 230
        canvas.drawCircle(cx, cy, coreR, coreRimPaint)

        // Hourglass / omnitrix symbol
        hourglassBmp?.let { bmp ->
            val half = coreR * 0.72f
            tempRectF.set(cx - half, cy - half, cx + half, cy + half)
            canvas.drawBitmap(bmp, null, tempRectF, bmpPaint)
        } ?: drawHourglassFallback(canvas, cx, cy, coreR * 0.65f)
    }

    /** Draws the two-fan hourglass (the actual Omnitrix symbol shape) using pure canvas ops */
    private fun drawHourglassFallback(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor() }
        val path = Path()
        // Top fan
        val arcRect = RectF(cx - r, cy - r, cx + r, cy + r)
        path.moveTo(cx, cy)
        path.arcTo(arcRect, -145f, 110f)
        path.close()
        // Bottom fan
        path.moveTo(cx, cy)
        path.arcTo(arcRect, 35f, 110f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawDnaSweep(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        scanPaint.color = ContextCompat.getColor(context, R.color.mode_dna_yellow)
        scanPaint.alpha = 200
        canvas.save()
        canvas.rotate(ambientAngle, cx, cy)
        canvas.drawLine(cx, cy - r, cx, cy + r, scanPaint)
        // Trailing arc
        val arcPaint = Paint(scanPaint).apply { alpha = 50; strokeWidth = r * 0.7f; style = Paint.Style.STROKE }
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 50f, false, arcPaint)
        canvas.restore()
    }

    private fun drawLowPowerPulse(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val phase = ((sin(Math.toRadians(ambientAngle.toDouble())) + 1) / 2).toFloat()
        val pulseR = r * (0.55f + 0.45f * phase)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f
            color = ContextCompat.getColor(context, R.color.mode_low_power_red); alpha = 140
        }
        canvas.drawCircle(cx, cy, pulseR, p)
    }

    private fun accentColor() = ContextCompat.getColor(context, when (mode) {
        WatchMode.NORMAL -> R.color.omnitrix_green
        WatchMode.DNA_SCAN -> R.color.mode_dna_yellow
        WatchMode.LOW_POWER -> R.color.mode_low_power_red
    })

    // ── animation ─────────────────────────────────────────────────────────────

    private fun restartAmbientAnimation() {
        ambientAnimator?.cancel()
        val dur = when (mode) { WatchMode.DNA_SCAN -> 2000L; WatchMode.LOW_POWER -> 3500L; else -> 8000L }
        ambientAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = dur; repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { ambientAngle = it.animatedValue as Float; if (mode != WatchMode.NORMAL) invalidate() }
            start()
        }
    }

    private fun pulseGlow() {
        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofFloat(0f, 255f, 120f, 0f).apply {
            duration = 600
            addUpdateListener { glowAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // ── touch ──────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = width / 2f; val cy = height / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y
                touchLastX = event.x; touchLastY = event.y
                isDragging = false; longPressFired = false
                longRunnable = Runnable {
                    if (!isDragging) {
                        longPressFired = true
                        mode = mode.next()
                        onModeCycle?.invoke(mode)
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    }
                }
                longHandler.postDelayed(longRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dist = hypot(event.x - touchDownX, event.y - touchDownY)
                if (!isDragging && dist > touchSlop) {
                    isDragging = true
                    longRunnable?.let { longHandler.removeCallbacks(it) }
                }
                if (isDragging) {
                    val prev = Math.toDegrees(atan2((touchLastY - cy).toDouble(), (touchLastX - cx).toDouble()))
                    val curr = Math.toDegrees(atan2((event.y - cy).toDouble(), (event.x - cx).toDouble()))
                    var delta = (curr - prev).toFloat()
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    ringAngleDeg += delta
                    invalidate()
                }
                touchLastX = event.x; touchLastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                longRunnable?.let { longHandler.removeCallbacks(it) }
                if (!isDragging && !longPressFired) {
                    val distToCenter = hypot(event.x - cx, event.y - cy)
                    val coreR = min(width, height) / 2f * 0.96f * 0.42f
                    if (distToCenter <= coreR) {
                        // Tapped core
                        performClick()
                    } else {
                        // Check if tapped an alien on the ring
                        val tappedSlot = hitTestAlien(event.x, event.y, cx, cy)
                        if (tappedSlot != -1) {
                            highlightedSlot = tappedSlot
                            invalidate()
                            postDelayed({ onAlienSelected?.invoke(tappedSlot) }, 200)
                        }
                    }
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                longRunnable?.let { longHandler.removeCallbacks(it) }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTestAlien(tx: Float, ty: Float, cx: Float, cy: Float): Int {
        val r = min(width, height) / 2f * 0.96f
        val alienRingR = r * 0.72f
        val iconHalf = r * 0.38f * 0.5f
        val count = AlienRoster.ALIENS.size
        for ((index, alien) in AlienRoster.ALIENS.withIndex()) {
            val angleDeg = (360f / count * index) - 90f + ringAngleDeg
            val rad = Math.toRadians(angleDeg.toDouble())
            val ax = (cx + alienRingR * cos(rad)).toFloat()
            val ay = (cy + alienRingR * sin(rad)).toFloat()
            if (hypot(tx - ax, ty - ay) <= iconHalf * 1.4f) return alien.slot
        }
        return -1
    }

    override fun performClick(): Boolean {
        super.performClick()
        highlightedSlot = -1
        pulseGlow()
        postDelayed({ onActivate?.invoke() }, 350)
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ambientAnimator?.cancel()
        glowAnimator?.cancel()
        longRunnable?.let { longHandler.removeCallbacks(it) }
    }
}
