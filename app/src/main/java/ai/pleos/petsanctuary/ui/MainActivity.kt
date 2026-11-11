package ai.pleos.petsanctuary.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ai.pleos.petsanctuary.BuildConfig
import ai.pleos.petsanctuary.domain.PetSanctuaryController
import ai.pleos.petsanctuary.model.PetPresetMode
import ai.pleos.petsanctuary.model.PetSanctuaryState
import ai.pleos.petsanctuary.model.VehicleGearState
import ai.pleos.petsanctuary.service.PetSanctuaryService
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * OEM-Grade Luxury In-Vehicle Cockpit Dashboard for "Pleos Pet Sanctuary" (플레오스 펫 힐링 라운지).
 * Designed to look like a premium production automaker infotainment system (e.g. Genesis / MBUX / Pleos AAOS).
 *
 * Features:
 * - Dynamic Ambient Aura / Mood Glow background responding to selected mode
 * - 8 High-Impact Preset Cards arranged in 2 rows of 4 cards
 * - Real-time Reassurance Guardian Banner for Quick Stop Mode (Window Lock & Climate Reassurance)
 * - Cooldown Timer Countdown Badge for Post-Play Cooldown
 * - High-Fidelity Dial Gauge Telemetry for Climate (HVAC), Window Lock, Interior Illumination, and Acoustic Therapy
 * - Collapsible Engineering Console (Virtual Simulator hidden by default)
 * - Full support for Pleos Connect AAOS (2560x1440 Widescreen & Split View)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PleosPetSanctuaryUI"
        private const val REQ_RECORD_AUDIO = 2001
        const val EXTRA_INITIAL_PRESET = "extra_initial_preset"
        const val EXTRA_SIMULATE_GEAR = "extra_simulate_gear"
        const val EXTRA_SIMULATE_NAVI_DUCK = "extra_simulate_navi_duck"
    }

    private var sanctuaryService: PetSanctuaryService? = null
    private var stateJob: Job? = null
    private var tunnelConfirmDialog: AlertDialog? = null

    // UI Root & Background Aura
    private lateinit var rootScroll: ScrollView
    private lateinit var auraGlowHeader: View

    // Top Header & Status Badges
    private lateinit var gearPillBadge: TextView
    private lateinit var safetyPillBadge: TextView
    private lateinit var audioPillBadge: TextView
    private lateinit var liveStatusSubtext: TextView

    // Guardian Reassurance Banner
    private lateinit var guardianBannerView: LinearLayout
    private lateinit var guardianBannerTitle: TextView
    private lateinit var guardianBannerDetail: TextView

    // Center Stage Cards
    private val presetCardViews = mutableMapOf<PetPresetMode, LinearLayout>()
    private val presetCheckBadges = mutableMapOf<PetPresetMode, TextView>()
    private val presetSubtitleViews = mutableMapOf<PetPresetMode, TextView>()

    // Live Vehicle Telemetry Glass Cards
    private lateinit var ambientGlowOrb: View
    private lateinit var ambientValueText: TextView
    private lateinit var ambientDetailText: TextView
    private lateinit var btnBrightnessMinus: Button
    private lateinit var btnBrightnessPlus: Button

    private lateinit var tempBigValueText: TextView
    private lateinit var tempDetailText: TextView
    private lateinit var airCleanerBadge: TextView
    private lateinit var seatVentBadge: TextView
    private lateinit var windowLockBadge: TextView
    private lateinit var btnTempMinus: Button
    private lateinit var btnTempPlus: Button

    private lateinit var audioWaveIcon: TextView
    private lateinit var voiceStatusBadge: TextView
    private lateinit var btnMuteAudio: Button
    private lateinit var audioTitleText: TextView
    private lateinit var audioDetailText: TextView
    private lateinit var btnRecordVoice: Button
    private lateinit var btnTtsVoice: Button
    private lateinit var btnPreviewVoice: Button
    private lateinit var btnClearVoice: Button

    // Collapsible Engineering Console
    private lateinit var consoleContainer: LinearLayout
    private lateinit var btnToggleConsole: Button
    private var isConsoleVisible = false
    private lateinit var logContainerText: TextView

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? PetSanctuaryService.LocalBinder ?: return
            val svc = binder.getService()
            sanctuaryService = svc
            Log.i(TAG, "Bound to PetSanctuaryService")

            stateJob?.cancel()
            stateJob = lifecycleScope.launch {
                svc.controller.state.collect { state ->
                    renderState(state)
                }
            }

            intent?.let { handleIntentExtras(it, svc) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sanctuaryService = null
            stateJob?.cancel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rootView = buildLuxuryCockpitView()
        setContentView(rootView)

        val serviceIntent = Intent(this, PetSanctuaryService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sanctuaryService?.let { handleIntentExtras(intent, it) }
    }

    private fun handleIntentExtras(intent: Intent, service: PetSanctuaryService) {
        val presetStr = intent.getStringExtra(EXTRA_INITIAL_PRESET)
        if (!presetStr.isNullOrBlank()) {
            val matchedMode = PetPresetMode.values().find {
                it.name.equals(presetStr, ignoreCase = true)
            }
            if (matchedMode != null) {
                Log.i(TAG, "Intent requested initial preset: $matchedMode")
                service.activateModeAndNotify(matchedMode)
                lifecycleScope.launch {
                    delay(280L)
                    if (service.controller.state.value.activeMode == matchedMode) {
                        service.activateModeAndNotify(matchedMode)
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            val gearStr = intent.getStringExtra(EXTRA_SIMULATE_GEAR)
            if (!gearStr.isNullOrBlank()) {
                val gear = when (gearStr.uppercase()) {
                    "D", "DRIVE" -> VehicleGearState.DRIVE
                    "R", "REVERSE" -> VehicleGearState.REVERSE
                    "N", "NEUTRAL" -> VehicleGearState.NEUTRAL
                    else -> VehicleGearState.PARK
                }
                service.vehicleHal.fallbackSimulator.simulateGearShift(gear)
            }

            if (intent.getBooleanExtra(EXTRA_SIMULATE_NAVI_DUCK, false)) {
                service.controller.handleAudioFocusChange(
                    PetSanctuaryController.AUDIOFOCUS_LOSS_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun showTunnelWindowConfirmationDialog() {
        if (tunnelConfirmDialog?.isShowing == true) return
        tunnelConfirmDialog = AlertDialog.Builder(this)
            .setTitle("🚇 터널 세이프가드 — 창문 자동 닫힘 안전 확인")
            .setMessage(
                "터널 진입 세이프가드 모드를 가동합니다.\n\n" +
                    "반려견이 창문 밖으로 머리나 발을 내밀고 있지 않은지 확인하셨습니까?\n\n" +
                    "• [창문 닫고 가동]: 4개 도어 창문 100% 밀폐 + 내기순환 + 저주파 차폐음\n" +
                    "• [창문 유지하고 가동]: 창문 작동 없이 내기순환 + 조명 + 저주파 차폐음만 가동"
            )
            .setPositiveButton("창문 닫고 가동") { _, _ ->
                sanctuaryService?.controller?.dismissTunnelWindowPrompt()
                sanctuaryService?.activateModeAndNotify(
                    PetPresetMode.TUNNEL_SAFEGUARD,
                    closeWindowsOverride = true
                )
            }
            .setNegativeButton("창문 유지하고 가동") { _, _ ->
                sanctuaryService?.controller?.dismissTunnelWindowPrompt()
                sanctuaryService?.activateModeAndNotify(
                    PetPresetMode.TUNNEL_SAFEGUARD,
                    closeWindowsOverride = false
                )
            }
            .setNeutralButton("취소") { dialog, _ ->
                sanctuaryService?.controller?.dismissTunnelWindowPrompt()
                dialog.dismiss()
            }
            .setOnCancelListener {
                sanctuaryService?.controller?.dismissTunnelWindowPrompt()
            }
            .create()
        tunnelConfirmDialog?.show()
    }

    private fun handleRecordOwnerVoiceClick() {
        val svc = sanctuaryService ?: return
        if (svc.controller.state.value.isDrivingRestricted) {
            svc.controller.startOwnerVoiceRecording(5)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            svc.controller.startOwnerVoiceRecording(5)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQ_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sanctuaryService?.controller?.startOwnerVoiceRecording(5)
            } else {
                liveStatusSubtext.text = "마이크 권한이 거부되었습니다. [🗣️ AI 안심음성] 버튼으로 한국어 안심 음성을 생성할 수 있습니다."
            }
        }
    }

    private fun buildLuxuryCockpitView(): View {
        rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#090D14"))
            isFillViewport = true
        }

        val frameRoot = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Ambient Mood Aura Glow (Soft gradient at top/center that reflects cabin mood lighting)
        auraGlowHeader = View(this).apply {
            background = gradientAura("#261E0F", "#090D14")
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(480)
            )
        }
        frameRoot.addView(auraGlowHeader)

        val screenWidthDp = resources.configuration.screenWidthDp
        val isCompactScreen = screenWidthDp < 900

        // Main Center Content Column
        val centerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                if (isCompactScreen) dp(16) else dp(36),
                if (isCompactScreen) dp(24) else dp(72),
                if (isCompactScreen) dp(16) else dp(36),
                if (isCompactScreen) dp(32) else dp(96)
            )
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // ==========================================
        // 1. TOP AUTOMOTIVE STATUS BAR (LUXURY BRANDING)
        // ==========================================
        val topBarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val brandHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val pawBadge = TextView(this).apply {
            text = "🐾"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(0, 0, dp(10), 0)
        }
        val brandTitle = TextView(this).apply {
            text = "PLEOS PET SANCTUARY"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
        }
        val subBrandPill = TextView(this).apply {
            text = "HYUNDAI PLEOS AAOS"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#0C2A42", "#0284C7")
            setPadding(dp(8), dp(3), dp(8), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(12) }
        }
        brandHeader.addView(pawBadge)
        brandHeader.addView(brandTitle)
        brandHeader.addView(subBrandPill)
        titleCol.addView(brandHeader)

        liveStatusSubtext = TextView(this).apply {
            text = "반려견 심리 안정 및 차량 감각 제어가 준비되었습니다."
            setTextColor(Color.parseColor("#8E9AA8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, 0)
        }
        titleCol.addView(liveStatusSubtext)
        topBarRow.addView(titleCol)

        // Status Badges (Pills)
        gearPillBadge = createPillBadge("기어 P", "#133E26", "#22C55E")
        safetyPillBadge = createPillBadge("정차 케어 모드", "#172E4C", "#38BDF8")
        audioPillBadge = createPillBadge("볼륨 100%", "#1C2433", "#94A3B8")

        topBarRow.addView(gearPillBadge)
        topBarRow.addView(safetyPillBadge)
        topBarRow.addView(audioPillBadge)

        btnToggleConsole = Button(this).apply {
            text = "🔧 엔지니어링"
            isAllCaps = false
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = roundedPillBg("#151D2A", "#334155")
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply { leftMargin = dp(10) }
            setOnClickListener {
                isConsoleVisible = !isConsoleVisible
                consoleContainer.visibility = if (isConsoleVisible) View.VISIBLE else View.GONE
                text = if (isConsoleVisible) "✕ 콘솔 닫기" else "🔧 엔지니어링"
            }
        }
        topBarRow.addView(btnToggleConsole)
        centerContainer.addView(topBarRow)

        // ==========================================
        // GUARDIAN REASSURANCE BANNER (SHOWN WHEN QUICK STOP IS ACTIVE)
        // ==========================================
        guardianBannerView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = roundedContainerBg("#2D1602", "#D97706", 14)
            setPadding(dp(20), dp(14), dp(20), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val guardianIcon = TextView(this).apply {
            text = "🛡️"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setPadding(0, 0, dp(14), 0)
        }
        val guardianTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        guardianBannerTitle = TextView(this).apply {
            text = "5분 휴게소 안심 가디언 작동 중"
            setTextColor(Color.parseColor("#FDE68A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        }
        guardianBannerDetail = TextView(this).apply {
            text = "쾌적 21℃ 유지 · 창문 조작 잠금(도어 버튼 터치 방지) · 보호자 안심 차임벨 작동 중"
            setTextColor(Color.parseColor("#FCD34D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(2), 0, 0)
        }
        guardianTextCol.addView(guardianBannerTitle)
        guardianTextCol.addView(guardianBannerDetail)
        guardianBannerView.addView(guardianIcon)
        guardianBannerView.addView(guardianTextCol)
        centerContainer.addView(guardianBannerView)

        // ==========================================
        // 2. HERO ONE-TOUCH PRESET CARDS (RESPONSIVE CHUNKING)
        // ==========================================
        val heroTitleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        heroTitleRow.addView(TextView(this).apply {
            text = "반려동물 감각 케어 맞춤 프리셋 (총 8개 모드)"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
        })
        heroTitleRow.addView(TextView(this).apply {
            text = "주행 중(D단)에도 안전한 원터치 조작 지원"
            setTextColor(Color.parseColor("#64748B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(12), 0, 0, 0)
        })
        centerContainer.addView(heroTitleRow)

        val allPresets = listOf(
            PetPresetMode.RELAX,
            PetPresetMode.DEEP_SLEEP,
            PetPresetMode.FRESH_AIR,
            PetPresetMode.TUNNEL_SAFEGUARD,
            PetPresetMode.NAUSEA_RELIEF,
            PetPresetMode.QUICK_STOP_GUARDIAN,
            PetPresetMode.POST_PLAY_COOLDOWN,
            PetPresetMode.OFF
        )

        val chunkSize = if (isCompactScreen) 2 else 4
        allPresets.chunked(chunkSize).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(if (isCompactScreen) 10 else 14) }
            }
            chunk.forEachIndexed { index, mode ->
                val card = createPresetHeroCard(mode, isFirstInRow = (index == 0), isCompact = isCompactScreen)
                presetCardViews[mode] = card
                row.addView(card)
            }
            centerContainer.addView(row)
        }

        // ==========================================
        // 3. AUTOMOTIVE TELEMETRY COCKPIT (GLASS CARDS)
        // ==========================================
        val telemetryRow = LinearLayout(this).apply {
            orientation = if (isCompactScreen) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        }

        // Telemetry Card 1: Light & Ambient System
        val lightGlassCard = createGlassmorphicContainer(0.33f, isCompactScreen)
        val lightTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ambientGlowOrb = View(this).apply {
            background = circularGlow("#FFA726", 18)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(10) }
        }
        val lightTitle = TextView(this).apply {
            text = "실내 앰비언트 무드램프"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnBrightnessMinus = createMiniRoundButton("-") {
            val cur = sanctuaryService?.controller?.state?.value?.ambientBrightness ?: 50
            sanctuaryService?.controller?.adjustAmbientBrightness(cur - 10)
        }
        btnBrightnessPlus = createMiniRoundButton("+") {
            val cur = sanctuaryService?.controller?.state?.value?.ambientBrightness ?: 50
            sanctuaryService?.controller?.adjustAmbientBrightness(cur + 10)
        }
        lightTop.addView(ambientGlowOrb)
        lightTop.addView(lightTitle)
        lightTop.addView(btnBrightnessMinus)
        lightTop.addView(btnBrightnessPlus)
        lightGlassCard.addView(lightTop)

        ambientValueText = TextView(this).apply {
            text = "45% BRIGHTNESS"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(4))
        }
        ambientDetailText = TextView(this).apply {
            text = "웜 앰버 (#FFB74D) · 2열 독서등 소등(차단)"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        lightGlassCard.addView(ambientValueText)
        lightGlassCard.addView(ambientDetailText)
        telemetryRow.addView(lightGlassCard)

        // Telemetry Card 2: Climate, Air Cleaner & Window Safety
        val hvacGlassCard = createGlassmorphicContainer(0.34f, isCompactScreen).apply {
            if (!isCompactScreen) {
                (layoutParams as LinearLayout.LayoutParams).apply {
                    leftMargin = dp(14)
                    rightMargin = dp(14)
                }
            }
        }
        val hvacTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val hvacIcon = TextView(this).apply {
            text = "❄️"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, dp(8), 0)
        }
        val hvacTitle = TextView(this).apply {
            text = "스마트 공조 & 세이프티"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnTempMinus = createMiniRoundButton("-") {
            sanctuaryService?.controller?.adjustTargetTemperature(-0.5f)
        }
        btnTempPlus = createMiniRoundButton("+") {
            sanctuaryService?.controller?.adjustTargetTemperature(+0.5f)
        }
        hvacTop.addView(hvacIcon)
        hvacTop.addView(hvacTitle)
        hvacTop.addView(btnTempMinus)
        hvacTop.addView(btnTempPlus)
        hvacGlassCard.addView(hvacTop)

        val tempValueRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }
        tempBigValueText = TextView(this).apply {
            text = "22.0℃"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
        }
        airCleanerBadge = TextView(this).apply {
            text = "AIR CLEANER"
            setTextColor(Color.parseColor("#4ADE80"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#052E16", "#22C55E")
            setPadding(dp(7), dp(3), dp(7), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
        }
        seatVentBadge = TextView(this).apply {
            text = "2열 시트통풍"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#0C2A42", "#0284C7")
            setPadding(dp(7), dp(3), dp(7), dp(3))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(6) }
        }
        windowLockBadge = TextView(this).apply {
            text = "WINDOW LOCK"
            setTextColor(Color.parseColor("#FBBF24"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#451A03", "#F59E0B")
            setPadding(dp(7), dp(3), dp(7), dp(3))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(6) }
        }
        tempValueRow.addView(tempBigValueText)
        tempValueRow.addView(airCleanerBadge)
        tempValueRow.addView(seatVentBadge)
        tempValueRow.addView(windowLockBadge)
        hvacGlassCard.addView(tempValueRow)

        tempDetailText = TextView(this).apply {
            text = "실내 19.1℃ | 풍량 2단 | 전방&하향 | 외기유입"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        hvacGlassCard.addView(tempDetailText)
        telemetryRow.addView(hvacGlassCard)

        // Telemetry Card 3: Acoustic Therapy, Owner Reassurance Voice & Ducking
        val audioGlassCard = createGlassmorphicContainer(0.33f, isCompactScreen)
        val audioTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        audioWaveIcon = TextView(this).apply {
            text = "🎵"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, dp(8), 0)
        }
        val audioCardTitle = TextView(this).apply {
            text = "음향 테라피 & 보호자 보이스"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        voiceStatusBadge = TextView(this).apply {
            text = "기본 차임벨"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#1E293B", "#475569")
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }
        btnMuteAudio = Button(this).apply {
            text = "🔊 켜짐"
            isAllCaps = false
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#1E293B", "#475569")
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(32)
            ).apply { leftMargin = dp(6) }
            setOnClickListener {
                sanctuaryService?.controller?.toggleAudioMute()
            }
        }
        audioTop.addView(audioWaveIcon)
        audioTop.addView(audioCardTitle)
        audioTop.addView(voiceStatusBadge)
        audioTop.addView(btnMuteAudio)
        audioGlassCard.addView(audioTop)

        audioTitleText = TextView(this).apply {
            text = "60BPM 로우패스 화음 & 핑크노이즈"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(2))
        }
        audioDetailText = TextView(this).apply {
            text = "심박수 안정화 음원 출력 중 (내비 안내 우선 감쇠 연동)"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }
        audioGlassCard.addView(audioTitleText)
        audioGlassCard.addView(audioDetailText)

        val voiceActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        btnRecordVoice = createVoiceActionButton("🎙️ 음성녹음(5초)") {
            handleRecordOwnerVoiceClick()
        }
        btnTtsVoice = createVoiceActionButton("🗣️ AI 안심음성") {
            sanctuaryService?.controller?.applyTtsReassuranceVoice()
        }
        btnPreviewVoice = createVoiceActionButton("▶️ 미리듣기") {
            sanctuaryService?.controller?.previewCustomVoice()
        }.apply {
            visibility = View.GONE
        }
        btnClearVoice = createVoiceActionButton("🗑️ 해제") {
            sanctuaryService?.controller?.clearCustomVoice()
        }.apply {
            visibility = View.GONE
        }
        voiceActionRow.addView(btnRecordVoice)
        voiceActionRow.addView(btnTtsVoice)
        voiceActionRow.addView(btnPreviewVoice)
        voiceActionRow.addView(btnClearVoice)
        audioGlassCard.addView(voiceActionRow)
        telemetryRow.addView(audioGlassCard)

        centerContainer.addView(telemetryRow)

        // ==========================================
        // 4. COLLAPSIBLE ENGINEERING CONSOLE (SIMULATOR)
        // ==========================================
        consoleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = roundedContainerBg("#0D1520", "#1E293B", 14)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        consoleContainer.addView(TextView(this).apply {
            text = "PLEOS VIRTUAL CONSOLE (하드웨어 시뮬레이터 & 센서 주입)"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        val simButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        simButtonsRow.addView(createConsoleButton("기어 P (정차)") {
            sanctuaryService?.vehicleHal?.fallbackSimulator?.simulateGearShift(VehicleGearState.PARK)
        })
        simButtonsRow.addView(createConsoleButton("기어 D (주행 규제)") {
            sanctuaryService?.vehicleHal?.fallbackSimulator?.simulateGearShift(VehicleGearState.DRIVE)
        })
        simButtonsRow.addView(createConsoleButton("내비 음성 (20% Duck)") {
            sanctuaryService?.controller?.handleAudioFocusChange(
                PetSanctuaryController.AUDIOFOCUS_LOSS_TRANSIENT_MAY_DUCK
            )
        })
        simButtonsRow.addView(createConsoleButton("내비 종료 (100% 복원)") {
            sanctuaryService?.controller?.handleAudioFocusChange(
                PetSanctuaryController.AUDIOFOCUS_GAIN
            )
        })
        simButtonsRow.addView(createConsoleButton("트림 미지원 토글") {
            val svc = sanctuaryService ?: return@createConsoleButton
            val caps = svc.vehicleHal.getCapabilities()
            val toggled = caps.copy(
                supportsAmbientLight = !caps.supportsAmbientLight,
                supportsAirCleaner = !caps.supportsAirCleaner
            )
            svc.vehicleHal.fallbackSimulator.updateSimulatedCapabilities(toggled)
            svc.controller.refreshCapabilities(toggled)
        })
        consoleContainer.addView(simButtonsRow)

        val naviSimButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        naviSimButtonsRow.addView(createConsoleButton("🚇 내비 연동: 터널 300m 전 감지 (모의)") {
            showTunnelWindowConfirmationDialog()
        })
        naviSimButtonsRow.addView(createConsoleButton("☀️ 내비 연동: 터널 통과 완료 (모의)") {
            sanctuaryService?.activateModeAndNotify(PetPresetMode.RELAX)
        })
        consoleContainer.addView(naviSimButtonsRow)

        logContainerText = TextView(this).apply {
            text = "SDK 이벤트 로그 대기 중..."
            setTextColor(Color.parseColor("#6EE7B7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            background = roundedContainerBg("#070B10", "#0F172A", 8)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        consoleContainer.addView(logContainerText)
        centerContainer.addView(consoleContainer)

        frameRoot.addView(centerContainer)
        rootScroll.addView(frameRoot)
        return rootScroll
    }

    private fun createPresetHeroCard(
        mode: PetPresetMode,
        isFirstInRow: Boolean,
        isCompact: Boolean = false
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = heroCardBg(false, mode.accentHex)
            setPadding(
                if (isCompact) dp(12) else dp(16),
                if (isCompact) dp(10) else dp(14),
                if (isCompact) dp(12) else dp(16),
                if (isCompact) dp(10) else dp(14)
            )
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, dp(if (isCompact) 125 else 140), 1f).apply {
                if (!isFirstInRow) leftMargin = dp(if (isCompact) 8 else 10)
            }
            setOnClickListener {
                if (mode == PetPresetMode.TUNNEL_SAFEGUARD) {
                    showTunnelWindowConfirmationDialog()
                } else {
                    sanctuaryService?.activateModeAndNotify(mode)
                }
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = TextView(this).apply {
            text = mode.emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val checkBadge = TextView(this).apply {
            text = if (mode == PetPresetMode.OFF) "STANDBY" else mode.badgeKo
            setTextColor(Color.parseColor(mode.accentHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#131B26", mode.accentHex)
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }
        presetCheckBadges[mode] = checkBadge

        topRow.addView(iconView)
        topRow.addView(checkBadge)
        card.addView(topRow)

        val titleView = TextView(this).apply {
            text = mode.titleKo
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(2))
        }
        val subtitleView = TextView(this).apply {
            text = mode.subtitleKo
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 2
        }
        presetSubtitleViews[mode] = subtitleView

        card.addView(titleView)
        card.addView(subtitleView)
        return card
    }

    private fun createGlassmorphicContainer(weight: Float, isCompact: Boolean = false): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedContainerBg("#101926", "#1E293B", 16)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            minimumHeight = dp(178)
            if (isCompact) {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            } else {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderState(state: PetSanctuaryState) {
        val mode = state.activeMode
        Log.i(TAG, "RenderCockpit: mode=$mode, gear=${state.currentGear}, isRestricted=${state.isDrivingRestricted}")

        // 1. Dynamic Aura Glow based on active mode
        val auraTop = when (mode) {
            PetPresetMode.RELAX -> "#3D2910"
            PetPresetMode.DEEP_SLEEP -> "#0F3320"
            PetPresetMode.FRESH_AIR -> "#0C2E42"
            PetPresetMode.TUNNEL_SAFEGUARD -> "#382405"
            PetPresetMode.NAUSEA_RELIEF -> "#042F2C"
            PetPresetMode.QUICK_STOP_GUARDIAN -> "#362002"
            PetPresetMode.POST_PLAY_COOLDOWN -> "#3F0E2A"
            PetPresetMode.OFF -> "#111827"
        }
        auraGlowHeader.background = gradientAura(auraTop, "#090D14")

        // 2. Header Badges
        gearPillBadge.text = if (state.isDrivingRestricted) "기어 D (주행)" else "기어 P (정차)"
        gearPillBadge.background = roundedPillBg(
            if (state.isDrivingRestricted) "#450A0A" else "#052E16",
            if (state.isDrivingRestricted) "#EF4444" else "#22C55E"
        )
        gearPillBadge.setTextColor(Color.parseColor(if (state.isDrivingRestricted) "#FCA5A5" else "#86EFAC"))

        if (state.isDrivingRestricted) {
            safetyPillBadge.text = "주행 안전 규제(잠금)"
            safetyPillBadge.background = roundedPillBg("#431407", "#F97316")
            safetyPillBadge.setTextColor(Color.parseColor("#FDBA74"))
        } else {
            safetyPillBadge.text = "정차 모드 (조작 허용)"
            safetyPillBadge.background = roundedPillBg("#0C2A42", "#0284C7")
            safetyPillBadge.setTextColor(Color.parseColor("#7DD3FC"))
        }

        val vol = (state.audioVolumeMultiplier * 100).toInt()
        audioPillBadge.text = when {
            state.isUserMuted -> "음소거 (MUTE)"
            state.isAudioDuckedForNavi -> "내비 덕킹 (20%)"
            else -> "볼륨 ${vol}%"
        }
        audioPillBadge.background = roundedPillBg(
            when {
                state.isUserMuted -> "#450A0A"
                state.isAudioDuckedForNavi -> "#422006"
                else -> "#1E293B"
            },
            when {
                state.isUserMuted -> "#EF4444"
                state.isAudioDuckedForNavi -> "#EAB308"
                else -> "#64748B"
            }
        )
        audioPillBadge.setTextColor(
            Color.parseColor(
                when {
                    state.isUserMuted -> "#FCA5A5"
                    state.isAudioDuckedForNavi -> "#FDE047"
                    else -> "#CBD5E1"
                }
            )
        )

        btnMuteAudio.text = if (state.isUserMuted) "🔇 음소거됨" else "🔊 사운드 켜짐"
        btnMuteAudio.background = roundedPillBg(
            if (state.isUserMuted) "#450A0A" else "#1E293B",
            if (state.isUserMuted) "#EF4444" else "#475569"
        )
        btnMuteAudio.setTextColor(Color.parseColor(if (state.isUserMuted) "#FCA5A5" else "#E2E8F0"))

        liveStatusSubtext.text = state.lastStatusMessage

        // 3. Guardian Mode Reassurance Banner & Tunnel Window Prompt
        if (state.pendingTunnelWindowPrompt) {
            showTunnelWindowConfirmationDialog()
        }

        if (state.isGuardianModeActive) {
            guardianBannerView.visibility = View.VISIBLE
            val voiceBannerNote = if (state.hasCustomVoiceRecording) {
                "🎙️ [${state.voiceSourceLabel}] 믹싱 재생 중"
            } else {
                "3초 주기 안심 차임벨 작동 중"
            }
            if (state.cooldownSecondsRemaining > 0) {
                val m = state.cooldownSecondsRemaining / 60
                val s = state.cooldownSecondsRemaining % 60
                val timeStr = String.format("%02d:%02d", m, s)
                guardianBannerTitle.text = "5분 휴게소 안심 가디언 작동 중 (남은 시간 $timeStr)"
                guardianBannerDetail.text =
                    "쾌적 21.0℃ 유지 · 창문 조작 잠금(도어 버튼 터치 방지) · $voiceBannerNote (※ 장기 방치 절대 금지)"
            } else {
                guardianBannerTitle.text = "⚠️ 5분 휴게소 안심 시간 경과! 즉시 차량으로 복귀해 주세요"
                guardianBannerDetail.text =
                    "지정된 5분이 지났습니다. 반려견 안전을 위해 즉시 복귀해 주세요 (실내 21.0℃ 공조 · 창문 잠금 · $voiceBannerNote 유지 중)"
            }
        } else {
            guardianBannerView.visibility = View.GONE
        }

        // 4. Preset Hero Cards Highlighting & Timers
        presetCardViews.forEach { (cardMode, cardView) ->
            val isSelected = cardMode == mode
            val isParkedOnlyBlocked = (cardMode == PetPresetMode.QUICK_STOP_GUARDIAN && state.isDrivingRestricted && !isSelected)
            cardView.background = heroCardBg(isSelected, cardMode.accentHex)
            cardView.alpha = if (isParkedOnlyBlocked) 0.48f else 1.0f

            presetCheckBadges[cardMode]?.apply {
                if (isSelected) {
                    val m = state.cooldownSecondsRemaining / 60
                    val s = state.cooldownSecondsRemaining % 60
                    text = when {
                        cardMode == PetPresetMode.POST_PLAY_COOLDOWN && state.cooldownSecondsRemaining > 0 ->
                            String.format("COOLING %02d:%02d", m, s)
                        cardMode == PetPresetMode.QUICK_STOP_GUARDIAN && state.cooldownSecondsRemaining > 0 ->
                            String.format("GUARDIAN %02d:%02d", m, s)
                        cardMode == PetPresetMode.QUICK_STOP_GUARDIAN && state.cooldownSecondsRemaining == 0 ->
                            "TIME OVER ⚠️"
                        else -> "ACTIVE ●"
                    }
                    setTextColor(Color.WHITE)
                    background = roundedPillBg(cardMode.accentHex, cardMode.accentHex)
                } else if (isParkedOnlyBlocked) {
                    text = "정차(P) 전용"
                    setTextColor(Color.parseColor("#FCA5A5"))
                    background = roundedPillBg("#450A0A", "#EF4444")
                } else {
                    text = if (cardMode == PetPresetMode.OFF) "STANDBY" else cardMode.badgeKo
                    setTextColor(Color.parseColor(cardMode.accentHex))
                    background = roundedPillBg("#131B26", cardMode.accentHex)
                }
            }

            presetSubtitleViews[cardMode]?.apply {
                val m = state.cooldownSecondsRemaining / 60
                val s = state.cooldownSecondsRemaining % 60
                text = when {
                    cardMode == PetPresetMode.POST_PLAY_COOLDOWN && isSelected && state.cooldownSecondsRemaining > 0 ->
                        "급속 쿨다운 진행 중 (${String.format("%02d:%02d", m, s)} 남음) → 릴렉스 자동전환"
                    cardMode == PetPresetMode.QUICK_STOP_GUARDIAN && isSelected && state.cooldownSecondsRemaining > 0 ->
                        "휴게소 안심 가디언 작동 중 (${String.format("%02d:%02d", m, s)} 남음) · 창문 잠금 유지"
                    cardMode == PetPresetMode.QUICK_STOP_GUARDIAN && isSelected && state.cooldownSecondsRemaining == 0 ->
                        "⚠️ 5분 경과 — 즉시 차량 복귀 필요 (공조·창문잠금 유지 중)"
                    else -> cardMode.subtitleKo
                }
            }
        }

        // 5. Telemetry Cards
        val hexColor = String.format("#%06X", 0xFFFFFF and state.ambientColorRgb)
        ambientGlowOrb.background = circularGlow(hexColor, 18)
        ambientValueText.text = "${state.ambientBrightness}% BRIGHTNESS"
        ambientDetailText.text = "$hexColor · 2열 독서등: ${if (state.isRearReadingLightOff) "소등(차단)" else "정상"}"

        tempBigValueText.text = "${state.targetTempCelsius}℃"
        val dirLabel = when (state.fanDirection) {
            FanDirectionType.FLOOR -> "하향(발밑)"
            FanDirectionType.FACE_AND_FLOOR -> "전방&하향"
            FanDirectionType.FACE -> "전방"
            FanDirectionType.DEFROST -> "성에제거"
            else -> "일반"
        }
        val recLabel = if (state.isRecirculationOn) "내기순환(차단)" else "외기유입(환기)"
        val winLabel = if (state.isWindowsClosed) " | 창문밀폐" else ""
        val ventLabel = if (state.isRearSeatVentilationOn) " | 2열통풍 ON" else ""
        val batLabel = " | 배터리 ${state.evBatteryLevelPercent.toInt()}%"
        tempDetailText.text = "실내 ${state.cabinTempCelsius}℃ | 풍량 ${state.fanSpeedLevel}단 | $dirLabel | $recLabel$winLabel$ventLabel$batLabel"

        airCleanerBadge.visibility = if (state.isAirCleanerActive) View.VISIBLE else View.GONE
        seatVentBadge.visibility = if (state.isRearSeatVentilationOn) View.VISIBLE else View.GONE
        windowLockBadge.visibility = if (state.isWindowLockActive) View.VISIBLE else View.GONE

        audioTitleText.text = state.activeSoundProfile.titleKo
        audioDetailText.text = "${state.activeSoundProfile.descriptionKo} (출력: ${vol}%) · ${state.voiceSourceLabel}"

        when {
            state.isRecordingVoice -> {
                val rem = state.recordingSecondsRemaining.coerceAtLeast(1)
                voiceStatusBadge.text = "🔴 녹음 중 (${rem}초)"
                voiceStatusBadge.setTextColor(Color.parseColor("#FCA5A5"))
                voiceStatusBadge.background = roundedPillBg("#450A0A", "#EF4444")
                btnRecordVoice.text = "🔴 녹음 중 (${rem}초)"
            }
            state.hasCustomVoiceRecording -> {
                voiceStatusBadge.text = "🎙️ 보이스 ON"
                voiceStatusBadge.setTextColor(Color.parseColor("#FDE68A"))
                voiceStatusBadge.background = roundedPillBg("#451A03", "#F59E0B")
                btnRecordVoice.text = "🎙️ 다시녹음(5초)"
            }
            else -> {
                voiceStatusBadge.text = "기본 차임벨"
                voiceStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                voiceStatusBadge.background = roundedPillBg("#1E293B", "#475569")
                btnRecordVoice.text = "🎙️ 음성녹음(5초)"
            }
        }
        btnPreviewVoice.visibility = if (state.hasCustomVoiceRecording && !state.isRecordingVoice) View.VISIBLE else View.GONE
        btnClearVoice.visibility = if (state.hasCustomVoiceRecording && !state.isRecordingVoice) View.VISIBLE else View.GONE

        // Alpha lock for fine-tuning and voice recording buttons during driving (CarUxRestrictions)
        val fineTuneEnabled = !state.isDrivingRestricted
        listOf(
            btnBrightnessMinus,
            btnBrightnessPlus,
            btnTempMinus,
            btnTempPlus,
            btnRecordVoice,
            btnTtsVoice,
            btnPreviewVoice,
            btnClearVoice
        ).forEach { btn ->
            btn.alpha = if (fineTuneEnabled) 1.0f else 0.35f
        }

        // Logs in engineering console
        logContainerText.text = state.eventLogs.joinToString("\n")
    }

    private fun createPillBadge(textVal: String, bgHex: String, borderHex: String): TextView {
        return TextView(this).apply {
            text = textVal
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg(bgHex, borderHex)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
        }
    }

    private fun createMiniRoundButton(symbol: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = symbol
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedPillBg("#1E293B", "#475569")
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { leftMargin = dp(6) }
            setOnClickListener { onClick() }
        }
    }

    private fun createVoiceActionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedContainerBg("#1E293B", "#334155", 8)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply { rightMargin = dp(6) }
            setOnClickListener { onClick() }
        }
    }

    private fun createConsoleButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            background = roundedContainerBg("#1E293B", "#334155", 8)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(6)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun heroCardBg(isSelected: Boolean, accentHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            if (isSelected) {
                setColor(Color.parseColor("#152233"))
                setStroke(dp(2), Color.parseColor(accentHex))
            } else {
                setColor(Color.parseColor("#101722"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
        }
    }

    private fun roundedContainerBg(fillHex: String, strokeHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun roundedPillBg(fillHex: String, strokeHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(24).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun circularGlow(colorHex: String, sizeDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(colorHex))
            setStroke(dp(3), Color.parseColor("#FFFFFF"))
        }
    }

    private fun gradientAura(topColorHex: String, bottomColorHex: String): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(topColorHex), Color.parseColor(bottomColorHex))
        )
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        stateJob?.cancel()
        try {
            unbindService(serviceConnection)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
