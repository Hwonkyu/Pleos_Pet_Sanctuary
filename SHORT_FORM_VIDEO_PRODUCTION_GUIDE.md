# 🎬 Pleos Pet Sanctuary 60초 숏폼 제작 가이드 (Google Flow 용)

> **콘셉트**: "차에만 타면 낑낑대던 우리 강아지, 이제 현대 Pleos AAOS가 알아서 힐링해 줍니다"  
> **총 구성**: 10초 컷 × 6개 씬 = 총 60초 (YouTube Shorts, Instagram Reels, TikTok 포맷)  
> **도구**: Google Flow (Text-to-Video / Image-to-Video AI 모델)

---

## 🎞️ 60초 씬별 타임라인 요약
| 씬 번호 | 타임 | 테마 | 핵심 메시지 | 활용 이미지 에셋 |
| :---: | :---: | :--- | :--- | :--- |
| **Scene 1** | 00~10s | **문제 제기** | 차만 타면 불안해하고 침 흘리는 반려견 | [`cabin_01_relax_amber.jpg`](manual_screenshots/cabin_01_relax_amber.jpg) 변형 또는 실사 컷 |
| **Scene 2** | 10~20s | **솔루션 등장** | 버튼 하나로 조명·공조·사운드 원터치 제어 | [`manual_01_relax_main.png`](manual_screenshots/manual_01_relax_main.png) + [`cabin_01_relax_amber.jpg`](manual_screenshots/cabin_01_relax_amber.jpg) |
| **Scene 3** | 20~30s | **터널 세이프가드** | 터널 진입 전 창문 자동 밀폐 & 노이즈 캔슬 | [`manual_02_tunnel_popup.png`](manual_screenshots/manual_02_tunnel_popup.png) + [`cabin_02_tunnel_safeguard.jpg`](manual_screenshots/cabin_02_tunnel_safeguard.jpg) |
| **Scene 4** | 30~40s | **멀미 케어 & 쿨다운** | 직바람 없는 하향 송풍과 2열 시트 통풍 | [`manual_04_nausea_relief.png`](manual_screenshots/manual_04_nausea_relief.png) + [`cabin_03_nausea_cooldown_airflow.jpg`](manual_screenshots/cabin_03_nausea_cooldown_airflow.jpg) |
| **Scene 5** | 40~50s | **5분 휴게소 안심** | 분리불안 달래는 창문 잠금 & 엄마 목소리 | [`manual_05_guardian_voice.png`](manual_screenshots/manual_05_guardian_voice.png) + [`cabin_04_guardian_voice.jpg`](manual_screenshots/cabin_04_guardian_voice.jpg) |
| **Scene 6** | 50~60s | **안전 & 엔딩** | 주행 안전 규제(CarUx) & Pleos AAOS 엔딩 | [`manual_07_driving_d_ducked.png`](manual_screenshots/manual_07_driving_d_ducked.png) + 앱 로고 |

---

## 🎬 씬별 상세 프롬프트 & 연출 가이드 (Google Flow 입력용)

### 📍 Scene 1 (00s ~ 10s) : "차만 타면 불안해하는 우리 강아지..."
- **목적**: 시청자 3초 후킹(Hooking) 및 반려인 공감대 형성
- **투입 이미지 (Image-to-Video)**: 자동차 2열 카시트에 앉아 불안해하며 낑낑대거나 침을 흘리는 강아지 사진
- **Google Flow 프롬프트 (영문 입력 권장)**:
  ```text
  Cinematic close-up of an anxious cute golden retriever in a modern car rear seat looking nervous, panting softly, cinematic shallow depth of field, natural dim cabin lighting, 4k photorealistic, smooth slow camera push-in.
  ```
- **카메라 연출**: 불안한 강아지의 표정으로 천천히 슬로우 줌인 (Push-in)
- **보이스오버 (VO)**: *"차만 타면 멀미하고, 터널만 들어가면 덜덜 떨던 우리 강아지..."*
- **자막 (배민도현체/노토산스 B)**: `차만 타면 낑낑대고 멀미하는 강아지 🐶💦`

---

### 📍 Scene 2 (10s ~ 20s) : "원터치로 차량 전체가 안심 라운지로!"
- **목적**: 메인 대시보드 UI 터치 한 번으로 실내 분위기가 반전되는 시각적 쾌감
- **투입 이미지**: [`manual_01_relax_main.png`](manual_screenshots/manual_01_relax_main.png) ➡️ [`cabin_01_relax_amber.jpg`](manual_screenshots/cabin_01_relax_amber.jpg)
- **Google Flow 프롬프트**:
  ```text
  First-person driver POV finger gently taps 'RELAX' preset card on a sleek Genesis 27-inch automotive infotainment touch screen. The screen glows amber, then smooth camera pans to rear seat where warm amber ambient LED strips illuminate softly, the dog calms down lying comfortably on the premium pet seat, high-end automotive luxury interior, photorealistic 8k.
  ```
- **카메라 연출**: 손가락이 디스플레이의 [릴렉스 안심] 카드를 탭 ➡️ 부드럽게 2열로 패닝하며 따뜻한 앰버 무드램프가 켜지는 장면
- **보이스오버 (VO)**: *"현대 Pleos 펫 힐링 라운지! 터치 한 번이면 조명, 에어컨, 60BPM 핑크노이즈까지 한 번에 제어됩니다."*
- **자막**: `원터치로 조명 · 공조 · 심박 안정 사운드 동시 가동 ✨`

---

### 📍 Scene 3 (20s ~ 30s) : "터널 진입? 창문 밀폐 & 소음 차폐 드론 가동!"
- **목적**: 터널 패닉 방지 및 반려견 끼임 방지 안전 확인 팝업의 스마트함 강조
- **투입 이미지**: [`manual_02_tunnel_popup.png`](manual_screenshots/manual_02_tunnel_popup.png) ➡️ [`cabin_02_tunnel_safeguard.jpg`](manual_screenshots/cabin_02_tunnel_safeguard.jpg)
- **Google Flow 프롬프트**:
  ```text
  A modern electric car entering a brightly lit highway tunnel. Inside the cabin, car windows automatically slide up smoothly to seal shut. Soft warm white ambient lighting fills the interior. The happy dog sits securely looking forward, no fear of tunnel roar. Cinematic motion, futuristic sound-barrier visual wave, 4k realism.
  ```
- **카메라 연출**: 차량이 어두운 터널로 진입할 때 4개 도어 창문이 매끄럽게 닫히며 실내가 포근한 화이트 빛으로 안정화
- **보이스오버 (VO)**: *"터널이 다가오면 반려견 안전 확인 팝업과 함께 창문이 밀폐되고, 터널 굉음을 막아주는 저주파 차폐음이 켜져요."*
- **자막**: `🚇 터널 진입 전 창문 자동 밀폐 & 배기가스 100% 차단`

---

### 📍 Scene 4 (30s ~ 40s) : "멀미엔 얼굴 직바람 NO! 하향 송풍 & 2열 시트 통풍"
- **목적**: 디테일한 반려견 생체 배려 기능 (시트 통풍 + 하향 에어플로우)
- **투입 이미지**: [`manual_04_nausea_relief.png`](manual_screenshots/manual_04_nausea_relief.png) ➡️ [`cabin_03_nausea_cooldown_airflow.jpg`](manual_screenshots/cabin_03_nausea_cooldown_airflow.jpg)
- **Google Flow 프롬프트**:
  ```text
  Side angle of car interior showing gentle blue cyan airflow streams flowing along the car floor, avoiding the dog's face. The dog's dedicated ventilated rear seat emits gentle cool breeze, fur softly rustling in comfortable cooling. High-tech ventilation stream particle visualization, premium automotive quality, 8k.
  ```
- **카메라 연출**: 발 밑으로 흐르는 시원한 공기 흐름(Airflow 파티클)과 2열 통풍 시트에서 시원함을 느끼는 강아지 클로즈업
- **보이스오버 (VO)**: *"멀미할 땐 눈 코가 시리지 않게 발 밑으로만 시원한 바람! 2열 시트 통풍으로 체온도 빠르게 식혀줍니다."*
- **자막**: `🍃 얼굴 직바람 방지 하향 송풍 + 2열 시트 통풍 가동!`

---

### 📍 Scene 5 (40s ~ 50s) : "5분 휴게소 안심: 창문 잠금 & 엄마 목소리 믹싱"
- **목적**: 반려인의 최대 고민인 '잠깐 편의점/화장실 갈 때의 분리불안' 해결
- **투입 이미지**: [`manual_05_guardian_voice.png`](manual_screenshots/manual_05_guardian_voice.png) ➡️ [`cabin_04_guardian_voice.jpg`](manual_screenshots/cabin_04_guardian_voice.jpg)
- **Google Flow 프롬프트**:
  ```text
  Parked car at a clean highway rest stop. Inside, the infotainment display shows '5-Minute Guardian Active 04:30'. The car speakers pulse with gentle musical chimes mixed with a mother's warm voice. The dog is comfortably relaxed on the seat listening peacefully, windows securely locked, golden hour lighting outside, photorealistic.
  ```
- **카메라 연출**: 차 밖은 휴게소 주차장, 차 안 디스플레이는 5분 타이머 작동 ➡️ 스피커에서 보호자 목소리가 나오자 편안하게 엎드리는 강아지
- **보이스오버 (VO)**: *"휴게소 잠깐 갈 때도 안심! 창문 버튼은 잠기고, 스피커에선 엄마의 안심 목소리가 흘러나와요."*
- **자막**: `⏱️ 5분 휴게소 안심: 창문 잠금 + 보호자 목소리 믹싱 재생`

---

### 📍 Scene 6 (50s ~ 60s) : "주행 안전은 지키고, 반려견은 행복하게!"
- **목적**: AAOS 주행 안전 규제(CarUxRestrictions) 준수 강조 및 브랜드 각인 엔딩
- **투입 이미지**: [`manual_07_driving_d_ducked.png`](manual_screenshots/manual_07_driving_d_ducked.png) + 대시보드 전경
- **Google Flow 프롬프트**:
  ```text
  Wide shot of modern car cockpit driving smoothly into scenic sunset. Infotainment screen automatically dims to safe driving mode. In the back, the dog is peacefully sleeping. The driver smiles looking at rearview mirror. Hyundai Pleos logo fades in with sleek typography 'Pleos Pet Sanctuary', professional automotive commercial ending.
  ```
- **카메라 연출**: 와이드 운전석 콕핏에서 백미러로 잠든 강아지를 바라보며 미소 짓는 운전자 ➡️ 앱 로고 및 엔딩 슬레이트
- **보이스오버 (VO)**: *"주행 중엔 눈부심 없이 안전 디밍까지! 세상에서 가장 안전하고 편안한 이동, 플레오스 펫 힐링 라운지와 함께하세요."*
- **자막**: `🐾 플레오스 펫 힐링 라운지 | Hyundai Pleos AAOS`

---

## 💡 숏폼 편집 꿀팁 (CapCut / Premiere 등에서 합칠 때)
1. **BGM / 사운드 이펙트**:
   - Scene 1에는 약간의 심장박동 소리(긴장감)
   - Scene 2 탭 순간에 청량한 `'팅~'` UI 터치 사운드와 함께 잔잔한 Lo-Fi 힐링 음악 전환
   - Scene 3에서는 웅장한 터널 소리가 나다가 창문이 닫히며 `'훅-'` 하고 적막해지는 사운드 디자인 적용
2. **화면 분할(Split Screen) 기법**:
   - 상단 50%에는 **실제 에뮬레이터 화면([manual_*.png])**, 하단 50%에는 **실내 렌더링([cabin_*.jpg])** 을 배치하면 앱 기능과 실차 반응이 한눈에 보여 공모전 심사위원 및 대중 평가에서 전달력이 극대화됩니다.
