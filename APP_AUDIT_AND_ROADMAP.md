# Pleos Pet Sanctuary 전체 점검 보고서 & 조치 완료 내역

> **최종 업데이트**: 2026-11-11  
> **기준 코드**: `C:\Users\wonky\AndroidStudioProjects\PleosPetSanctuary` (`scratch/pleos-pet-sanctuary`와 동기화 완료)  
> **작성 원칙**: 소스 코드, Gradle 빌드 로그, JUnit 14개 테스트 결과 XML, SDK AAR(`Vehicle-2.3.2.aar`) 바이트코드, 에뮬레이터 Logcat 및 공모전 공식 웹페이지 3곳(`submission`, `development`, `judging-awards`) 원문 등 **직접 확인한 근거만** 기재합니다.

---

## 0. 사용자 결정 사항(1~4번) 조치 결과 요약

| 결정 항목 | 요청 내용 | 조치 결과 및 검증 근거 |
| :--- | :--- | :--- |
| **1번 (P0 7개 항목 전체 진행)** | 창문 잠금 해제 버그, OFF 복원 스냅샷, VHAL 실패 UI 표시, 디버그/릴리즈 분리, 브로드캐스트 보안, 과장 문구 정정 | **코드 반영 및 JUnit 14건 통과 완료** (`PetSanctuaryController.kt`, `PleosSdkVehicleHal.kt`, `PetSanctuaryService.kt`, `MainActivity.kt`, `PetPresetMode.kt`) |
| **2번 (5분 휴게소 안심 실제 타이머)** | `QUICK_STOP_GUARDIAN` 모드에 실제 5분(300초) 카운트다운 타이머 및 만료 경고 구현 | **구현 및 가상시간 단위테스트 검증 완료** (`cooldownDurationSeconds = 300`, 실시간 `05:00` 배너/카드 카운트다운 + 만료 시 즉시 복귀 경고 표시 및 공조·창문잠금 유지) |
| **3번 (터널 모드 창문 닫힘 확인 팝업)** | `TUNNEL_SAFEGUARD` 실행 시 창문 자동 닫힘 전 안전 확인 팝업 추가 | **구현 및 단위테스트 검증 완료** (`MainActivity.showTunnelWindowConfirmationDialog()`: `[창문 닫고 가동]` / `[창문 유지하고 가동]` / `[취소]` 선택 팝업 및 `closeWindowsOverride` 파라미터 적용) |
| **4번 (공모전 규정 3곳 & 접수양식 대조)** | `submission`, `development`, `judging-awards` 원문 및 첨부 DOCX 양식 검토·반영 | **공식 페이지 3곳 전문 추출·대조 완료**, 제출 규격에 맞춘 [`README.md`](./README.md) 및 실행 가능한 서명된 Release APK([`apk/팀명_PleosPetSanctuary_release.apk`](./apk/팀명_PleosPetSanctuary_release.apk), 9.12MB) 생성 완료 |

---

## 1. 공모전 공식 가이드 3곳 & 접수양식(DOCX) 원문 대조 결과

직접 추출하여 확인한 공식 페이지 원문(`guide/submission`, `guide/development`, `guide/judging-awards`)과 첨부해주신 `contest-application-consent.docx` 양식 기준 대조표입니다.

### 1.1 개발 환경 규정 (`https://appdevchallenge.auto-hmg.io/guide/development`)
| 공식 요구사항 | 현재 프로젝트 상태 | 판정 |
| :--- | :--- | :--- |
| **Android API Level 28 이상** | `minSdk = 28`, `compileSdk = 34`, `targetSdk = 34` ([`app/build.gradle.kts`](./app/build.gradle.kts)) | ✅ 충족 |
| **Gradle 8.0 이상** | Gradle `8.11.1`, Android Gradle Plugin `8.5.2` | ✅ 충족 |
| **Java 또는 Kotlin** | Kotlin `1.9.24` (`jvmTarget = "17"`) | ✅ 충족 |
| **제공 SDK 활용** (`Vehicle SDK`, `FusedLocation`, `NaviHelper`, `TTS`, `STT`) | 공식 `ai.pleos.playground:Vehicle:2.3.2`(`Light`, `HVAC`, `Window`, `Gear`) 연동 완료 | ✅ 충족 |

### 1.2 지원·제출 규정 (`https://appdevchallenge.auto-hmg.io/guide/submission`)
| 공식 제출 규정 | 프로젝트 준비 상태 및 주의사항 | 판정 |
| :--- | :--- | :--- |
| **접수 마감 및 이메일** | `2026.11.15 23:59`까지 `appdevchallenge@hyundai-partners.com` 제출 | 📌 일정 확인 |
| **이메일 제목 양식** | `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명` (수정 제출 시 앞에 `[수정제출]` 추가) | 📌 제출 시 준수 |
| **필수 제출물: 접수양식(기획서) DOCX** | `01 참가신청서` + `02 앱 프로젝트 소개/심사자료` + `03 개인정보 동의서` + `04 서명`. **주의: `02 앱 프로젝트 소개` 본문에는 성명·이메일·전화번호·소속 등 개인 식별 정보를 절대 기재하면 안 됨(블라인드 심사 규정)** | ⚠️ 작성 시 필수 유의 |
| **선택 제출물(가산점): GitHub 저장소 & APK** | 저장소 내 `apk/` 폴더에 **`팀명_앱이름_release.apk`** 형식으로 **실행 가능한 release 버전** 업로드 → [`apk/팀명_PleosPetSanctuary_release.apk`](./apk/팀명_PleosPetSanctuary_release.apk) (9.12MB) 생성 완료 | ✅ 준비 완료 |
| **GitHub `README.md` 필수 기재 4항목** | ① 앱 이름 및 한 줄 소개, ② 실행 방법, ③ 테스트 환경 및 필요한 설정, ④ APK 파일 위치 → 루트 [`README.md`](./README.md)에 4개 섹션 모두 작성 완료 | ✅ 준비 완료 |

### 1.3 심사 기준 (`https://appdevchallenge.auto-hmg.io/guide/judging-awards`)
1. **차량특화성 (필수)**: 모바일 앱으로는 불가능한 차량 고유 하드웨어(실내 무드램프 조도·색상, 공조 온도·풍향·내외기·공기청정, 파워윈도우 잠금·개폐, 기어 `P/D` 연동 주행 안전 디밍)와 차량 스피커 음향 테라피의 유기적 결합.
2. **창의성 (필수)**: 반려견 동승 시 운전자와 반려견이 겪는 핵심 Pain Point 4가지(주행 멀미, 터널 진입 시 반사 굉음·배기가스 패닉, 산책 후 체온 과열, 휴게소 정차 시 도어 창문 오작동 사고)를 8가지 원터치 모드로 해결.
3. **기술 활용성 (필수)**: `ai.pleos.playground:Vehicle:2.3.2`의 동적 Capability 탐색(`check*Capabilities`), 기어/온도 실시간 리스너, AAOS `CarUxRestrictions` 안전 잠금, `AudioFocus` 내비게이션 음성 20% 자동 감쇠(Ducking) 활용.
4. **사업성 (필수)**: 접수양식(DOCX) `02 앱 프로젝트 소개` 작성 시 **타깃 고객 분석(반려동물 동반 운전자), 예상 MAU, Retention Rate, 향후 서비스 확대 계획(NaviHelper 지오펜싱 연동, 수의학 사운드 구독 등)** 을 수치와 함께 명시해야 함.

---

## 2. P0 및 결정 사항 상세 코드 수정 내역

### ✅ P0-1. 창문 잠금(Window Lock) 강제 해제 버그 수정
- **원인**: 기존에는 모든 모드 활성화 시 `vehicleHal.setWindowLock(mode.windowLock)`을 호출하여, `QUICK_STOP_GUARDIAN` 외의 모드(`mode.windowLock == false`)를 켜면 운전자가 잠가둔 창문 잠금이 강제로 풀렸음.
- **수정 ([`PetSanctuaryController.kt`](./app/src/main/java/ai/pleos/petsanctuary/domain/PetSanctuaryController.kt))**:
  - `mode.windowLock == true`(`5분 휴게소 안심`)일 때만 `setWindowLock(true)`를 호출하고 `didModifyWindowLock = true`를 기록합니다.
  - 일반 모드(`RELAX`, `DEEP_SLEEP` 등)로 진입할 때는 운전자의 기존 창문 잠금 상태를 건드리지 않고 그대로 보존하며, `QUICK_STOP_GUARDIAN`에서 다른 모드로 나가거나 `OFF`할 때만 진입 전 창문 잠금 상태(`previousWindowLockOn`)로 복원합니다.
  - **검증**: JUnit `windowLockPreservation_doesNotUnlockExistingDriverWindowLockInNonGuardianModes` 통과.

### ✅ P0-2. 진입 전 차량 상태(Baseline Snapshot) VHAL Getter 연동 및 색상 인덱스 복원
- **원인**: 초기 상태가 앱 하드코딩 기본값(`24.0℃`, 밝기 `65%`)으로만 되어 있었고, `OFF` 복원 시 앰비언트 색상 인덱스를 무조건 `1`로 호출했음.
- **수정 ([`PleosSdkVehicleHal.kt`](./app/src/main/java/ai/pleos/petsanctuary/hal/PleosSdkVehicleHal.kt), [`PetSanctuaryState.kt`](./app/src/main/java/ai/pleos/petsanctuary/model/PetSanctuaryState.kt))**:
  - 초기화 시 `getHvacTemperature`, `getHvacAcState`, `getHvacRecirculationState`, `getAmbientLightBrightness`, `getAmbientLightBrightnessColor`, `getAmbientLightSwitch`, `getCurrentWindowLockState`를 호출해 실제 차량 초기값을 읽어옵니다.
  - `VehicleBaselineSnapshot`에 `previousPleosColorIndex`를 저장하여 `OFF` 시 원래 색상 인덱스로 정확히 복원합니다.
  - `checkAmbientColorAutoByDriveModeEnabledCapabilities`의 `AccessType`을 먼저 확인하여 미지원 차량/에뮬레이터에서 `0x21200787` 예외가 발생하지 않도록 방지했습니다.

### ✅ P0-3. VHAL 하드웨어 제어 실패(PropertyAsyncError)의 투명한 상태/UI 반영
- **원인**: `Pleos_Connect_AVD`에서 `setHvacFanSpeed`, `setHvacFanDirection`이 `PropertyAsyncError(error code: 2)`를 반환해도 UI에는 아무 표시 없이 100% 하드웨어 제어에 성공한 것처럼 표시되었음.
- **수정 ([`PleosSdkVehicleHal.kt`](./app/src/main/java/ai/pleos/petsanctuary/hal/PleosSdkVehicleHal.kt), [`PetSanctuaryController.kt`](./app/src/main/java/ai/pleos/petsanctuary/domain/PetSanctuaryController.kt), [`MainActivity.kt`](./app/src/main/java/ai/pleos/petsanctuary/ui/MainActivity.kt))**:
  - 각 VHAL setter의 `onFailed` 콜백을 `PetSanctuaryController.recordVhalFallback()`으로 연결하여 `state.vhalFallbackProperties`에 실패한 하드웨어 속성명(예: `풍량단수`, `풍향제어`)을 기록합니다.
  - 하단 공조 텔레메트리 카드(`tempDetailText`)에 **`· [VHAL폴백: 풍량단수/풍향제어]`** 가 실시간으로 표시되어 하드웨어 실제 반영 항목과 시뮬레이터 폴백 항목을 정직하게 구분합니다.

### ✅ P0-4 & P0-5. 디버그 인텐트 분리 및 BroadcastReceiver 보안 강화
- **`MainActivity.kt`**: 외부 인텐트를 통한 가짜 기어 주입(`EXTRA_SIMULATE_GEAR`) 및 오디오 덕킹 강제 트리거(`EXTRA_SIMULATE_NAVI_DUCK`)가 `BuildConfig.DEBUG == true`일 때만 동작하도록 격리했습니다.
- **`PetSanctuaryService.kt`**: `naviTunnelReceiver` 등록 시 `Context.RECEIVER_NOT_EXPORTED`를 적용하여 외부 무권한 앱이 임의로 주행 중 창문 제어 브로드캐스트를 주입하지 못하도록 차단했습니다.

### ✅ P0-6. 과장/허위 문구 정정
- **`PetPresetMode.kt`**:
  - `"휴게소 안심 차임벨 & 보이스"` → **`"휴게소 안심 차임벨 & 브라운노이즈"`** (실제 3초 주기 사인파 차임벨 + 브라운노이즈 합성에 부합하도록 정정)
  - `"터널 소음 차폐 안티-드론"` → **`"터널 저주파 차폐 사운드"`** (`"터널 진입 시 급격한 저주파 공명음을 완화하는 110Hz·55Hz 차폐음"`)

### ✅ 결정 #2. 5분 휴게소 안심(`QUICK_STOP_GUARDIAN`) 실제 5분(300초) 타이머 구현
- **`PetPresetMode.kt` / `PetSanctuaryController.kt` / `MainActivity.kt`**:
  - `QUICK_STOP_GUARDIAN`에 `isTimedCooldown = true, cooldownDurationSeconds = 300`을 설정했습니다.
  - 활성화 시 상단 가디언 배너(`5분 휴게소 안심 가디언 작동 중 (남은 시간 04:59)`)와 카드 배지(`GUARDIAN 04:59`)에서 실시간 카운트다운이 동작합니다.
  - 300초(5분) 경과 시 공조(21.0℃)와 창문 잠금은 반려견 보호를 위해 유지하되, 배너와 상태바가 **`"⚠️ 5분 휴게소 안심 시간 경과! 즉시 차량으로 복귀해 주세요"`** 경고 상태로 전환됩니다.
  - **검증**: JUnit `quickStopGuardian_countsDownFiveMinutesAndEmitsExpiryWarning` 및 `postPlayCooldown_transitionsAutomaticallyToRelaxAfterSevenMinutes` 통과.

### ✅ 결정 #3. 터널 세이프가드(`TUNNEL_SAFEGUARD`) 창문 자동 닫힘 안전 확인 팝업
- **`MainActivity.kt` / `PetSanctuaryController.kt` / `PetSanctuaryService.kt`**:
  - 사용자가 `터널 세이프가드` 카드를 누르거나 터널 감지 이벤트가 발생하면 즉시 창문을 닫지 않고 **`AlertDialog` 안전 확인 팝업**을 띄웁니다:
    - **`[창문 닫고 가동]`** (`closeWindowsOverride = true`): 4개 도어 창문 밀폐 + 내기순환 + 조명 + 저주파 차폐음
    - **`[창문 유지하고 가동]`** (`closeWindowsOverride = false`): 창문 작동 없이 내기순환 + 조명 + 저주파 차폐음만 가동
    - **`[취소]`**: 모드 변경 취소
  - 또한 창문이 닫힌 상태일 때 하단 공조 텔레메트리 카드에 **`| 창문밀폐`** 텍스트가 명시됩니다.
  - **검증**: JUnit `tunnelSafeguard_respectsWindowCloseConfirmationOverride` 통과.

---

## 3. 검증 결과 및 남은 한계 (투명한 구분)

### 3.1 직접 실행으로 검증 완료된 항목
- **단위 테스트 (JUnit 4 + Coroutines Test)**: 총 **14개 테스트 케이스 전원 통과 (0 failures, 0 errors, 0.164s)**
  - XML 리포트: `app/build/test-results/testDebugUnitTest/TEST-ai.pleos.petsanctuary.PetSanctuaryControllerTest.xml`
- **Debug & Release APK 빌드 및 서명 검증**:
  - `./gradlew testDebugUnitTest assembleDebug assembleRelease` 성공 (`BUILD SUCCESSFUL`)
  - 생성된 실행 가능 Release APK: `apk/PleosPetSanctuary_release.apk` 및 `apk/팀명_PleosPetSanctuary_release.apk` (**9,567,162 bytes, 약 9.12MB**)

### 3.2 환경적 한계 및 사용자 확인 안내
- 현재 백그라운드 ADB에 연결된 에뮬레이터 프로세스가 없으며, 에이전트가 백그라운드에서 에뮬레이터를 강제 기동할 경우 `.lock` 파일로 인해 사용자의 Android Studio 에뮬레이터 실행을 방해하므로 백그라운드 에뮬레이터 기동은 수행하지 않았습니다.
- 따라서 **이번 턴에 추가된 UI 요소(터널 창문 닫힘 확인 `AlertDialog` 팝업, 5분 휴게소 실시간 카운트다운 배너, VHAL 폴백 텍스트 표시)의 실제 화면 렌더링**은 Android Studio에서 **`Run 'app'` (▶)** 으로 실행하여 직접 눈으로 확인해 주셔야 합니다.
