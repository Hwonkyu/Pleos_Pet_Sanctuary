package ai.pleos.petsanctuary.domain

import ai.pleos.petsanctuary.audio.PetAudioController
import ai.pleos.petsanctuary.hal.PetVehicleHal
import ai.pleos.petsanctuary.model.PetPresetMode
import ai.pleos.petsanctuary.model.PetSanctuaryState
import ai.pleos.petsanctuary.model.SoundProfile
import ai.pleos.petsanctuary.model.VehicleBaselineSnapshot
import ai.pleos.petsanctuary.model.VehicleCapabilities
import ai.pleos.petsanctuary.model.VehicleGearState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Core Domain Controller & Safety State Machine for Pleos Pet Sanctuary.
 *
 * Responsibilities:
 * 1. Applies coordinated Light + HVAC + Window + Acoustic Therapy presets via [PetVehicleHal] & [PetAudioController].
 * 2. Captures pre-activation vehicle snapshot and restores original settings on [PetPresetMode.OFF].
 * 3. Enforces AAOS `CarUxRestrictions` and automatic ambient light dimming when gear != PARK.
 * 4. Handles navigation voice guidance AudioFocus ducking (20% volume) and recovery.
 * 5. Performs capability checks and safe bounds clamping for lower-trim vehicles.
 * 6. Manages timed modes such as [PetPresetMode.POST_PLAY_COOLDOWN] with automatic transition.
 */
class PetSanctuaryController(
    private val vehicleHal: PetVehicleHal,
    private val audioController: PetAudioController,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        const val AUDIOFOCUS_GAIN = 1
        const val AUDIOFOCUS_LOSS = -1
        const val AUDIOFOCUS_LOSS_TRANSIENT = -2
        const val AUDIOFOCUS_LOSS_TRANSIENT_MAY_DUCK = -3

        const val NORMAL_VOLUME = 1.0f
        const val DUCKED_NAVI_VOLUME = 0.2f
        const val MUTED_VOLUME = 0.0f
        const val MIN_GUARDIAN_EV_BATTERY_PERCENT = 20.0f
    }

    private val _state = MutableStateFlow(PetSanctuaryState())
    val state: StateFlow<PetSanctuaryState> = _state.asStateFlow()

    private var cooldownJob: Job? = null
    private var recordingCountdownJob: Job? = null

    fun initialize() {
        vehicleHal.initialize(
            onGearChanged = { gear -> handleGearTransition(gear) },
            onCabinTempChanged = { tempC ->
                _state.update { it.copy(cabinTempCelsius = tempC) }
            },
            onCapabilitiesLoaded = { caps ->
                _state.update { it.copy(capabilities = caps) }
            },
            onInitialBaselineRead = { reading ->
                _state.update { current ->
                    if (current.activeMode == PetPresetMode.OFF && current.baselineSnapshot == null) {
                        current.copy(
                            targetTempCelsius = reading.targetTempCelsius ?: current.targetTempCelsius,
                            fanSpeedLevel = reading.fanSpeedLevel ?: current.fanSpeedLevel,
                            fanDirection = reading.fanDirection ?: current.fanDirection,
                            isRecirculationOn = reading.isRecirculationOn ?: current.isRecirculationOn,
                            isAcEnabled = reading.isAcEnabled ?: current.isAcEnabled,
                            isAirCleanerActive = reading.isAirCleanerActive ?: current.isAirCleanerActive,
                            isAmbientLightOn = reading.isAmbientLightOn ?: current.isAmbientLightOn,
                            ambientPleosColorIndex = reading.ambientPleosColorIndex ?: current.ambientPleosColorIndex,
                            ambientBrightness = reading.ambientBrightness ?: current.ambientBrightness,
                            isWindowLockActive = reading.isWindowLockActive ?: current.isWindowLockActive,
                            isRearSeatVentilationOn = reading.isRearSeatVentilationOn ?: current.isRearSeatVentilationOn,
                            evBatteryLevelPercent = reading.evBatteryLevelPercent ?: current.evBatteryLevelPercent
                        )
                    } else {
                        current
                    }
                }
            },
            onEvBatteryChanged = { pct ->
                _state.update { it.copy(evBatteryLevelPercent = pct) }
            }
        )
        _state.update {
            it.copy(
                hasCustomVoiceRecording = audioController.hasCustomVoice(),
                voiceSourceLabel = audioController.getVoiceSourceLabel()
            )
        }
        appendLog("시스템 초기화 완료 (Pleos Vehicle SDK 연동 준비)")
    }

    private fun recordVhalFallback(propertyLabel: String) {
        _state.update { current ->
            val updatedSet = current.vhalFallbackProperties + propertyLabel
            current.copy(vhalFallbackProperties = updatedSet)
        }
        appendLog("⚠️ VHAL 하드웨어 속성 폴백: [$propertyLabel] 미지원 → 앱 내 가상 시뮬레이터 상태로 유지")
    }

    fun requestTunnelWindowPrompt() {
        _state.update { it.copy(pendingTunnelWindowPrompt = true) }
    }

    fun dismissTunnelWindowPrompt() {
        _state.update { it.copy(pendingTunnelWindowPrompt = false) }
    }

    /**
     * Activates a Pet Sanctuary preset or restores original vehicle state when [PetPresetMode.OFF] is selected.
     *
     * @param closeWindowsOverride Optional user decision from confirmation dialog on whether to close windows
     *        in [PetPresetMode.TUNNEL_SAFEGUARD].
     */
    fun activatePreset(mode: PetPresetMode, closeWindowsOverride: Boolean? = null) {
        if (mode == PetPresetMode.OFF) {
            restoreBaselineAndStop()
            return
        }

        val current = _state.value

        // Safety Guard 1: QUICK_STOP_GUARDIAN can only be activated when parked (Gear == PARK)
        if (mode == PetPresetMode.QUICK_STOP_GUARDIAN && current.currentGear.isDriving) {
            val blockMsg = "⚠️ [정차 안전 제한] '5분 휴게소 안심' 모드는 완전히 정차(기어 P)한 후 하차 시에만 가동할 수 있습니다."
            _state.update { it.copy(lastStatusMessage = blockMsg) }
            appendLog(blockMsg)
            return
        }

        // Safety Guard 2: Block QUICK_STOP_GUARDIAN if EV battery < 20% to prevent battery depletion while parked
        if (mode == PetPresetMode.QUICK_STOP_GUARDIAN && current.evBatteryLevelPercent < MIN_GUARDIAN_EV_BATTERY_PERCENT) {
            val lowBatMsg = "⚠️ [EV 배터리 보호 제한] 배터리 잔량(${current.evBatteryLevelPercent.toInt()}%)이 20% 미만이어서 정차 중 공조 유지 가동이 제한됩니다."
            _state.update { it.copy(lastStatusMessage = lowBatMsg) }
            appendLog(lowBatMsg)
            return
        }

        val baseSnapshot = current.baselineSnapshot ?: VehicleBaselineSnapshot(
            previousTempCelsius = current.targetTempCelsius,
            previousFanSpeed = current.fanSpeedLevel,
            previousFanDirection = current.fanDirection,
            previousRecirculationOn = current.isRecirculationOn,
            previousAcEnabled = current.isAcEnabled,
            previousAirCleanerEnabled = current.isAirCleanerActive,
            previousAmbientLightOn = current.isAmbientLightOn,
            previousAmbientColorRgb = current.ambientColorRgb,
            previousPleosColorIndex = current.ambientPleosColorIndex,
            previousAmbientBrightness = current.ambientBrightness,
            previousAutoColorByDriveMode = current.isAutoColorByDriveModeEnabled,
            previousWindowLockOn = current.isWindowLockActive,
            didModifyWindowLock = false,
            previousRearSeatVentilationOn = current.isRearSeatVentilationOn
        )

        val caps = vehicleHal.getCapabilities()
        val isDriving = current.currentGear.isDriving

        // Reset VHAL fallback warnings for this activation
        _state.update { it.copy(vhalFallbackProperties = emptySet(), pendingTunnelWindowPrompt = false) }

        // 1. Calculate safe target brightness (dimmed if driving)
        val rawTargetBrightness = if (isDriving) {
            min(mode.parkedBrightness, mode.drivingSafeBrightness)
                .coerceAtMost(PetPresetMode.MAX_SAFE_DRIVING_BRIGHTNESS)
        } else {
            mode.parkedBrightness
        }
        val clampedBrightness = rawTargetBrightness.coerceIn(caps.minBrightness, caps.maxBrightness)

        // 2. Calculate clamped HVAC temperature and fan speed
        val clampedTemp = mode.targetTempCelsius.coerceIn(caps.minTempCelsius, caps.maxTempCelsius)
        val clampedFan = mode.targetFanSpeed.coerceIn(caps.minFanSpeed, caps.maxFanSpeed)

        // 3. Apply Light controls (with graceful degradation if unsupported)
        val degradedFeatures = mutableListOf<String>()
        if (caps.supportsAmbientLight) {
            // Prevent drive mode (Eco/Sport) from overriding pet calming color
            vehicleHal.setAmbientColorAutoByDriveMode(false)
            vehicleHal.setAmbientLightSwitch(
                true,
                onFailed = { recordVhalFallback("앰비언트전원") }
            )
            if (caps.supportsAmbientColorControl) {
                vehicleHal.setAmbientLightColor(
                    mode.ambientColorRgb,
                    mode.pleosColorIndex,
                    onFailed = { recordVhalFallback("앰비언트컬러") }
                )
            } else {
                degradedFeatures.add("앰비언트 컬러 미지원 트림")
            }
            vehicleHal.setAmbientLightBrightness(
                clampedBrightness,
                onFailed = { recordVhalFallback("앰비언트밝기") }
            )
        } else {
            degradedFeatures.add("앰비언트 무드램프 미지원 트림")
        }

        if (caps.supportsRearReadingLightControl && mode.turnOffRearReadingLight) {
            vehicleHal.setRearReadingLightOff(
                true,
                onFailed = { recordVhalFallback("독서등소등") }
            )
        }

        // 4. Apply HVAC & Window controls
        vehicleHal.setHvacPowerAndAc(powerOn = true, acOn = true)
        vehicleHal.setHvacTargetTemperature(
            clampedTemp,
            onFailed = { recordVhalFallback("목표온도") }
        )
        vehicleHal.setHvacFanSpeed(
            clampedFan,
            onFailed = { recordVhalFallback("풍량단수") }
        )
        vehicleHal.setHvacFanDirection(
            mode.fanDirection,
            onFailed = { recordVhalFallback("풍향제어") }
        )

        mode.recirculationOn?.let { recOn ->
            vehicleHal.setHvacRecirculation(
                recOn,
                onFailed = { recordVhalFallback("내외기순환") }
            )
        }

        val airCleanerApplied = if (caps.supportsAirCleaner) {
            vehicleHal.setHvacAirCleaner(
                mode.airCleanerEnabled,
                onFailed = { recordVhalFallback("공기청정") }
            )
            mode.airCleanerEnabled
        } else {
            degradedFeatures.add("공기청정 미지원 트림")
            false
        }

        val seatVentApplied = if (caps.supportsSeatVentilation) {
            vehicleHal.setRearSeatVentilation(
                mode.rearSeatVentilation,
                onFailed = { recordVhalFallback("2열시트통풍") }
            )
            mode.rearSeatVentilation
        } else {
            false
        }

        // Window Lock preservation: only lock windows when mode requires it (QUICK_STOP_GUARDIAN);
        // when leaving QUICK_STOP_GUARDIAN, restore the driver's original window lock state.
        // Never forcefully unlock windows if the driver already had them locked in other modes.
        val effectiveWindowLock: Boolean
        val updatedSnapshot: VehicleBaselineSnapshot
        if (mode.windowLock) {
            if (caps.supportsWindowLock) {
                vehicleHal.setWindowLock(
                    true,
                    onFailed = { recordVhalFallback("창문잠금") }
                )
            }
            effectiveWindowLock = caps.supportsWindowLock
            updatedSnapshot = baseSnapshot.copy(didModifyWindowLock = true)
        } else if (current.activeMode == PetPresetMode.QUICK_STOP_GUARDIAN || baseSnapshot.didModifyWindowLock) {
            if (caps.supportsWindowLock) {
                vehicleHal.setWindowLock(baseSnapshot.previousWindowLockOn)
            }
            effectiveWindowLock = baseSnapshot.previousWindowLockOn
            updatedSnapshot = baseSnapshot
        } else {
            effectiveWindowLock = current.isWindowLockActive
            updatedSnapshot = baseSnapshot
        }

        val shouldCloseWindows = closeWindowsOverride ?: mode.windowsClosed
        if (caps.supportsWindowPosition && shouldCloseWindows) {
            vehicleHal.setWindowsClosed(
                true,
                onFailed = { recordVhalFallback("창문밀폐") }
            )
        }
        if (mode == PetPresetMode.TUNNEL_SAFEGUARD && !shouldCloseWindows) {
            degradedFeatures.add("창문 닫힘 제외(사용자 선택)")
        }

        // 5. Start Acoustic Therapy (respecting user mute & navigation ducking)
        val targetVolume = when {
            current.isUserMuted -> MUTED_VOLUME
            current.isAudioDuckedForNavi -> DUCKED_NAVI_VOLUME
            else -> NORMAL_VOLUME
        }
        audioController.startProfile(mode.soundProfile)
        audioController.setVolumeMultiplier(targetVolume)

        // 6. Handle Timed Modes (QUICK_STOP_GUARDIAN 5-min timer & POST_PLAY_COOLDOWN 7-min timer)
        cooldownJob?.cancel()
        if (mode.isTimedCooldown && mode.cooldownDurationSeconds > 0) {
            cooldownJob = coroutineScope.launch {
                var remaining = mode.cooldownDurationSeconds
                while (remaining > 0) {
                    _state.update { it.copy(cooldownSecondsRemaining = remaining) }
                    delay(1000L)
                    remaining--
                }
                _state.update { it.copy(cooldownSecondsRemaining = 0) }
                if (mode == PetPresetMode.POST_PLAY_COOLDOWN) {
                    appendLog("[산책 후 쿨다운] 7분 급속 냉각 완료 → 릴렉스 안심 모드로 자동 전환")
                    activatePreset(PetPresetMode.RELAX)
                } else if (mode == PetPresetMode.QUICK_STOP_GUARDIAN) {
                    val warnMsg = "⚠️ [5분 휴게소 안심 시간 경과] 지정된 5분이 지났습니다! 장기 방치는 위험하므로 즉시 복귀해 주세요 (공조·창문잠금 유지 중)"
                    _state.update { it.copy(lastStatusMessage = warnMsg) }
                    appendLog(warnMsg)
                }
            }
        }

        val statusSuffix = if (degradedFeatures.isEmpty()) {
            "차량 제어 연동 완료"
        } else {
            "차량 제어 연동 (${degradedFeatures.joinToString(", ")})"
        }
        val statusMsg = "[${mode.titleKo}] 가동 중 (${clampedTemp}℃ · 풍량 ${clampedFan}단 · 밝기 ${clampedBrightness}%) - $statusSuffix"

        _state.update {
            it.copy(
                activeMode = mode,
                baselineSnapshot = updatedSnapshot,
                targetTempCelsius = clampedTemp,
                fanSpeedLevel = clampedFan,
                fanDirection = mode.fanDirection,
                isRecirculationOn = mode.recirculationOn ?: it.isRecirculationOn,
                isAcEnabled = true,
                isAirCleanerActive = airCleanerApplied,
                isRearSeatVentilationOn = seatVentApplied,
                isAmbientLightOn = caps.supportsAmbientLight,
                ambientColorRgb = if (caps.supportsAmbientLight && caps.supportsAmbientColorControl) {
                    mode.ambientColorRgb
                } else {
                    it.ambientColorRgb
                },
                ambientPleosColorIndex = if (caps.supportsAmbientLight && caps.supportsAmbientColorControl) {
                    mode.pleosColorIndex
                } else {
                    it.ambientPleosColorIndex
                },
                ambientBrightness = if (caps.supportsAmbientLight) clampedBrightness else 0,
                isRearReadingLightOff = caps.supportsRearReadingLightControl && mode.turnOffRearReadingLight,
                isAutoColorByDriveModeEnabled = if (caps.supportsAmbientLight) false else it.isAutoColorByDriveModeEnabled,
                isWindowLockActive = effectiveWindowLock,
                isWindowsClosed = caps.supportsWindowPosition && shouldCloseWindows,
                isGuardianModeActive = (mode == PetPresetMode.QUICK_STOP_GUARDIAN),
                cooldownSecondsRemaining = if (mode.isTimedCooldown) mode.cooldownDurationSeconds else 0,
                activeSoundProfile = mode.soundProfile,
                audioVolumeMultiplier = targetVolume,
                isAudioPausedByFocusLoss = false,
                capabilities = caps,
                lastStatusMessage = statusMsg
            )
        }
        appendLog(statusMsg)
    }

    private fun restoreBaselineAndStop() {
        cooldownJob?.cancel()
        cooldownJob = null

        val current = _state.value
        val snapshot = current.baselineSnapshot
        val caps = vehicleHal.getCapabilities()

        audioController.stop()

        if (snapshot != null) {
            if (caps.supportsAmbientLight) {
                vehicleHal.setAmbientLightSwitch(snapshot.previousAmbientLightOn)
                if (caps.supportsAmbientColorControl) {
                    vehicleHal.setAmbientLightColor(
                        snapshot.previousAmbientColorRgb,
                        snapshot.previousPleosColorIndex
                    )
                }
                vehicleHal.setAmbientLightBrightness(snapshot.previousAmbientBrightness)
                vehicleHal.setAmbientColorAutoByDriveMode(snapshot.previousAutoColorByDriveMode)
            }
            if (caps.supportsRearReadingLightControl) {
                vehicleHal.setRearReadingLightOff(false)
            }
            vehicleHal.setHvacPowerAndAc(powerOn = true, acOn = snapshot.previousAcEnabled)
            vehicleHal.setHvacTargetTemperature(snapshot.previousTempCelsius)
            vehicleHal.setHvacFanSpeed(snapshot.previousFanSpeed)
            vehicleHal.setHvacFanDirection(snapshot.previousFanDirection)
            vehicleHal.setHvacRecirculation(snapshot.previousRecirculationOn)

            if (caps.supportsAirCleaner) {
                vehicleHal.setHvacAirCleaner(snapshot.previousAirCleanerEnabled)
            }
            if (caps.supportsSeatVentilation) {
                vehicleHal.setRearSeatVentilation(snapshot.previousRearSeatVentilationOn)
            }
            if (caps.supportsWindowLock && snapshot.didModifyWindowLock) {
                vehicleHal.setWindowLock(snapshot.previousWindowLockOn)
            }

            _state.update {
                it.copy(
                    activeMode = PetPresetMode.OFF,
                    targetTempCelsius = snapshot.previousTempCelsius,
                    fanSpeedLevel = snapshot.previousFanSpeed,
                    fanDirection = snapshot.previousFanDirection,
                    isRecirculationOn = snapshot.previousRecirculationOn,
                    isAcEnabled = snapshot.previousAcEnabled,
                    isAirCleanerActive = snapshot.previousAirCleanerEnabled,
                    isRearSeatVentilationOn = snapshot.previousRearSeatVentilationOn,
                    isAmbientLightOn = snapshot.previousAmbientLightOn,
                    ambientColorRgb = snapshot.previousAmbientColorRgb,
                    ambientPleosColorIndex = snapshot.previousPleosColorIndex,
                    ambientBrightness = snapshot.previousAmbientBrightness,
                    isRearReadingLightOff = false,
                    isAutoColorByDriveModeEnabled = snapshot.previousAutoColorByDriveMode,
                    isWindowLockActive = snapshot.previousWindowLockOn,
                    isWindowsClosed = false,
                    isGuardianModeActive = false,
                    cooldownSecondsRemaining = 0,
                    activeSoundProfile = SoundProfile.NONE,
                    audioVolumeMultiplier = MUTED_VOLUME,
                    isUserMuted = false,
                    isAudioDuckedForNavi = false,
                    isAudioPausedByFocusLoss = false,
                    baselineSnapshot = null,
                    vhalFallbackProperties = emptySet(),
                    pendingTunnelWindowPrompt = false,
                    lastStatusMessage = "케어 종료 — 진입 전 차량 공조·조명 설정이 원래대로 복원되었습니다."
                )
            }
            appendLog("케어 모드 해제: 기존 차량 설정(${snapshot.previousTempCelsius}℃, 밝기 ${snapshot.previousAmbientBrightness}%) 복원 완료")
        } else {
            _state.update {
                it.copy(
                    activeMode = PetPresetMode.OFF,
                    activeSoundProfile = SoundProfile.NONE,
                    audioVolumeMultiplier = MUTED_VOLUME,
                    isUserMuted = false,
                    isGuardianModeActive = false,
                    isRearSeatVentilationOn = false,
                    cooldownSecondsRemaining = 0,
                    vhalFallbackProperties = emptySet(),
                    pendingTunnelWindowPrompt = false,
                    lastStatusMessage = "케어 대기 중"
                )
            }
            appendLog("케어 모드 대기 상태")
        }
    }

    /**
     * Handles real-time Gear Selection changes (`P`, `R`, `N`, `D`) and enforces:
     * 1. `CarUxRestrictions` UI lock (`isDrivingRestricted = true` when gear != PARK).
     * 2. Safe Driving Ambient Light Dimming (caps brightness to <= 30% during driving).
     */
    fun handleGearTransition(newGear: VehicleGearState) {
        val current = _state.value
        val isDriving = newGear.isDriving
        val mode = current.activeMode
        val caps = vehicleHal.getCapabilities()

        if (mode != PetPresetMode.OFF && caps.supportsAmbientLight) {
            val targetBrightness = if (isDriving) {
                min(mode.parkedBrightness, mode.drivingSafeBrightness)
                    .coerceAtMost(PetPresetMode.MAX_SAFE_DRIVING_BRIGHTNESS)
            } else {
                mode.parkedBrightness
            }.coerceIn(caps.minBrightness, caps.maxBrightness)

            vehicleHal.setAmbientLightBrightness(targetBrightness)

            val gearMsg = if (isDriving) {
                "기어 [${newGear.label}] 전환: 주행 안전 규제(CarUxRestrictions) 활성화 및 조명 안전 디밍(${targetBrightness}%)"
            } else {
                "기어 [P 정차] 전환: 세부 조작 잠금 해제 및 정차 조명 밝기(${targetBrightness}%) 복귀"
            }

            _state.update {
                it.copy(
                    currentGear = newGear,
                    isDrivingRestricted = isDriving,
                    ambientBrightness = targetBrightness,
                    lastStatusMessage = gearMsg
                )
            }
            appendLog(gearMsg)
        } else {
            val gearMsg = if (isDriving) {
                "기어 [${newGear.label}] 감지: 주행 중 안전 모드로 전환됩니다."
            } else {
                "기어 [P 정차] 감지: 모든 설정 조작이 가능합니다."
            }
            _state.update {
                it.copy(
                    currentGear = newGear,
                    isDrivingRestricted = isDriving,
                    lastStatusMessage = gearMsg
                )
            }
            appendLog(gearMsg)
        }
    }

    /**
     * Handles Android Automotive AudioFocus events so Navigation prompts are never drowned out.
     */
    fun handleAudioFocusChange(focusChange: Int) {
        val current = _state.value
        if (current.activeMode == PetPresetMode.OFF) return

        when (focusChange) {
            AUDIOFOCUS_LOSS_TRANSIENT_MAY_DUCK -> {
                val vol = if (current.isUserMuted) MUTED_VOLUME else DUCKED_NAVI_VOLUME
                audioController.setVolumeMultiplier(vol)
                val msg = "내비게이션 음성 안내 감지: 반려견 테라피 사운드 볼륨 20% 자동 감쇠(Ducking)"
                _state.update {
                    it.copy(
                        audioVolumeMultiplier = vol,
                        isAudioDuckedForNavi = true,
                        isAudioPausedByFocusLoss = false,
                        lastStatusMessage = msg
                    )
                }
                appendLog(msg)
            }
            AUDIOFOCUS_GAIN -> {
                val vol = if (current.isUserMuted) MUTED_VOLUME else NORMAL_VOLUME
                audioController.setVolumeMultiplier(vol)
                val msg = "내비게이션 안내 종료: 테라피 사운드 볼륨 100% 복원"
                _state.update {
                    it.copy(
                        audioVolumeMultiplier = vol,
                        isAudioDuckedForNavi = false,
                        isAudioPausedByFocusLoss = false,
                        lastStatusMessage = msg
                    )
                }
                appendLog(msg)
            }
            AUDIOFOCUS_LOSS, AUDIOFOCUS_LOSS_TRANSIENT -> {
                audioController.setVolumeMultiplier(MUTED_VOLUME)
                val msg = "통화/긴급 오디오 포커스 전환: 테라피 사운드 일시 음소거"
                _state.update {
                    it.copy(
                        audioVolumeMultiplier = MUTED_VOLUME,
                        isAudioDuckedForNavi = false,
                        isAudioPausedByFocusLoss = true,
                        lastStatusMessage = msg
                    )
                }
                appendLog(msg)
            }
        }
    }

    /**
     * Toggles soundscape mute independently while keeping HVAC & ambient lighting active.
     * Safe one-touch action allowed in both parked and driving states.
     */
    fun toggleAudioMute(): Boolean {
        val current = _state.value
        val nextMuted = !current.isUserMuted
        val effectiveVol = when {
            nextMuted || current.isAudioPausedByFocusLoss || current.activeMode == PetPresetMode.OFF -> MUTED_VOLUME
            current.isAudioDuckedForNavi -> DUCKED_NAVI_VOLUME
            else -> NORMAL_VOLUME
        }
        audioController.setVolumeMultiplier(effectiveVol)
        val msg = if (nextMuted) {
            "🔇 사운드 테라피 음소거 (공조·조명 케어는 그대로 유지됩니다)"
        } else {
            "🔊 사운드 테라피 음소거 해제 (볼륨 ${(effectiveVol * 100).toInt()}% 복원)"
        }
        _state.update {
            it.copy(
                isUserMuted = nextMuted,
                audioVolumeMultiplier = effectiveVol,
                lastStatusMessage = msg
            )
        }
        appendLog(msg)
        return nextMuted
    }

    /**
     * Manual fine-tuning of target temperature.
     * Blocked when `isDrivingRestricted == true` to comply with AAOS `CarUxRestrictions`.
     */
    fun adjustTargetTemperature(deltaCelsius: Float): Boolean {
        val current = _state.value
        if (current.isDrivingRestricted) {
            val msg = "주행 안전 규제(CarUxRestrictions): 주행 중에는 미세 온도 조작이 제한됩니다. 원터치 프리셋을 이용하세요."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        val caps = vehicleHal.getCapabilities()
        val nextTemp = ((current.targetTempCelsius + deltaCelsius) * 2).toInt() / 2.0f
        val clamped = nextTemp.coerceIn(caps.minTempCelsius, caps.maxTempCelsius)
        vehicleHal.setHvacTargetTemperature(clamped)
        val msg = "실내 목표 온도 수동 설정: ${clamped}℃"
        _state.update {
            it.copy(
                targetTempCelsius = clamped,
                lastStatusMessage = msg
            )
        }
        appendLog(msg)
        return true
    }

    /**
     * Manual fine-tuning of ambient brightness.
     * Blocked when `isDrivingRestricted == true` to comply with AAOS `CarUxRestrictions`.
     */
    fun adjustAmbientBrightness(newBrightness: Int): Boolean {
        val current = _state.value
        if (current.isDrivingRestricted) {
            val msg = "주행 안전 규제(CarUxRestrictions): 주행 중에는 조명 밝기 슬라이더 조작이 제한됩니다."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        val caps = vehicleHal.getCapabilities()
        if (!caps.supportsAmbientLight) {
            val msg = "현재 차량 트림은 앰비언트 라이트 밝기 제어를 지원하지 않습니다."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        val clamped = newBrightness.coerceIn(caps.minBrightness, caps.maxBrightness)
        vehicleHal.setAmbientLightBrightness(clamped)
        val msg = "앰비언트 조명 밝기 수동 설정: ${clamped}%"
        _state.update {
            it.copy(
                ambientBrightness = clamped,
                lastStatusMessage = msg
            )
        }
        appendLog(msg)
        return true
    }

    /**
     * Records 5 seconds of the owner's reassuring voice via microphone (PCM 16-bit 22,050Hz)
     * and mixes it periodically into the active acoustic therapy stream.
     * Blocked when `isDrivingRestricted == true` to comply with AAOS `CarUxRestrictions`.
     */
    fun startOwnerVoiceRecording(durationSeconds: Int = 5): Boolean {
        val current = _state.value
        if (current.isDrivingRestricted) {
            val msg = "주행 안전 규제(CarUxRestrictions): 주행 중에는 보호자 음성 녹음이 제한됩니다. 기어 P(정차) 상태에서 녹음해 주세요."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        if (current.isRecordingVoice) {
            return false
        }
        val startMsg = "🎙️ 보호자 안심 음성을 녹음 중입니다 (${durationSeconds}초)... 따뜻한 목소리를 들려주세요."
        _state.update {
            it.copy(
                isRecordingVoice = true,
                recordingSecondsRemaining = durationSeconds,
                lastStatusMessage = startMsg
            )
        }
        appendLog(startMsg)

        recordingCountdownJob?.cancel()
        recordingCountdownJob = coroutineScope.launch {
            var sec = durationSeconds
            while (sec > 0 && _state.value.isRecordingVoice) {
                _state.update { it.copy(recordingSecondsRemaining = sec) }
                delay(1000L)
                sec--
            }
        }

        audioController.recordOwnerVoice(durationSeconds) { success, label ->
            recordingCountdownJob?.cancel()
            val resultMsg = if (success) {
                "🎙️ 보호자 안심 음성 적용 완료 [$label] — 테라피 사운드에 주기적으로 믹싱 재생됩니다."
            } else {
                "⚠️ $label"
            }
            _state.update {
                it.copy(
                    isRecordingVoice = false,
                    recordingSecondsRemaining = 0,
                    hasCustomVoiceRecording = audioController.hasCustomVoice(),
                    voiceSourceLabel = audioController.getVoiceSourceLabel(),
                    lastStatusMessage = resultMsg
                )
            }
            appendLog(resultMsg)
        }
        return true
    }

    /**
     * Plays the recorded owner voice or synthesized TTS reassurance voice once immediately for preview.
     * Blocked when `isDrivingRestricted == true` to comply with AAOS `CarUxRestrictions`.
     */
    fun previewCustomVoice(): Boolean {
        val current = _state.value
        if (current.isDrivingRestricted) {
            val msg = "주행 안전 규제(CarUxRestrictions): 주행 중에는 음성 미리듣기가 제한됩니다."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        if (!audioController.hasCustomVoice()) {
            val msg = "⚠️ 미리듣기할 안심 음성이 없습니다. 먼저 녹음하거나 AI 안심음성을 생성해 주세요."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        val started = audioController.previewCustomVoiceOnce()
        if (started) {
            val msg = "▶️ [${current.voiceSourceLabel}] 안심 음성 1회 미리듣기 재생 중..."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
        }
        return started
    }

    /**
     * Synthesizes a Korean reassurance voice phrase via Android TextToSpeech (or vocal formant fallback)
     * so users/evaluators can test voice reassurance mixing even on an emulator without a physical microphone.
     * Blocked when `isDrivingRestricted == true` to comply with AAOS `CarUxRestrictions`.
     */
    fun applyTtsReassuranceVoice(
        phrase: String = "아가야 안심해, 보호자가 곧 돌아옵니다. 조금만 기다려 주세요."
    ): Boolean {
        val current = _state.value
        if (current.isDrivingRestricted) {
            val msg = "주행 안전 규제(CarUxRestrictions): 주행 중에는 안심 음성 생성/변경이 제한됩니다. 기어 P(정차) 상태에서 설정해 주세요."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        val startMsg = "🗣️ 반려견 안심 음성(TTS)을 생성하고 있습니다..."
        _state.update { it.copy(lastStatusMessage = startMsg) }
        appendLog(startMsg)

        audioController.applyTtsReassuranceVoice(phrase) { _, label ->
            val doneMsg = "🗣️ 안심 음성 적용 완료 [$label] — 테라피 사운드에 주기적으로 믹싱 재생됩니다."
            _state.update {
                it.copy(
                    hasCustomVoiceRecording = audioController.hasCustomVoice(),
                    isRecordingVoice = false,
                    voiceSourceLabel = audioController.getVoiceSourceLabel(),
                    lastStatusMessage = doneMsg
                )
            }
            appendLog(doneMsg)
        }
        return true
    }

    /**
     * Clears any recorded owner voice or TTS reassurance voice and returns to pure acoustic therapy.
     * Blocked when `isDrivingRestricted == true` to comply with AAOS `CarUxRestrictions`.
     */
    fun clearCustomVoice(): Boolean {
        val current = _state.value
        if (current.isDrivingRestricted) {
            val msg = "주행 안전 규제(CarUxRestrictions): 주행 중에는 안심 음성 설정 변경이 제한됩니다."
            _state.update { it.copy(lastStatusMessage = msg) }
            appendLog(msg)
            return false
        }
        audioController.clearCustomVoice()
        val msg = "보호자 안심 음성이 해제되어 기본 테라피 사운드/차임벨 모드로 복귀했습니다."
        _state.update {
            it.copy(
                hasCustomVoiceRecording = false,
                isRecordingVoice = false,
                voiceSourceLabel = audioController.getVoiceSourceLabel(),
                lastStatusMessage = msg
            )
        }
        appendLog(msg)
        return true
    }

    fun refreshCapabilities(newCapabilities: VehicleCapabilities) {
        _state.update { it.copy(capabilities = newCapabilities) }
        appendLog("차량 하드웨어 Capability 갱신: 조명=${newCapabilities.supportsAmbientLight}, 공기청정=${newCapabilities.supportsAirCleaner}")
    }

    private fun appendLog(message: String) {
        _state.update { current ->
            val updatedLogs = (listOf(message) + current.eventLogs).take(12)
            current.copy(eventLogs = updatedLogs)
        }
    }

    fun release() {
        cooldownJob?.cancel()
        cooldownJob = null
        recordingCountdownJob?.cancel()
        recordingCountdownJob = null
        audioController.release()
        vehicleHal.release()
    }
}
