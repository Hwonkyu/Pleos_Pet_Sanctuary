package ai.pleos.petsanctuary.model

import ai.pleos.playground.vehicle.constant.control.FanDirectionType

data class VehicleCapabilities(
    val supportsAmbientLight: Boolean = true,
    val supportsAmbientColorControl: Boolean = true,
    val supportsAirCleaner: Boolean = true,
    val supportsRearReadingLightControl: Boolean = true,
    val supportsWindowLock: Boolean = true,
    val supportsWindowPosition: Boolean = true,
    val supportsSeatVentilation: Boolean = true,
    val minTempCelsius: Float = 17.0f,
    val maxTempCelsius: Float = 30.0f,
    val minFanSpeed: Int = 1,
    val maxFanSpeed: Int = 7,
    val minBrightness: Int = 0,
    val maxBrightness: Int = 100,
    val isConnectedToPleosService: Boolean = false
)

data class InitialVehicleReading(
    val targetTempCelsius: Float? = null,
    val fanSpeedLevel: Int? = null,
    val fanDirection: FanDirectionType? = null,
    val isRecirculationOn: Boolean? = null,
    val isAcEnabled: Boolean? = null,
    val isAirCleanerActive: Boolean? = null,
    val isAmbientLightOn: Boolean? = null,
    val ambientPleosColorIndex: Int? = null,
    val ambientBrightness: Int? = null,
    val isWindowLockActive: Boolean? = null,
    val isRearSeatVentilationOn: Boolean? = null,
    val evBatteryLevelPercent: Float? = null
)

data class VehicleBaselineSnapshot(
    val previousTempCelsius: Float,
    val previousFanSpeed: Int,
    val previousFanDirection: FanDirectionType,
    val previousRecirculationOn: Boolean,
    val previousAcEnabled: Boolean,
    val previousAirCleanerEnabled: Boolean,
    val previousAmbientLightOn: Boolean,
    val previousAmbientColorRgb: Int,
    val previousPleosColorIndex: Int = 1,
    val previousAmbientBrightness: Int,
    val previousAutoColorByDriveMode: Boolean,
    val previousWindowLockOn: Boolean,
    val didModifyWindowLock: Boolean = false,
    val previousRearSeatVentilationOn: Boolean = false
)

data class PetSanctuaryState(
    val activeMode: PetPresetMode = PetPresetMode.OFF,
    val currentGear: VehicleGearState = VehicleGearState.PARK,
    val isDrivingRestricted: Boolean = false,
    val cabinTempCelsius: Float = 24.5f,
    val targetTempCelsius: Float = 24.0f,
    val fanSpeedLevel: Int = 2,
    val fanDirection: FanDirectionType = FanDirectionType.FACE_AND_FLOOR,
    val isRecirculationOn: Boolean = false,
    val isAcEnabled: Boolean = true,
    val isAirCleanerActive: Boolean = false,
    val isAmbientLightOn: Boolean = true,
    val ambientColorRgb: Int = 0xFF90CAF9.toInt(),
    val ambientPleosColorIndex: Int = 1,
    val ambientBrightness: Int = 65,
    val isRearReadingLightOff: Boolean = false,
    val isAutoColorByDriveModeEnabled: Boolean = true,
    val isWindowLockActive: Boolean = false,
    val isWindowsClosed: Boolean = false,
    val isGuardianModeActive: Boolean = false,
    val cooldownSecondsRemaining: Int = 0,
    val activeSoundProfile: SoundProfile = SoundProfile.NONE,
    val audioVolumeMultiplier: Float = 0.0f,
    val isUserMuted: Boolean = false,
    val isAudioDuckedForNavi: Boolean = false,
    val isAudioPausedByFocusLoss: Boolean = false,
    val hasCustomVoiceRecording: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val recordingSecondsRemaining: Int = 0,
    val voiceSourceLabel: String = "기본 안심 차임벨 (녹음 음성 없음)",
    val isRearSeatVentilationOn: Boolean = false,
    val evBatteryLevelPercent: Float = 82.0f,
    val capabilities: VehicleCapabilities = VehicleCapabilities(),
    val baselineSnapshot: VehicleBaselineSnapshot? = null,
    val vhalFallbackProperties: Set<String> = emptySet(),
    val pendingTunnelWindowPrompt: Boolean = false,
    val lastStatusMessage: String = "케어 대기 중 — 반려견 탑승 시 모드를 선택하세요.",
    val eventLogs: List<String> = emptyList()
)
