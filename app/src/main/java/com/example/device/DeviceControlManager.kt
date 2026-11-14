package com.example.device

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceActionOutcome(
    val executed: Boolean,
    val blockedByUserToggle: Boolean = false,
    val missingOsPermission: String? = null,
    val permissionKey: String = "",
    val summaryBn: String
)

class DeviceControlManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _currentVolumePercent = MutableStateFlow(readVolumePercent())
    val currentVolumePercent: StateFlow<Int> = _currentVolumePercent.asStateFlow()

    private val _appBrightnessPercent = MutableStateFlow(60)
    val appBrightnessPercent: StateFlow<Int> = _appBrightnessPercent.asStateFlow()

    fun hasOsPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasOsPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun readVolumePercent(): Int {
        val am = audioManager ?: return 50
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current * 100f) / max).toInt().coerceIn(0, 100)
    }

    fun setFlashlight(enable: Boolean): DeviceActionOutcome {
        val cm = cameraManager ?: return DeviceActionOutcome(
            executed = false,
            permissionKey = "allowFlashlight",
            summaryBn = "এই ডিভাইসে ক্যামেরা ফ্ল্যাশ সার্ভিস পাওয়া যায়নি।"
        )
        return try {
            val flashCameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (flashCameraId == null) {
                DeviceActionOutcome(
                    executed = false,
                    permissionKey = "allowFlashlight",
                    summaryBn = "এই ডিভাইসে ফ্ল্যাশলাইট হার্ডওয়্যার পাওয়া যায়নি।"
                )
            } else {
                cm.setTorchMode(flashCameraId, enable)
                _isFlashlightOn.value = enable
                triggerHapticPulse()
                DeviceActionOutcome(
                    executed = true,
                    permissionKey = "allowFlashlight",
                    summaryBn = if (enable) "ফ্ল্যাশলাইট চালু করা হয়েছে।" else "ফ্ল্যাশলাইট বন্ধ করা হয়েছে।"
                )
            }
        } catch (e: Exception) {
            DeviceActionOutcome(
                executed = false,
                permissionKey = "allowFlashlight",
                summaryBn = "ফ্ল্যাশলাইট পরিবর্তন করা যায়নি: ${e.localizedMessage ?: "হার্ডওয়্যার ত্রুটি"}"
            )
        }
    }

    fun setMediaVolumePercent(percent: Int): DeviceActionOutcome {
        val am = audioManager ?: return DeviceActionOutcome(
            executed = false,
            permissionKey = "allowVolumeControl",
            summaryBn = "অডিও ম্যানেজার পাওয়া যায়নি।"
        )
        val clamped = percent.coerceIn(0, 100)
        return try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetIndex = ((clamped / 100f) * max).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
            val actual = readVolumePercent()
            _currentVolumePercent.value = actual
            triggerHapticPulse()
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowVolumeControl",
                summaryBn = "মিডিয়া ভলিউম $actual% এ সেট করা হয়েছে।"
            )
        } catch (e: Exception) {
            DeviceActionOutcome(
                executed = false,
                permissionKey = "allowVolumeControl",
                summaryBn = "ভলিউম পরিবর্তন করা যায়নি (DND মোড চালু থাকতে পারে)।"
            )
        }
    }

    fun setAppBrightness(activity: Activity?, percent: Int): DeviceActionOutcome {
        val clamped = percent.coerceIn(5, 100)
        _appBrightnessPercent.value = clamped
        return if (activity != null) {
            val lp = activity.window.attributes
            lp.screenBrightness = clamped / 100f
            activity.window.attributes = lp
            triggerHapticPulse()
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowDisplayAndSettings",
                summaryBn = "স্ক্রিন ব্রাইটনেস $clamped% এ সেট করা হয়েছে।"
            )
        } else {
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowDisplayAndSettings",
                summaryBn = "ব্রাইটনেস পছন্দ $clamped% সংরক্ষিত হয়েছে।"
            )
        }
    }

    fun openConnectivitySettings(type: String): DeviceActionOutcome {
        val intent = when (type.uppercase()) {
            "WIFI" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
            }
            "BLUETOOTH" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val label = when (type.uppercase()) {
                "WIFI" -> "Wi-Fi কন্ট্রোল প্যানেল"
                "BLUETOOTH" -> "Bluetooth সেটিংস"
                else -> "ডিসপ্লে সেটিংস"
            }
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowDisplayAndSettings",
                summaryBn = "$label খোলা হয়েছে।"
            )
        } catch (e: Exception) {
            DeviceActionOutcome(
                executed = false,
                permissionKey = "allowDisplayAndSettings",
                summaryBn = "সেটিংস প্যানেল খোলা যায়নি।"
            )
        }
    }

    fun setAlarm(hour: Int, minute: Int, label: String): DeviceActionOutcome {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, h)
            putExtra(AlarmClock.EXTRA_MINUTES, m)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Sathi AI অ্যালার্ম" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowAlarmsAndTimers",
                summaryBn = String.format("%02d:%02d এর জন্য অ্যালার্ম সেট করা হচ্ছে।", h, m)
            )
        } catch (e: Exception) {
            DeviceActionOutcome(
                executed = false,
                permissionKey = "allowAlarmsAndTimers",
                summaryBn = "ডিভাইসে অ্যালার্ম অ্যাপ পাওয়া যায়নি।"
            )
        }
    }

    fun setTimer(seconds: Int, label: String): DeviceActionOutcome {
        val safeSeconds = seconds.coerceIn(5, 86400)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, safeSeconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Sathi AI টাইমার" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowAlarmsAndTimers",
                summaryBn = "$safeSeconds সেকেন্ডের টাইমার সেট করা হচ্ছে।"
            )
        } catch (e: Exception) {
            DeviceActionOutcome(
                executed = false,
                permissionKey = "allowAlarmsAndTimers",
                summaryBn = "ডিভাইসে টাইমার অ্যাপ পাওয়া যায়নি।"
            )
        }
    }

    fun openDialer(phoneNumber: String): DeviceActionOutcome {
        val sanitized = phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (sanitized.isBlank()) {
            return DeviceActionOutcome(
                executed = false,
                permissionKey = "allowDialer",
                summaryBn = "সঠিক ফোন নম্বর দেওয়া হয়নি।"
            )
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowDialer",
                summaryBn = "$sanitized নম্বরের জন্য ডায়ালার খোলা হয়েছে।"
            )
        } catch (e: Exception) {
            DeviceActionOutcome(
                executed = false,
                permissionKey = "allowDialer",
                summaryBn = "ফোন ডায়ালার খোলা যায়নি।"
            )
        }
    }

    fun postReminderNotification(title: String, body: String): DeviceActionOutcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasOsPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return DeviceActionOutcome(
                executed = false,
                missingOsPermission = Manifest.permission.POST_NOTIFICATIONS,
                permissionKey = "allowNotifications",
                summaryBn = "নোটিফিকেশন পাঠানোর জন্য সিস্টেম পারমিশন প্রয়োজন।"
            )
        }
        ensureNotificationChannel()
        return try {
            val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title.ifBlank { "Sathi AI রিমাইন্ডার" })
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt(), notification)
            triggerHapticPulse()
            DeviceActionOutcome(
                executed = true,
                permissionKey = "allowNotifications",
                summaryBn = "রিমাইন্ডার নোটিফিকেশন পাঠানো হয়েছে।"
            )
        } catch (e: SecurityException) {
            DeviceActionOutcome(
                executed = false,
                missingOsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else null,
                permissionKey = "allowNotifications",
                summaryBn = "নোটিফিকেশন পারমিশন অনুমোদিত নয়।"
            )
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val existing = manager?.getNotificationChannel(REMINDER_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "Sathi AI Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Personal task reminders and assistant alerts"
                }
                manager?.createNotificationChannel(channel)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    v?.vibrate(40)
                }
            }
        } catch (_: Exception) {
            // Haptics are optional
        }
    }

    private companion object {
        const val REMINDER_CHANNEL_ID = "sathi_reminders"
    }
}
