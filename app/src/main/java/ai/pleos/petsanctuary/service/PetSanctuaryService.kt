package ai.pleos.petsanctuary.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.pleos.petsanctuary.audio.AndroidPetSoundEngine
import ai.pleos.petsanctuary.domain.PetSanctuaryController
import ai.pleos.petsanctuary.hal.PleosSdkVehicleHal
import ai.pleos.petsanctuary.model.PetPresetMode
import ai.pleos.petsanctuary.ui.MainActivity

/**
 * Foreground Service that maintains Pet Sanctuary climate, ambient lighting,
 * gear safety monitoring, procedural acoustic therapy, and Navigation Tunnel Event listeners
 * even when the driver switches the main display to Pleos Navigation.
 */
class PetSanctuaryService : Service() {

    companion object {
        private const val TAG = "PetSanctuaryService"
        private const val CHANNEL_ID = "pleos_pet_sanctuary_channel"
        private const val NOTIFICATION_ID = 2026
        const val ACTION_START_PRESET = "ai.pleos.petsanctuary.action.START_PRESET"
        const val ACTION_STOP_CARE = "ai.pleos.petsanctuary.action.STOP_CARE"
        const val EXTRA_PRESET_NAME = "extra_preset_name"
        const val ACTION_TUNNEL_APPROACHING = "ai.pleos.petsanctuary.TUNNEL_APPROACHING"
        const val ACTION_TUNNEL_EXITED = "ai.pleos.petsanctuary.TUNNEL_EXITED"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PetSanctuaryService = this@PetSanctuaryService
    }

    private val binder = LocalBinder()

    lateinit var vehicleHal: PleosSdkVehicleHal
        private set

    lateinit var soundEngine: AndroidPetSoundEngine
        private set

    lateinit var controller: PetSanctuaryController
        private set

    /**
     * BroadcastReceiver listening to Navigation Tunnel events:
     * e.g., when the in-vehicle navigation broadcasts `TUNNEL_APPROACHING` 300m ahead.
     */
    private val naviTunnelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TUNNEL_APPROACHING -> {
                    Log.i(TAG, "Navi tunnel event received -> Activating TUNNEL_SAFEGUARD (awaiting window close confirmation)")
                    activateModeAndNotify(PetPresetMode.TUNNEL_SAFEGUARD, closeWindowsOverride = false)
                    controller.requestTunnelWindowPrompt()
                }
                ACTION_TUNNEL_EXITED -> {
                    Log.i(TAG, "Navi tunnel exited event received -> Restoring to RELAX")
                    controller.dismissTunnelWindowPrompt()
                    activateModeAndNotify(PetPresetMode.RELAX)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        vehicleHal = PleosSdkVehicleHal(applicationContext)
        soundEngine = AndroidPetSoundEngine(applicationContext) { focusChange ->
            controller.handleAudioFocusChange(focusChange)
        }
        controller = PetSanctuaryController(vehicleHal, soundEngine)
        controller.initialize()

        val filter = IntentFilter().apply {
            addAction(ACTION_TUNNEL_APPROACHING)
            addAction(ACTION_TUNNEL_EXITED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(naviTunnelReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(naviTunnelReceiver, filter)
        }
        Log.i(TAG, "Registered Navi Tunnel BroadcastReceiver (RECEIVER_NOT_EXPORTED)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CARE) {
            Log.i(TAG, "Notification action STOP_CARE received -> Restoring baseline & turning OFF")
            activateModeAndNotify(PetPresetMode.OFF)
            return START_STICKY
        }

        val presetName = intent?.getStringExtra(EXTRA_PRESET_NAME)
        if (presetName != null) {
            val mode = runCatching { PetPresetMode.valueOf(presetName) }.getOrDefault(PetPresetMode.OFF)
            controller.activatePreset(mode)
        }

        val activeMode = controller.state.value.activeMode
        startForeground(NOTIFICATION_ID, buildNotification(activeMode))
        return START_STICKY
    }

    fun activateModeAndNotify(mode: PetPresetMode, closeWindowsOverride: Boolean? = null) {
        controller.activatePreset(mode, closeWindowsOverride)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(mode))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pleos Pet Sanctuary Care Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "반려견 차량 감각 케어(조명·공조·사운드 테라피) 백그라운드 유지 서비스"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(mode: PetPresetMode): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (mode == PetPresetMode.OFF) {
            "플레오스 펫 힐링 라운지 — 대기 중"
        } else {
            "🐾 [${mode.titleKo}] 반려견 감각 케어 가동 중"
        }

        val desc = if (mode == PetPresetMode.OFF) {
            "반려견 탑승 시 모드를 선택하세요."
        } else {
            "${mode.subtitleKo} (목표: ${mode.targetTempCelsius}℃)"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(desc)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(mode != PetPresetMode.OFF)

        if (mode != PetPresetMode.OFF) {
            val stopIntent = Intent(this, PetSanctuaryService::class.java).apply {
                action = ACTION_STOP_CARE
            }
            val stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "케어 종료 (OFF)",
                stopPendingIntent
            )
        }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try {
            unregisterReceiver(naviTunnelReceiver)
        } catch (_: Exception) {
        }
        controller.release()
        super.onDestroy()
    }
}
