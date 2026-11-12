# 내 세팅 따라가기 (FirstCarGuide)

> 처음 타는 현대차에서, 내가 쓰던 차의 세팅을 그대로 가져오거나 이 차에서 가능한 가장 비슷한 환경으로 맞춰 주는 첫 탑승 온보딩 앱.

차마다 지원 항목과 범위가 다르기 때문에 "복사"가 아니라 **이 차의 capability에 맞춘 변환**을 합니다.
항목마다 결과를 **유지 / 유사 / 미지원 / 확인 불가 / 참고**로 나눠 보여 줍니다. 읽지 못했거나 판단할 수 없는 항목을 "유지"로 표시하지 않습니다.

## 실행 방법
1. Android Studio(최신 버전)에서 이 폴더를 엽니다. Gradle wrapper가 없으면 Android Studio가 생성합니다.
2. Pleos Connect Emulator v2.1.0(API 34) AVD를 **Cold boot**, RAM 4GB로 실행합니다. Quick boot로 켜면 VHAL을 쓸 수 없어 Vehicle SDK가 실패합니다.
3. Android Studio에서 Run을 누르거나 `adb install -r apk/TEAM_FirstCarGuide_release.apk`로 설치합니다.
4. 차량 상태 시뮬레이션: `adb forward tcp:8088 tcp:8088`을 실행한 뒤 브라우저에서 http://127.0.0.1:8088/ 을 엽니다(Climate 탭).
5. 주행 규제 테스트: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`, 해제는 `off`.
6. 단위 테스트: `./gradlew testDebugUnitTest` (변환 규칙, 비교, 저장 포맷)

## 테스트 환경 및 필요한 설정
- Android Studio, JDK 17, AGP 8.8.2, Kotlin 2.0.0, compileSdk/targetSdk 35, minSdk 28
- Pleos Connect Emulator 2.1.0
- 사용 SDK: **Vehicle SDK 2.3.1**(조회만 사용). 공식 샘플 앱과 같은 버전입니다.
- 네트워크와 서버를 쓰지 않습니다. 세팅은 기기 로컬(앱 전용 저장소)에만 저장합니다. 비밀값이 필요 없습니다.
- release 서명: `local.properties`에 `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`를 넣습니다. keystore는 커밋하지 않습니다.

## 시연 시나리오 (약 2분)
| # | 조작 | 앱 반응 |
|---|---|---|
| 1 | Virtual Console > Climate에서 운전석 시트 열선 3단계, 온도 22도, 핸들 열선 ON | (앱이 다음 저장 때 이 값을 읽음) |
| 2 | 앱 홈에서 **내 세팅 저장하기** | 읽는 동안 원형 로딩, 끝나면 저장한 개수와 분류 카드. 분류를 누르면 저장한 값을 항목별로 확인 (못 읽은 항목과 이유는 진단 화면에서 확인) |
| 3 | 홈 왼쪽 아래 **진단**에서 비교 대상을 **KONA**로 전환 → 뒤로 | 홈에 "처음 타는 차입니다" 카드 표시 (F5) |
| 4 | **맞춰 보기** | 결과 요약: 유지 / 유사 / 미지원 / 확인 불가 개수, 시트 자세 그림 (스크롤 없음) |
| 5 | **항목별로 보기** | 열선 "3단계 → 2단계 (3단계 중 3 → 2단계 중 2)", 핸들 열선 "미지원 + 시트 열선을 한 단계 높여 보세요", 직접 설정 위치 안내 |
| 6 | `driving_regulation on` | 모든 화면이 닫히고 "주행 중 · 유지 n · 유사 n · 미지원 n" 한 줄만 남음 |
| 7 | `driving_regulation off` | 홈으로 복귀 |
| 8 | 진단 > **내 데이터 지우기** | 공유 차량에서 내리기 전 정리 |

- 차 이름은 공식 영문 표기를 씁니다. 기본 차는 "IONIQ 9"이고, 비교 대상 "KONA", "GRANDEUR"의 지원 범위는 `app/src/main/assets/vehicles/*.json`에 적어 둔 값이고 실제 차종 제원을 옮긴 것이 아닙니다. 저장 단계는 항상 실제 Vehicle SDK 조회입니다.
- 비교 대상을 **현재 차량**으로 두면 이 에뮬레이터의 capability를 실제 SDK로 조회해 비교합니다.

## 구조
```
com.team.firstcar
├─ MainViewModel            UiState 하나. Vehicle 세션, 주행 규제 모니터의 생명주기 소유
├─ vehicle/                 VehicleSession(초기화, 도메인 객체), DrivingStateMonitor(CarUxRestrictions)
├─ settings/
│  ├─ model/                SettingValue, CapabilityInfo, ProfileSnapshot, CompareResult (순수 Kotlin)
│  ├─ registry/             SettingRegistry: 30개 설정 항목과 API 이름 후보
│  ├─ reader/               ReflectiveApi(메서드 탐색, 호출, 타임아웃), ValueMapper, SettingsReader
│  ├─ source/               RealCapabilityProvider(SDK), DemoCapabilityProvider(JSON)
│  ├─ transform/            Transformer(변환 규칙), ValueFormat (순수 Kotlin, 단위 테스트)
│  ├─ compare/              Comparator (순수 Kotlin, 단위 테스트)
│  └─ store/                ProfileJson, ProfileStore(앱 전용 저장소 JSON 1개)
├─ domain/FirstRideStore    첫 탑승 판단
└─ ui/                      홈, 저장, 비교 요약, 비교 상세, 진단 (Compose)
```

### Vehicle SDK 호출 방식
공식 문서에는 HVAC, Light, Seat 등의 메서드 시그니처가 없고 리스너 이름만 있습니다. 그래서 공식 샘플(`pleos-sample-vehicle-app`의 `VehicleApiExplorer`)과 같은 방식으로, 실행할 때 리플렉션으로 메서드를 찾아 호출합니다.
- 항목마다 API 이름 후보(예: `HvacSeatTemperature`)를 두고, 실행할 때 `check{이름}Capabilities`와 `getCurrent{이름}` / `get{이름}`을 찾습니다.
- 콜백(`onComplete`, `onFailed`)과 area(운전석 `ROW_1_LEFT`)는 자동으로 채우고, 호출마다 2.5초 타임아웃을 둡니다. 항목 하나가 실패해도 나머지는 계속 읽습니다.
- 이름 후보가 틀리면 그 항목만 "메서드 없음"이 됩니다. **진단 > API 목록**(Logcat 태그 `FirstCar`)에서 실제 메서드 이름을 확인하고 `SettingRegistry`의 후보를 고치면 됩니다.

### 변환 규칙 (Transformer)
| 타입 | 규칙 |
|---|---|
| Switch | 지원하면 그대로, 아니면 지원 안 됨 |
| Level | 범위가 같으면 그대로. 다르면 `round(1 + (v-1)/(oldMax-1) × (newMax-1))`. 꺼짐(0)은 꺼짐 유지 |
| Number | 단위 환산(°C/°F) → 새 범위로 클램프 → 스텝에 스냅 |
| Enum | 새 값 집합에 있으면 그대로, 없으면 지원 안 됨 (대체 매핑 안 함) |
| Color | 지원 색 중 RGB 거리가 가장 가까운 색 |
| Position(시트 위치) | 비율만 계산해 참고용으로 표시, 자동 적용 안 함 |
| capability 모름 | 확인 불가. 절대 "그대로"로 처리하지 않음 |

### 주행 규제
- `distractionOptimized` 선언, `CarUxRestrictionsManager`로 `isRequiresDistractionOptimization`을 구독합니다.
- 주행 중에는 모든 화면을 닫고 한 줄 상태만 보여 주며, 애니메이션을 끕니다. 진행 중이던 저장과 비교는 취소합니다.
- 제어(set)는 하지 않습니다. 차량 제어 SDK가 준비되기 전까지는 "직접 설정 위치" 안내로 대체합니다.

### 퍼미션 (조회만)
`CAR_CLIMATE`, `CAR_LIGHTS`, `CAR_SEATS`, `READ_CAR_STEERING`, `CAR_MIRRORS`, `CAR_INFO`, `READ_CAR_DISPLAY_UNITS`, `CAR_DRIVE`, `CAR_TOLL`, `CAR_WINDOWS` (모두 `pleos.car.permission.` 접두사). `INTERNET`과 `CONTROL_*`은 선언하지 않습니다.

## APK
- 위치: `apk/TEAM_FirstCarGuide_release.apk` (`TEAM`을 팀명으로 바꿔 주세요)
- 빌드: `./gradlew assembleRelease` 후 `app/build/outputs/apk/release/app-release.apk`를 위 경로로 복사
- (용량 초과 시) GitHub Release 링크:

## 디자인
- 블랙 앤 화이트. 강조는 색 대신 검정/흰색 면의 대비로만 합니다(오류 표시와 시트 열선·통풍 빛만 예외). pill 버튼, 28dp 라운드, 장식 없음
- 애니메이션은 버튼 눌림 표시와 시트 자세 이동(0.5초) 두 가지뿐입니다
- 화면은 라이트 한 가지로 통일했습니다. 넓은 화면에서는 왼쪽에 누르는 것(버튼), 오른쪽에 보는 것을 둡니다. 홈은 왼쪽에 큰 버튼, 오른쪽에 흰 박스(시트 그림 + 헤드라인)입니다
- 차량 터치 기준: 주 버튼 88dp, 홈 액션 타일 128dp 이상, 칩과 뒤로 버튼 76~88dp, 컨트롤 간격 16dp 이상. 본문 24sp 이상
- 항목별 아이콘(공조, 시트, 핸들, 미러, 조명, 표시, 주행, 편의)은 흰 선 벡터이고 검정 타일 위에 올립니다. `python3 design/icons/build_icons.py app/src/main/res/drawable`로 다시 만듭니다
- 시트 그림은 벡터입니다. 레일, 방석, 등받이, 헤드레스트 4개 레이어라 저장한 시트 위치 비율대로 움직입니다(`ui/components/SeatVisual.kt`, 모양은 `SeatArt.kt`)
- 시트 모양을 고친 뒤 다시 생성: `python3 design/seat/build_seat.py --kotlin` (미리보기는 `design/seat/seat_preview.svg`). 이전 3D 원본은 `design/blender/`에 남아 있지만 앱에서는 쓰지 않습니다
- 시스템 애니메이션 끄기 설정이나 주행 중이면 모든 모션을 생략합니다

## 오픈소스 라이선스
AndroidX, Jetpack Compose, Kotlin, kotlinx.coroutines (Apache License 2.0), org.json (테스트 전용, JSON License)
