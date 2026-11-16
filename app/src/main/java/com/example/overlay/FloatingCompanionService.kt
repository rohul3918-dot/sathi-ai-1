package com.example.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.CompanionCharacterCatalog
import com.example.data.repository.CompanionConfigStore
import kotlin.math.abs

class FloatingCompanionService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayRoot: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var characterImageView: ImageView? = null
    private var breathAnimator: AnimatorSet? = null
    private var slideAnimator: ValueAnimator? = null
    private var isDockedAside = false

    private var restX = 0
    private var restY = 0
    private var isMovedUpForTouch = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val glideBackDownRunnable = Runnable {
        glideOverlayBackDown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val store = CompanionConfigStore(this)
        val slot = intent?.getIntExtra(EXTRA_CHARACTER_SLOT, store.current().selectedSlot)
            ?: store.current().selectedSlot
        val drawableId = CompanionCharacterCatalog.resolveDrawableId(this, slot)

        if (!canShowOverlay(this) || drawableId == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        showOrUpdateOverlay(drawableId)
        return START_STICKY
    }

    private fun showOrUpdateOverlay(drawableId: Int) {
        val wm = windowManager ?: return
        val existingImage = characterImageView
        if (overlayRoot != null && existingImage != null) {
            existingImage.setImageResource(drawableId)
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }

        // Moderate floating scale: slightly smaller than in-app (~164x210dp) yet clearly recognizable & expressive
        val charWidth = dp(122)
        val charHeight = dp(150)
        val characterFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(charWidth, charHeight)
        }

        val imageView = ImageView(this).apply {
            setImageResource(drawableId)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Sathi AI Live Companion"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        characterImageView = imageView

        val closeBadge = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 15, 25, 45))
                setStroke(dp(1), Color.argb(140, 46, 230, 214))
            }
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setOnClickListener {
                stopSelfSafely()
            }
        }

        characterFrame.addView(imageView)
        characterFrame.addView(closeBadge)

        val pillHint = TextView(this).apply {
            text = "লাইভ সাথী"
            setTextColor(Color.parseColor("#E8F6FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(9), dp(3), dp(9), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(215, 10, 18, 34))
                setStroke(dp(1), Color.argb(160, 46, 230, 214))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
        }

        container.addView(characterFrame)
        container.addView(pillHint)

        val typeFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val statusBarOffset = resolveStatusBarOffsetPx()
        restX = (screenWidth - dp(134)).coerceAtLeast(0)
        restY = statusBarOffset + dp(110)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            typeFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = restX
            y = restY
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { v, event ->
            val currentParams = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> {
                    // User touched elsewhere on screen while using another app:
                    // move character smoothly upward & keep resetting 3.5s idle timer.
                    moveOverlayUpForScreenTouch()
                    false
                }
                MotionEvent.ACTION_DOWN -> {
                    mainHandler.removeCallbacks(glideBackDownRunnable)
                    slideAnimator?.cancel()
                    initialX = currentParams.x
                    initialY = currentParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                    }
                    if (moved) {
                        currentParams.x = (initialX + dx).coerceIn(-dp(42), screenWidth - dp(48))
                        currentParams.y = (initialY + dy).coerceIn(dp(8), resources.displayMetrics.heightPixels - dp(170))
                        runCatching { wm.updateViewLayout(v, currentParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (isDockedAside) {
                            isDockedAside = false
                            currentParams.x = (screenWidth - dp(134)).coerceAtLeast(0)
                            restX = currentParams.x
                            v.alpha = 1f
                            runCatching { wm.updateViewLayout(v, currentParams) }
                        } else {
                            openMainTalkScreen()
                        }
                    } else {
                        val mid = currentParams.x + v.width / 2
                        if (currentParams.x < -dp(12) || currentParams.x > screenWidth - dp(86)) {
                            isDockedAside = true
                            currentParams.x = if (mid < screenWidth / 2) -dp(44) else screenWidth - dp(68)
                            v.alpha = 0.76f
                        } else {
                            isDockedAside = false
                            currentParams.x = if (mid < screenWidth / 2) dp(8) else (screenWidth - dp(134)).coerceAtLeast(0)
                            v.alpha = 1f
                        }
                        restX = currentParams.x
                        restY = currentParams.y
                        isMovedUpForTouch = false
                        runCatching { wm.updateViewLayout(v, currentParams) }
                    }
                    true
                }
                else -> false
            }
        }

        layoutParams = params
        overlayRoot = container
        try {
            wm.addView(container, params)
            startSubtleIdleAnimation(imageView)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to attach floating overlay: ${e.message}")
            stopSelfSafely()
        }
    }

    private fun moveOverlayUpForScreenTouch() {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        val wm = windowManager ?: return

        // Reset the 3.5-second idle timer on every outside touch
        mainHandler.removeCallbacks(glideBackDownRunnable)
        mainHandler.postDelayed(glideBackDownRunnable, TOUCH_IDLE_RETURN_DELAY_MS)

        if (isMovedUpForTouch) return
        isMovedUpForTouch = true
        val topAvoidY = dp(10)
        animateOverlayY(root, params, wm, params.y, topAvoidY, 0.56f, 220L)
    }

    private fun glideOverlayBackDown() {
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        if (!isMovedUpForTouch) return
        isMovedUpForTouch = false
        val targetAlpha = if (isDockedAside) 0.76f else 1f
        animateOverlayY(root, params, wm, params.y, restY, targetAlpha, 340L)
    }

    private fun animateOverlayY(
        root: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
        fromY: Int,
        toY: Int,
        targetAlpha: Float,
        durationMs: Long
    ) {
        slideAnimator?.cancel()
        val startAlpha = root.alpha
        slideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val frac = anim.animatedFraction
                params.y = (fromY + (toY - fromY) * frac).toInt()
                root.alpha = startAlpha + (targetAlpha - startAlpha) * frac
                runCatching { wm.updateViewLayout(root, params) }
            }
            start()
        }
    }

    private fun startSubtleIdleAnimation(target: View) {
        breathAnimator?.cancel()
        val floatY = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, -dp(4).toFloat(), dp(5).toFloat()).apply {
            duration = 1850L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val sway = ObjectAnimator.ofFloat(target, View.ROTATION, -1.6f, 1.6f).apply {
            duration = 2150L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, 0.98f, 1.035f).apply {
            duration = 1850L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val scaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, 0.98f, 1.035f).apply {
            duration = 1850L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        breathAnimator = AnimatorSet().apply {
            playTogether(floatY, sway, scaleX, scaleY)
            start()
        }
    }

    private fun openMainTalkScreen() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_VOICE_ON_OPEN, true)
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun stopSelfSafely() {
        removeOverlayView()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlayView() {
        mainHandler.removeCallbacks(glideBackDownRunnable)
        slideAnimator?.cancel()
        slideAnimator = null
        breathAnimator?.cancel()
        breathAnimator = null
        val root = overlayRoot ?: return
        runCatching { windowManager?.removeViewImmediate(root) }
        overlayRoot = null
        characterImageView = null
        layoutParams = null
        isMovedUpForTouch = false
    }

    override fun onDestroy() {
        removeOverlayView()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "সাথী ভাসমান চরিত্র",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "লাইভ ভয়েস চালু থাকলে অ্যাপের বাইরে ভাসমান সাথী চরিত্র দেখাতে ব্যবহৃত হয়।"
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_START_VOICE_ON_OPEN, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, FloatingCompanionService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("লাইভ সাথী চরিত্র সক্রিয়")
            .setContentText("অ্যাপে ফিরতে বা কথা বলতে চরিত্রে ট্যাপ করুন")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "বন্ধ করুন", stopIntent)
            .build()
    }

    private fun resolveStatusBarOffsetPx(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dp(28)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val TAG = "FloatingCompanion"
        private const val CHANNEL_ID = "sathi_floating_companion_channel"
        private const val NOTIFICATION_ID = 7701
        private const val TOUCH_IDLE_RETURN_DELAY_MS = 3500L
        const val ACTION_START_OR_UPDATE = "com.example.action.START_FLOATING_COMPANION"
        const val ACTION_STOP_OVERLAY = "com.example.action.STOP_FLOATING_COMPANION"
        const val EXTRA_CHARACTER_SLOT = "extra_character_slot"
        const val EXTRA_START_VOICE_ON_OPEN = "extra_start_voice_on_open"

        fun canShowOverlay(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        fun startOverlay(context: Context, slot: Int) {
            if (!canShowOverlay(context)) return
            if (CompanionCharacterCatalog.resolveDrawableId(context, slot) == 0) return
            val intent = Intent(context, FloatingCompanionService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_CHARACTER_SLOT, slot)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                Log.w(TAG, "Failed to start FloatingCompanionService: ${it.message}")
            }
        }

        fun stopOverlay(context: Context) {
            runCatching {
                context.stopService(Intent(context, FloatingCompanionService::class.java))
            }
        }
    }
}
