package ai.pleos.petsanctuary.model

import ai.pleos.playground.vehicle.constant.control.FanDirectionType

enum class SoundProfile(
    val titleKo: String,
    val descriptionKo: String,
    val baseFreqHz: Double
) {
    NONE("음원 꺼짐", "사운드 테라피 비활성", 0.0),
    PINK_NOISE_60BPM("웜 핑크노이즈 & 60BPM 하모니", "반려견 심박수를 낮추는 60BPM 로우패스 화음과 핑크노이즈", 216.0),
    BROWN_NOISE_ENGINE_MASK("딥 브라운노이즈 (엔진음 차폐)", "주행 중 노면·엔진 저주파 진동음을 상쇄하는 깊은 브라운노이즈", 128.0),
    GENTLE_RAIN_ACOUSTIC("어쿠스틱 레인 & 시냇물", "외부 경적 등 돌발 소음을 중화하는 부드러운 빗소리 대역", 320.0),
    NOISE_CANCEL_DRONE("터널 저주파 차폐 사운드", "터널 진입 시 급격한 저주파 공명음을 완화하는 110Hz·55Hz 차폐음", 110.0),
    COOL_BREEZE_STREAM("청량 계곡물 & 쿨 브리즈", "메스꺼움과 멀미 긴장을 완화하는 청량한 계곡물 사운드", 340.0),
    GUARDIAN_CHIME_VOICE("휴게소 안심 차임벨 & 보호자 보이스", "홀로 남은 강아지를 달래는 3초 주기 평온 차임벨 및 보호자 안심 음성(녹음/TTS) 믹싱", 260.0)
}

enum class VehicleGearState(val label: String, val isDriving: Boolean) {
    PARK("P (주차/정차)", false),
    REVERSE("R (후진)", true),
    NEUTRAL("N (중립)", true),
    DRIVE("D (주행)", true)
}

enum class PetPresetMode(
    val titleKo: String,
    val subtitleKo: String,
    val badgeKo: String,
    val emoji: String,
    val accentHex: String,
    val ambientColorRgb: Int,
    val pleosColorIndex: Int,
    val parkedBrightness: Int,
    val drivingSafeBrightness: Int,
    val targetTempCelsius: Float,
    val targetFanSpeed: Int,
    val fanDirection: FanDirectionType = FanDirectionType.FACE_AND_FLOOR,
    val recirculationOn: Boolean? = null,
    val windowLock: Boolean = false,
    val windowsClosed: Boolean = false,
    val isTimedCooldown: Boolean = false,
    val cooldownDurationSeconds: Int = 0,
    val airCleanerEnabled: Boolean,
    val rearSeatVentilation: Boolean = false,
    val turnOffRearReadingLight: Boolean,
    val soundProfile: SoundProfile
) {
    OFF(
        titleKo = "케어 대기 (OFF)",
        subtitleKo = "차량 기본 설정을 유지합니다",
        badgeKo = "STANDBY",
        emoji = "⏹",
        accentHex = "#94A3B8",
        ambientColorRgb = 0xFF37474F.toInt(),
        pleosColorIndex = 0,
        parkedBrightness = 50,
        drivingSafeBrightness = 30,
        targetTempCelsius = 23.0f,
        targetFanSpeed = 2,
        airCleanerEnabled = false,
        turnOffRearReadingLight = false,
        soundProfile = SoundProfile.NONE
    ),
    RELAX(
        titleKo = "릴렉스 안심",
        subtitleKo = "멀미·초기 긴장 완화 | 웜 앰버 · 22.0℃ · 60BPM",
        badgeKo = "RELAX CARE",
        emoji = "🐶",
        accentHex = "#FFA726",
        ambientColorRgb = 0xFFFFB74D.toInt(),
        pleosColorIndex = 12,
        parkedBrightness = 45,
        drivingSafeBrightness = 25,
        targetTempCelsius = 22.0f,
        targetFanSpeed = 2,
        airCleanerEnabled = true,
        turnOffRearReadingLight = true,
        soundProfile = SoundProfile.PINK_NOISE_60BPM
    ),
    DEEP_SLEEP(
        titleKo = "딥 슬립 숙면",
        subtitleKo = "장거리·야간 수면 유도 | 포레스트 그린 · 21.5℃ · 엔진음 차폐",
        badgeKo = "DEEP SLEEP",
        emoji = "🌙",
        accentHex = "#4ADE80",
        ambientColorRgb = 0xFF66BB6A.toInt(),
        pleosColorIndex = 28,
        parkedBrightness = 20,
        drivingSafeBrightness = 15,
        targetTempCelsius = 21.5f,
        targetFanSpeed = 1,
        airCleanerEnabled = true,
        turnOffRearReadingLight = true,
        soundProfile = SoundProfile.BROWN_NOISE_ENGINE_MASK
    ),
    FRESH_AIR(
        titleKo = "상쾌 리프레시",
        subtitleKo = "답답함·체온 상승 케어 | 소프트 스카이 · 20.0℃ · 청정 순환",
        badgeKo = "FRESH AIR",
        emoji = "🌿",
        accentHex = "#38BDF8",
        ambientColorRgb = 0xFF4FC3F7.toInt(),
        pleosColorIndex = 44,
        parkedBrightness = 50,
        drivingSafeBrightness = 30,
        targetTempCelsius = 20.0f,
        targetFanSpeed = 3,
        airCleanerEnabled = true,
        rearSeatVentilation = true,
        turnOffRearReadingLight = true,
        soundProfile = SoundProfile.GENTLE_RAIN_ACOUSTIC
    ),
    TUNNEL_SAFEGUARD(
        titleKo = "터널 세이프가드",
        subtitleKo = "터널 굉음·명암 패닉 방지 | 웜 화이트 · 창문 밀폐 · 노이즈 차폐",
        badgeKo = "TUNNEL SAFE",
        emoji = "🚇",
        accentHex = "#F59E0B",
        ambientColorRgb = 0xFFFFF3E0.toInt(),
        pleosColorIndex = 5,
        parkedBrightness = 40,
        drivingSafeBrightness = 30,
        targetTempCelsius = 22.0f,
        targetFanSpeed = 3,
        recirculationOn = true, // 터널 배기가스 차단 내기 순환
        windowsClosed = true,   // 터널 반사 굉음 차폐를 위한 창문 밀폐
        airCleanerEnabled = true,
        turnOffRearReadingLight = true,
        soundProfile = SoundProfile.NOISE_CANCEL_DRONE
    ),
    NAUSEA_RELIEF(
        titleKo = "멀미 쾌속 환기",
        subtitleKo = "구토 전조 메스꺼움 완화 | 19.5℃ · 하향 송풍 · 100% 외기 순환",
        badgeKo = "NAUSEA RELIEF",
        emoji = "🍃",
        accentHex = "#2DD4BF",
        ambientColorRgb = 0xFF80CBC4.toInt(),
        pleosColorIndex = 32,
        parkedBrightness = 35,
        drivingSafeBrightness = 25,
        targetTempCelsius = 19.5f, // 서늘한 온도로 멀미 체온 상승 억제
        targetFanSpeed = 4,
        fanDirection = FanDirectionType.FLOOR, // 개 얼굴 직바람 방지 (발 밑 하향 송풍)
        recirculationOn = false,              // 차내 정체 냄새 배출을 위한 외기 순환
        airCleanerEnabled = true,
        rearSeatVentilation = true,
        turnOffRearReadingLight = true,
        soundProfile = SoundProfile.COOL_BREEZE_STREAM
    ),
    QUICK_STOP_GUARDIAN(
        titleKo = "5분 휴게소 안심",
        subtitleKo = "5분 정차 분리불안 완화 | 21.0℃ 유지 · 창문 잠금 · 안심 차임벨",
        badgeKo = "STOP GUARDIAN",
        emoji = "⏱️",
        accentHex = "#FBBF24",
        ambientColorRgb = 0xFFFFE082.toInt(),
        pleosColorIndex = 14,
        parkedBrightness = 50,
        drivingSafeBrightness = 30,
        targetTempCelsius = 21.0f,
        targetFanSpeed = 2,
        windowLock = true, // 강아지가 도어 버튼을 밟아 창문이 열리는 사고 방지
        isTimedCooldown = true,
        cooldownDurationSeconds = 300, // 5분(300초) 휴게소 안심 타이머 및 장기 방치 경고
        airCleanerEnabled = true,
        turnOffRearReadingLight = false,
        soundProfile = SoundProfile.GUARDIAN_CHIME_VOICE
    ),
    POST_PLAY_COOLDOWN(
        titleKo = "산책 후 쿨다운",
        subtitleKo = "과열 체온 급속 냉각 & 털 집진 | 20.0℃ 강풍 하향 → 릴렉스 자동전환",
        badgeKo = "COOL DOWN",
        emoji = "🏃",
        accentHex = "#EC4899",
        ambientColorRgb = 0xFFF48FB1.toInt(),
        pleosColorIndex = 52,
        parkedBrightness = 45,
        drivingSafeBrightness = 25,
        targetTempCelsius = 20.0f,
        targetFanSpeed = 5,
        fanDirection = FanDirectionType.FLOOR,
        isTimedCooldown = true,
        cooldownDurationSeconds = 420, // 7분 급속 쿨다운 후 릴렉스 모드로 자동 전환
        airCleanerEnabled = true,
        rearSeatVentilation = true,
        turnOffRearReadingLight = true,
        soundProfile = SoundProfile.COOL_BREEZE_STREAM
    );

    companion object {
        const val MAX_SAFE_DRIVING_BRIGHTNESS = 30
    }
}
