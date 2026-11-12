# 개발 노트 (팀 내부)

## 0. 먼저 알아 둘 것
- 이 코드는 작업 환경에서 Pleos Maven과 Maven Central에 접근할 수 없어 **컴파일하지 못했습니다**. 첫 빌드에서 오류가 나면 메시지를 그대로 공유해 주세요.
- 빌드가 되면 `./gradlew testDebugUnitTest`로 변환, 비교, 저장 포맷 테스트(TransformerTest, ComparatorTest, ProfileJsonTest)부터 돌려 주세요.

## 1. 첫 빌드 체크
| 증상 | 확인할 것 |
|---|---|
| `ai.pleos.playground:Vehicle` 해결 실패 | settings.gradle.kts의 Pleos Maven URL, `gradle/libs.versions.toml`의 vehicleSdk(2.3.1 / 2.3.2) |
| `Unresolved reference: getHVAC` 등 | `VehicleSession.kt`의 도메인 getter. 공식 샘플 VehicleApiExplorer와 같은 이름이라 맞을 가능성이 높음 |
| `AreaBounds`, `CapabilityConfig` import 오류 | `ValueMapper.kt` 상단 import. 샘플 `ApiNumericRange.kt`와 같은 경로 |
| 앱 실행 직후 "Vehicle 초기화 실패" | 일반 AVD가 아니라 Pleos 전용 AVD를 Cold boot로 켰는지 |
| 대부분 항목이 "PERMISSION_DENIED" | Manifest 퍼미션. 해당 함수의 `@RequiresPermission`을 자동완성으로 확인 |

## 2. 11/7 게이트: 읽힘 결과표 만들기
1. 에뮬레이터에서 앱 실행 → 홈 왼쪽 아래 **진단** → **모든 항목 읽기**
2. 항목마다 `api:`(실제로 찾은 메서드), `값:`, `cap:`(supported, min, max), `실패:`가 표시됩니다. 같은 내용이 Logcat 태그 `FirstCar`에도 찍힙니다.
   ```sh
   adb logcat -s FirstCar
   ```
3. "메서드 없음"이 나온 항목은 **API 목록**을 눌러 실제 메서드 이름을 찾고, `settings/registry/SettingRegistry.kt`의 해당 항목 `locators` 키를 고칩니다.
   - 키 = 메서드 이름에서 `check`/`getCurrent`/`get` 접두사와 `Capabilities` 접미사를 뗀 부분
   - 예: 실제 이름이 `getCurrentSeatHeatingLevel`이면 키는 `SeatHeatingLevel`
4. "값 형식을 해석하지 못함"이면 `raw` 값을 보고 `settings/reader/ValueMapper.kt`의 `toSetting`에 변환을 추가합니다.
5. 게이트 기준: 읽히는 항목이 8개 미만이면 시연을 "읽힘 + 지원 여부 비교"로 줄이고, 변환 장면은 읽힌 Level 항목 2개 이상으로 구성합니다.

결과표 양식(기획서 부록용):
| 항목 | 찾은 API | 읽힘 | capability 범위 | 비고 |
|---|---|---|---|---|
| 운전석 열선 | checkHvacSeatTemperatureCapabilities / getCurrentHvacSeatTemperature | O | 0~3 | |

## 3. 비교 대상 차량 바꾸기
`app/src/main/assets/vehicles/*.json`에 파일을 추가하면 진단 화면의 차량 목록에 자동으로 나타납니다.
- `supported: false` → 지원 안 됨, 항목을 빼면 → 확인 불가
- Level/Number는 `min`, `max`(Number는 `step`, `unit`), Enum은 `allowed`, Color는 `palette`(#RRGGBB)

## 4. 시연 반복 (리허설)
- 진단 > **첫 탑승 기록 초기화**로 "처음 타는 차입니다" 카드를 다시 띄울 수 있습니다.
- 진단 > **내 데이터 지우기**로 저장을 초기화합니다.
- 장애 대비: SDK가 전부 실패해도 비교 화면은 열리지 않습니다(저장본이 필요). 리허설 때 저장본을 한 번 만들어 두고, 영상 녹화본을 따로 준비하세요.

## 5. 기획서 "5. 개발 계획" 초안 (실제 코드 기준)
- **사용 SDK:** Pleos Vehicle SDK 2.3.1 (조회만). AAOS `CarUxRestrictionsManager`.
- **사용 API:** HVAC(HvacTemperature/Value, HvacFanSpeed, HvacAcState, HvacMaxAcState, HvacAutoState, HvacDualState, HvacRecirculationState, HvacAirCleanerState, HvacSeatTemperature, HvacSeatVentilation, HvacSteeringWheelHeatState, HvacSideMirrorHeatState), Light(AmbientLightBrightness, AmbientLightBrightnessColor, AmbientColorAutoByDriveModeEnabled), Display(TemperatureDisplayUnit), DrivingMode(DrivingModeState), Toll(EtcsVoiceGuidanceVolume), Window(SmartGlassLevel), Seat(SeatForeAft, Height, Depth, Tilt, BackrestAngle, Lumbar, Headrest, BeltHeight Position), CarInfo. 항목마다 `check…Capabilities`로 지원 여부와 범위를 확인한 뒤 현재 값을 1회 조회합니다(스냅샷).
- **주행 중과 정차 중:** 정차 중에만 저장, 비교, 상세 화면을 엽니다. 주행 규제가 활성화되면 모든 화면을 닫고 한 줄 상태만 남기며 애니메이션을 끕니다. 차량 설정 변경(제어)은 하지 않습니다.
- **화면 구성:** portrait(1 window)는 홈 → 저장 → 비교 요약 → 항목별 상세, landscape(2 window, 폭 900dp 이상)는 요약과 상세를 나란히 보여 줍니다. 버튼 높이 88dp, 본문 24sp 이상, 대비 4.5:1 이상.
- **안정성:** 항목별 2.5초 타임아웃과 실패 격리, 못 읽은 항목은 이유와 함께 "확인 불가"로 표시합니다. 네트워크를 쓰지 않습니다.
- **개인정보:** 이름, 연락처 없이 세팅 값만 기기 로컬에 저장하고, 공유 차량용 "내 데이터 지우기"를 제공합니다.

## 6. 남은 일 (P1 이후)
- F4 적용: 제어 SDK 상태를 Playground My Project 콘솔에서 확인 → 가능하면 `set…` 호출 후 재조회로 반영 확인 (`CONTROL_CAR_*` 퍼미션 추가, NO_SETUP이면 호출 금지)
- F6 정리: Pleos 계정 로그아웃 연동 (현재는 로컬 데이터 삭제만)
- Pretendard 폰트: `res/font`에 Regular, Bold를 넣고 `ui/theme/Theme.kt`의 `Type.Brand`만 바꾸면 됩니다 (OFL 라이선스 고지 필요)
