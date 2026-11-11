# Pleos Pet Sanctuary 아키텍처 및 기능 구현 현황 보고서

> **작성 기준 (Fact-Only Verification)**  
> 본 문서는 실제 프로젝트 소스 코드, Gradle 빌드 설정(`app/build.gradle.kts`, `settings.gradle.kts`), 매니페스트(`AndroidManifest.xml`), 캐시된 `ai.pleos.playground:Vehicle:2.3.2` AAR 바이트코드 목록, JUnit 실행 결과 XML, 그리고 `Pleos_Connect_AVD` 에뮬레이터 Logcat 관측 기록만을 근거로 작성되었습니다. 미구현 기능이나 에뮬레이터 환경에서 실패/폴백된 항목은 숨김없이 사실 그대로 구분해 기술합니다.

---

## 1. 기술 스택 및 SDK 연동 근거

### 1.1 빌드 및 플랫폼 구성
* **근거 파일**: [`settings.gradle.kts`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/settings.gradle.kts), [`build.gradle.kts`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/build.gradle.kts), [`app/build.gradle.kts`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/build.gradle.kts)
* **SDK 저장소**: `https://nexus-playground.pleos.ai/repository/maven-releases/`
* **핵심 차량 SDK 의존성 (현재 1종만 탑재)**:
  * `ai.pleos.playground:Vehicle:2.3.2` (`app/build.gradle.kts`)
  * **팩트 체크**: `NaviHelper`, `TTS`, `STT`, `FusedLocation` 등 다른 Pleos SDK 모듈이나 Google Maps/Location 라이브러리는 현재 `app/build.gradle.kts`에 의존성으로 포함되어 있지 않습니다.
* **Android / Kotlin 환경**:
  * `applicationId`: `ai.pleos.petsanctuary`
  * `minSdk = 28`, `compileSdk = 34`, `targetSdk = 34`
  * Android Gradle Plugin `8.13.1`, Kotlin `2.2.21`, Java `17`, `kotlinx-coroutines` `1.9.0`

### 1.2 Android Automotive OS (AAOS) 및 Pleos 권한 선언
* **근거 파일**: [`AndroidManifest.xml`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/AndroidManifest.xml)
* **선언된 Pleos 서비스/제어 권한**:
  * 바인딩: `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`
  * 조명: `pleos.car.permission.CAR_LIGHTS`, `pleos.car.permission.CONTROL_CAR_LIGHTS`
  * 공조: `pleos.car.permission.CAR_CLIMATE`, `pleos.car.permission.CONTROL_CAR_CLIMATE`
  * 기어/주행: `pleos.car.permission.CAR_DRIVE`, `pleos.car.permission.CONTROL_CAR_DRIVE`
  * 창문: `pleos.car.permission.CAR_WINDOWS`, `pleos.car.permission.CONTROL_CAR_WINDOWS`
* **시스템 속성**:
  * `android.hardware.type.automotive` (`android:required="false"`로 일반 AVD 설치도 허용)
  * `MainActivity`: `distractionOptimized = "true"`, `launchMode = "singleTop"`, `screenOrientation = "landscape"`
  * `PetSanctuaryService`: `foregroundServiceType = "mediaPlayback"`, `android:exported = "false"`

---

## 2. 전체 계층형 아키텍처 및 모듈화 설계

앱은 차량 하드웨어 SDK나 Android OS 프레임워크에 도메인 로직이 종속되지 않도록 **의존성 역전(Interface Abstraction)** 기반의 5개 패키지로 모듈화되어 있습니다.

```mermaid
flowchart TB
    UI["UI Layer: ui/MainActivity.kt<br/>(StateFlow 구독 · 반응형 콕핏 · 엔지니어링 콘솔)"]
    SVC["Service Layer: service/PetSanctuaryService.kt<br/>(Foreground Service · 알림 · 내비 터널 BroadcastReceiver)"]
    DOM["Domain Layer: domain/PetSanctuaryController.kt<br/>(8종 프리셋 상태 머신 · 스냅샷 복원 · 안전 디밍 · 쿨다운 타이머)"]
    MDL["Model Layer: model/PetPresetMode.kt, PetSanctuaryState.kt<br/>(불변 상태 모델 · 프리셋 정의 · Capability 명세)"]

    HAL_IF["HAL Interface: hal/PetVehicleHal.kt"]
    AUD_IF["Audio Interface: audio/PetAudioController"]

    HAL_SDK["hal/PleosSdkVehicleHal.kt<br/>(Pleos Vehicle 2.3.2 API 호출 + Dual-Write 폴백)"]
    HAL_SIM["hal/SimulatedVehicleHal.kt<br/>(인메모리 가상 VHAL · JUnit/콘솔 시뮬레이터)"]

    AUD_AND["audio/AndroidPetSoundEngine<br/>(AudioTrack PCM 실시간 파형 합성 + AudioFocus)"]
    AUD_SIM["audio/SimulatedPetAudioController<br/>(JUnit 테스트용 가상 오디오 컨트롤러)"]

    PLEOS[("Pleos Vehicle Service<br/>(Light · HVAC · Window · Gear)")]

    UI -->|LocalBinder 바인딩 / StateFlow collect| SVC
    SVC -->|생성 및 조율| DOM
    DOM --> MDL
    DOM --> HAL_IF
    DOM --> AUD_IF

    HAL_IF <|.. HAL_SDK
    HAL_IF <|.. HAL_SIM
    HAL_SDK -->|내부 fallbackSimulator 보유| HAL_SIM
    HAL_SDK -->|Vehicle 2.3.2 SDK| PLEOS

    AUD_IF <|.. AUD_AND
    AUD_IF <|.. AUD_SIM
```

### 2.1 패키지 및 파일별 책임

| 패키지 / 파일 | 핵심 심볼 | 역할 및 모듈화 근거 |
|---|---|---|
| `model/` | [`PetPresetMode.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/model/PetPresetMode.kt), [`PetSanctuaryState.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/model/PetSanctuaryState.kt) | `PetPresetMode` (8종 모드), `SoundProfile` (7종), `VehicleGearState`, `VehicleCapabilities`, `VehicleBaselineSnapshot`, `PetSanctuaryState` 정의. 순수 데이터 모델 계층. |
| `hal/` | [`PetVehicleHal.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/hal/PetVehicleHal.kt), [`PleosSdkVehicleHal.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/hal/PleosSdkVehicleHal.kt), [`SimulatedVehicleHal.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/hal/SimulatedVehicleHal.kt) | 차량 하드웨어 제어 추상화. `PleosSdkVehicleHal`은 실제 SDK(`Light`, `HVAC`, `Gear`, `Window`)를 호출함과 동시에 내부 `fallbackSimulator`에도 상태 기록(Dual-Write). |
| `audio/` | [`PetSoundEngine.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/audio/PetSoundEngine.kt) | `PetAudioController` 인터페이스 아래, 실기용 `AndroidPetSoundEngine`(`AudioTrack` PCM 합성 + `AudioFocusRequest`)와 테스트용 `SimulatedPetAudioController` 분리. |
| `domain/` | [`PetSanctuaryController.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/domain/PetSanctuaryController.kt) | 핵심 비즈니스 로직 및 상태 머신(`StateFlow<PetSanctuaryState>`). 프리셋 적용, 범위 클램핑, 스냅샷 저장/복원, 기어별 밝기 디밍 및 수동 조작 차단, 오디오 덕킹, 쿨다운 코루틴 타이머 담당. |
| `service/` | [`PetSanctuaryService.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/service/PetSanctuaryService.kt) | 백그라운드 포그라운드 서비스 유지, 상단 알림 갱신, 내비게이션 터널 인텐트 수신용 `BroadcastReceiver`(`naviTunnelReceiver`) 호스팅. |
| `ui/` | [`MainActivity.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/ui/MainActivity.kt) | 순수 Kotlin 코드 기반 동적 뷰 빌드(`buildLuxuryCockpitView`), 상태 렌더링(`renderState`), 엔지니어링 시뮬레이션 콘솔, 인텐트 테스트 훅 제공. |
| `test/` | [`PetSanctuaryControllerTest.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/test/java/ai/pleos/petsanctuary/PetSanctuaryControllerTest.kt) | JVM JUnit 4 단위 테스트 10종 (`SimulatedVehicleHal` + `SimulatedPetAudioController` 주입). |

---

## 3. Pleos Vehicle SDK (`2.3.2`) API 매핑 및 실사용 현황

캐시된 `Vehicle-2.3.2.aar`(`classes.jar`) 내부에 포함된 전체 API 클래스 중 현재 앱이 연동한 범위는 다음과 같습니다.

| SDK 도메인 (`Vehicle.*`) | 현재 앱 연동 메서드 (`PleosSdkVehicleHal`) | 에뮬레이터(`Pleos_Connect_AVD`) Logcat 검증 결과 |
|---|---|---|
| **`Vehicle.getLight()`** | `checkAmbientLightBrightnessCapabilities`<br>`checkAmbientLightBrightnessColorCapabilities`<br>`checkAmbientLightSwitchCapabilities`<br>`checkReadingLightSwitchCapabilities`<br>`setAmbientLightSwitch` / `setAmbientLightBrightnessColor`<br>`setAmbientLightBrightness` / `setReadingLightSwitch`<br>`setAmbientColorAutoByDriveModeEnabled` | • `setAmbientLightSwitch(ON) OK`<br>• `setAmbientLightBrightnessColor(52) OK`<br>• `setAmbientLightBrightness(45) OK`<br>• `setReadingLightSwitch(OFF) OK`<br>• ⚠️ `setAmbientColorAutoByDriveModeEnabled`: `IllegalArgumentException` (`0x21200787` 미지원) |
| **`Vehicle.getHVAC()`** | `checkHvacPowerCapabilities` / `Ac` / `FanSpeed` / `Temperature` / `AirCleaner` / `Recirculation` / `FanDirection`<br>`getHvacTemperatureCurrent` / `registerHvacTemperatureCurrent`<br>`setHvacPower` / `setHvacAc` / `setHvacTemperature`<br>`setHvacFanSpeed` / `setHvacFanDirection` / `setHvacAirCleaner` / `setHvacRecirculation` | • `setHvacPower(true) OK`<br>• `setHvacAc(true) OK`<br>• `setHvacTemperature(20.0 C) OK`<br>• `setHvacAirCleaner(true) OK`<br>• ⚠️ `setHvacFanSpeed` (`areaId: 5`): `PropertyAsyncError{error code: 2}`<br>• ⚠️ `setHvacFanDirection` (`areaId: 5`): `PropertyAsyncError{error code: 2}` |
| **`Vehicle.getWindow()`** | `checkWindowLockCapabilities` / `checkWindowPositionCapabilities`<br>`setWindowLock` / `setWindowPosition` | • `setWindowLock(true) OK` & `(false) OK` (차량 클러스터에 `"창문 잠금"` / `"창문 잠금 해제"` UI 팝업 실제 표시됨) |
| **`Vehicle.getGear()`** | `getCurrentGearSelectionState`<br>`registerGearSelection` / `unregisterGearSelection` | • 컴파일 시 deprecated 경고 발생 (Google `GEAR_SELECTION` 사용 권고). 에뮬레이터 D단 UI 검증은 엔지니어링 시뮬레이터 인텐트(`EXTRA_SIMULATE_GEAR`)로 확인됨. |
| **미사용 SDK 도메인** | `Seat`, `Door`, `EvBattery`, `Safety`, `DrivingMode`, `CarInfo`, `Display`, `Brake`, `Fuel`, `Odometer`, `SideMirror`, `SteeringWheel`, `Tire`, `Toll`, `TurnSignal`, `Wiper` | **현재 프로젝트에서 호출하지 않음** (추후 고도화 확장 후보). |

---

## 4. 기능별 구현 상태 Fact Check (완료 vs 제한적/시뮬레이션 vs 미구현)

### 4.1 완전히 구현되어 코드 및 실행으로 확인된 기능 (Implemented)

1. **8종 반려동물 감각 케어 프리셋 상태 머신** (`PetPresetMode`, `PetSanctuaryController`)
   * `OFF`, `RELAX`(22.0℃/2단/45%), `DEEP_SLEEP`(21.5℃/1단/20%), `FRESH_AIR`(20.0℃/3단/50%), `TUNNEL_SAFEGUARD`(22.0℃/3단/내기순환/창문닫기), `NAUSEA_RELIEF`(19.5℃/4단/하향송풍/외기순환), `QUICK_STOP_GUARDIAN`(21.0℃/2단/창문잠금/가디언배너), `POST_PLAY_COOLDOWN`(20.0℃/5단/하향송풍/420초 타이머).
   * 도메인 코어 로직은 [`PetSanctuaryControllerTest.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/test/java/ai/pleos/petsanctuary/PetSanctuaryControllerTest.kt)의 JUnit 테스트 **10건 전원 통과**(`tests="10" skipped="0" failures="0" errors="0"`)로 검증됨.
2. **하드웨어 Capability 동적 조회 및 범위 클램핑 / 하위 트림 폴백**
   * `PleosSdkVehicleHal.queryVehicleCapabilities()`가 VHAL 지원 Zone(`SeatArea`, `WindowArea`)과 최소·최대 범위를 조회하고, `PetSanctuaryController.activatePreset()`이 `coerceIn`으로 클램핑 및 미지원 기능 폴백 로그 생성.
3. **기어 상태 기반 안전 디밍 및 수동 미세조절 차단**
   * `VehicleGearState.isDriving == true` (`PARK` 외 R/N/D)일 때 조명 밝기를 `min(parkedBrightness, drivingSafeBrightness).coerceAtMost(30)`으로 제한하고 `adjustTargetTemperature()`, `adjustAmbientBrightness()`를 `false` 반환으로 차단.
4. **7분(`420초`) 쿨다운 코루틴 카운트다운**
   * `POST_PLAY_COOLDOWN` 선택 시 백그라운드 코루틴이 1초 단위로 `cooldownSecondsRemaining`을 감소시키며 UI에 `COOLING MM:SS` 배지를 실시간 갱신함(에뮬레이터 Logcat에서 1초 간격 UI 리렌더링 관측됨).
5. **실시간 PCM 사운드 합성 및 AudioFocus 덕킹 구조**
   * [`AndroidPetSoundEngine`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/audio/PetSoundEngine.kt)가 `AudioTrack` 스트림 모드로 6종 사운드 파형을 생성하며, `AUDIOFOCUS_LOSS_TRANSIENT_MAY_DUCK`(0.2f), `AUDIOFOCUS_GAIN`(1.0f), `AUDIOFOCUS_LOSS`(0.0f) 전환 로직 구현 완료.
6. **Foreground Service 및 내비 터널 이벤트 BroadcastReceiver 골격**
   * [`PetSanctuaryService`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/service/PetSanctuaryService.kt)가 포그라운드 알림을 유지하고 `ai.pleos.petsanctuary.TUNNEL_APPROACHING` / `TUNNEL_EXITED` 인텐트 리시버를 등록함.

---

### 4.2 구현되었으나 제약·낙관적 처리·차이가 존재하는 기능 (Caveats & Partial Gaps)

> [!WARNING] 심사 및 재검토 시 반드시 인지해야 할 기술적 사실
> 아래 항목들은 기능이 "코드상으로는 호출되지만" 실제 차량 환경/에뮬레이터에서는 **시뮬레이터 폴백으로 처리되거나, UI 설명과 실제 동작 사이에 차이가 있는 부분**입니다.

1. **에뮬레이터 VHAL 일부 속성의 실제 하드웨어 미반영 & 낙관적 UI (Optimistic Update)**
   * **현상 근거**: 에뮬레이터 Logcat에서 `HVAC_FAN_SPEED`(`areaId: 5`), `HVAC_FAN_DIRECTION`(`areaId: 5`)는 `PropertyAsyncError(error code: 2)`로 실패했고, `setAmbientColorAutoByDriveModeEnabled`는 `IllegalArgumentException(property ID 0x21200787 not in carPropertyConfig)` 예외가 발생했습니다.
   * **구조적 원인**: `PleosSdkVehicleHal`이 VHAL 호출 전 `fallbackSimulator`를 먼저 업데이트하고, `PetSanctuaryController`는 VHAL 비동기 콜백(`onComplete`/`onFailed`) 결과를 기다리지 않고 `_state`를 즉시 갱신합니다. 그 결과 **실제 에뮬레이터 VHAL이 거부한 풍량(예: 5단)·풍향(하향)도 앱 화면 텔레메트리에는 적용된 것처럼 표시**됩니다.
2. **스냅샷 복원(`OFF`) 시 초기 차량 값 VHAL 역조회 미구현**
   * `PetSanctuaryController.activatePreset()`이 `VehicleBaselineSnapshot`을 생성할 때, 현재 차량의 실제 에어컨 설정·앰비언트 색상·밝기·윈도우 락 상태를 VHAL getter로 읽어오는 대신 **`PetSanctuaryState`의 초기 기본값(`24.0℃`, `풍량 2단`, `밝기 65%`, `#90CAF9`, `WindowLock = false`)을 기준으로 저장**합니다. (`initialize()`가 VHAL에서 직접 조회·구독하는 값은 `Gear`와 `CabinTemp` 2종뿐입니다.)
   * 또한 `TUNNEL_SAFEGUARD`에서 `setWindowsClosed(true)`(위치 0)로 창문을 닫은 뒤 `OFF`로 종료할 때, 창문을 이전 위치로 다시 여는 복원은 수행하지 않으며 `isWindowsClosed = false` 플래그만 초기화합니다.
3. **터널 내비게이션 자동 연동의 실제 동작 범위**
   * 순정 내비게이션이 터널 300m 전 이벤트를 자동으로 쏴주는 연동은 구현되어 있지 않습니다(Pleos `Vehicle:2.3.2`에 내비 도로 이벤트 API 부재). 현재는 `PetSanctuaryService`의 `naviTunnelReceiver` 브로드캐스트 수신 골격과 `MainActivity` 엔지니어링 콘솔의 수동 모의 버튼(`🚇 내비 연동: 터널 300m 전 감지`)으로 대체되어 있습니다. (단, 엔지니어링 콘솔 버튼은 `sendBroadcast`를 거치지 않고 직접 `activateModeAndNotify(TUNNEL_SAFEGUARD)`를 호출합니다.)
4. **사운드 엔진의 "보이스" 및 "노이즈 캔슬링" 구현 수준**
   * `SoundProfile.GUARDIAN_CHIME_VOICE`의 UI 설명에는 `"부드러운 차임벨 및 안심 음성 루프"`라고 명시되어 있으나, [`PetSoundEngine.kt`](file:///C:/Users/wonky/AndroidStudioProjects/PleosPetSanctuary/app/src/main/java/ai/pleos/petsanctuary/audio/PetSoundEngine.kt) 실제 코드는 **520Hz/260Hz 감쇠 차임벨 사인파 + 브라운 노이즈 합성만 출력하며, 사람 목소리 녹음 파일이나 TTS 음성은 재생하지 않습니다.**
   * `NOISE_CANCEL_DRONE` 역시 마이크 입력을 역위상으로 상쇄하는 하드웨어 ANC가 아닌 **110Hz/55Hz 저주파 드론 + 브라운 노이즈를 통한 음향 마스킹** 방식입니다.
5. **표준 Android `CarUxRestrictionsManager` 미사용**
   * Android Automotive의 `CarUxRestrictionsManager` 시스템을 직접 구독하지 않고, `Pleos Vehicle SDK`의 기어 상태(`VehicleGearState.isDriving`)만으로 자체 규제(`isDrivingRestricted`)를 판단합니다. UI 수동 조절 버튼(-/+)은 `alpha = 0.35f`로 흐리게 처리되지만 View의 `isEnabled`가 `false`가 되는 것은 아니며, 클릭 시 컨트롤러 메서드 내부에서 `return false`로 차단합니다.
6. **최신 UI 변경사항 및 세부 엣지 케이스 미검증 영역**
   * 반응형 분기(`isCompactScreen = screenWidthDp < 900`)와 엔지니어링 콘솔 내비 버튼 추가 직후 `assembleDebug` 및 단위 테스트 10종 통과는 확인했으나, **수정 이후 에뮬레이터 화면 캡처 및 `Medium Phone` 2열 레이아웃 실기 스크린샷 확인은 아직 수행하지 않았습니다.**
   * `TUNNEL_SAFEGUARD`에서 `isWindowsClosed = true` 상태가 되지만, `MainActivity` 텔레메트리 카드에는 `WINDOW LOCK` 배지만 존재하고 **창문 밀폐(`CLOSED`) 여부를 표시하는 별도 UI 배지는 없습니다.**
   * `POST_PLAY_COOLDOWN` 타이머의 420초 완료 시 `RELAX` 자동 전환 분기는 JUnit에서 시간 진행(`TestCoroutineScheduler` 등) 테스트로 검증되지 않았습니다.
   * `PleosSdkVehicleHal.queryVehicleCapabilities()`에서 여러 비동기 capability 콜백이 각각 `fallbackSimulator.getCapabilities().copy(...)`를 덮어쓰므로 동시 콜백 도착 시 일부 필드가 덮어씌워질 수 있는 경합 여지가 있습니다.

---

### 4.3 현재 미구현 및 추후 구현 예정 항목과 확장 구조 (Roadmap)

현재 아키텍처는 인터페이스로 계층이 분리되어 있으므로, 추후 아래 기능들을 기존 도메인/UI 구조를 크게 뜯어고치지 않고 플러그인 방식으로 모듈 확장이 가능합니다.

| 우선순위 | 추후 구현 예정 기능 | 현재 미구현 근거 | 모듈화 확장 방안 (연계 지점) |
|---|---|---|---|
| **1** | **실시간 터널/목적지 자동 감지 (`NaviTunnelDetector`)** | `build.gradle.kts`에 `NaviHelper` 또는 `Location` SDK 부재 | GPS `FusedLocation` + 전국 터널 GeoFence DB 또는 `Pleos NaviHelper SDK`를 독립 Detector 모듈로 추가 → 감지 시 `ACTION_TUNNEL_APPROACHING` 브로드캐스트 발행하여 기존 `PetSanctuaryService.naviTunnelReceiver`와 연결 |
| **2** | **실제 보호자 안심 보이스 / TTS 출력** | 현재 `AndroidPetSoundEngine`은 `AudioTrack` 사인파 합성만 구현 | `PetAudioController` 인터페이스 구현체 확장 (기존 `PetSanctuaryController` 코드 무변경). `Pleos TTS SDK` 또는 `MediaPlayer`로 보호자 사전 녹음 음성/TTS 안내를 30초~1분 간격 믹싱 |
| **3** | **차량 VHAL 초기 상태 동기화 & 확정 상태 반영** | `initialize()`에서 `Gear`와 `CabinTemp`만 조회 중; `copy()` 경합 존재 | `PleosSdkVehicleHal`에 VHAL Getter(`getHvacTemperatureSetting`, `getAmbientLightBrightness` 등) 초기 동기화 및 원자적(atomic) Capability 병합 추가, 실패 콜백(`PropertyAsyncError`) UI 토스트/뱃지 피드백 연동 |
| **4** | **2열 시트 통풍 & EV 배터리 임계치 보호** | `Vehicle-2.3.2.aar` 내 `Seat`, `EvBattery`, `Door` 클래스가 존재하나 미사용 | `PetVehicleHal`에 `setRearSeatVentilation()`, `getEvBatteryPercent()` 추가 → `POST_PLAY_COOLDOWN` 시 시트 통풍 연동, `QUICK_STOP_GUARDIAN` 시 배터리 보호 차단 로직 연계 |
| **5** | **견종·체급·연령별 맞춤 프리셋 커스터마이징** | 현재 `PetPresetMode` enum 상수에 온도/풍속이 고정됨 | `DataStore` 기반 `PetProfileRepository` 모듈 추가 → `PetSanctuaryController.activatePreset()`에서 기본값 대신 반려견 프로필 오프셋(예: 단두종 온도 `-1.0℃`, 노령견 최대 풍속 제한) 적용 |
| **6** | **창문 밀폐 상태 UI 배지 & Google `GEAR_SELECTION` 마이그레이션** | `MainActivity`에 `isWindowsClosed` 뱃지 부재; `Gear` API deprecated 경고 | `MainActivity` 텔레메트리 카드에 `WINDOWS CLOSED` 배지 추가, `PleosSdkVehicleHal`의 deprecated `Gear` 리스너를 표준 `GEAR_SELECTION` 프로퍼티 구독으로 교체 |

---

## 5. 검증 증적 요약 (최종 코드 기준)

* **소스 동기화 확인**: `C:\Users\wonky\.gemini\antigravity\scratch\pleos-pet-sanctuary\app\src`와 `C:\Users\wonky\AndroidStudioProjects\PleosPetSanctuary\app\src` 간 전체 파일 해시 일치 확인 (`Compare-Object` 차이 없음).
* **빌드 및 단위 테스트 결과** (`gradlew.bat compileDebugKotlin testDebugUnitTest assembleDebug`):
  * `BUILD SUCCESSFUL in 6s`
  * `PetSanctuaryControllerTest`: **10 tests, 0 failures, 0 errors, 0 skipped** (`TEST-ai.pleos.petsanctuary.PetSanctuaryControllerTest.xml` 기준).
* **미검증 사항(사실 기반 명시)**:
  * 직전 에뮬레이터 캡처(`shot_tunnel.png`, `shot_nausea.png`, `shot_guardian.png`, `shot_cooldown.png`, `shot_driving_d.png`, `shot_off.png`)는 반응형 UI 분기 및 `naviTunnelReceiver` 추가 전에 촬영된 것이며, 최종 코드 반영 후 에뮬레이터 재설치 및 화면 육안/스크린샷 재검증은 아직 진행되지 않았습니다.
