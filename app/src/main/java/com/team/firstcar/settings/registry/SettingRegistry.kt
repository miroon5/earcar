package com.team.firstcar.settings.registry

import com.team.firstcar.settings.model.SettingType

/** Vehicle SDK 도메인. getter 이름은 공식 샘플(VehicleApiExplorer)에서 확인한 것. */
enum class VehicleDomain { HVAC, LIGHT, SEAT, STEERING_WHEEL, SIDE_MIRROR, DISPLAY, DRIVING_MODE, TOLL, WINDOW, CAR_INFO }

/**
 * API 위치 후보. [key]는 메서드 이름에서 check/get/getCurrent/register 접두사와
 * Capabilities 접미사를 뗀 부분이다. 예) key "HvacSeatTemperature"
 *   -> checkHvacSeatTemperatureCapabilities, getCurrentHvacSeatTemperature / getHvacSeatTemperature
 * 실제 이름은 런타임에 리플렉션으로 찾고, 못 찾으면 진단 화면에 "메서드 없음"으로 보인다.
 * 리스너 이름(문서 기준)에서 Listener를 뗀 것을 1순위 후보로 둔다.
 */
data class ApiLocator(val domain: VehicleDomain, val key: String)

data class SettingItem(
    val id: String,
    val label: String,
    val group: String,
    val type: SettingType,
    val locators: List<ApiLocator>,
    /** 지원 안 될 때 보여 줄 대체 안내 */
    val alternative: String,
    /** 이 차에서 직접 바꿀 때의 위치 안내 (제어 SDK가 없으므로 안내로 적용) */
    val whereToSet: String,
    /** Enum 값의 한국어 표시 */
    val enumLabels: Map<String, String> = emptyMap(),
)

object SettingRegistry {
    private fun hvac(vararg keys: String) = keys.map { ApiLocator(VehicleDomain.HVAC, it) }

    val items: List<SettingItem> = listOf(
        // 공조
        SettingItem("hvac.temperature", "설정 온도", "공조", SettingType.NUMBER,
            hvac("HvacTemperatureValue", "HvacTemperature", "Temperature"),
            "공조 화면에서 온도를 직접 맞춰 주세요.", "공조 > 운전석 온도"),
        SettingItem("hvac.fanSpeed", "풍량", "공조", SettingType.LEVEL,
            hvac("HvacFanSpeed", "FanSpeed"),
            "공조 화면에서 풍량을 맞춰 주세요.", "공조 > 풍량"),
        SettingItem("hvac.ac", "에어컨(AC)", "공조", SettingType.SWITCH,
            hvac("HvacAcState", "HvacAc", "AcState"),
            "공조 화면의 A/C 버튼을 확인해 주세요.", "공조 > A/C"),
        SettingItem("hvac.maxAc", "최대 냉방", "공조", SettingType.SWITCH,
            hvac("HvacMaxAcState", "HvacMaxAc", "MaxAcState"),
            "에어컨을 켜고 온도를 가장 낮게 두면 비슷합니다.", "공조 > MAX A/C"),
        SettingItem("hvac.auto", "오토 공조", "공조", SettingType.SWITCH,
            hvac("HvacAutoState", "HvacAuto", "AutoState"),
            "온도와 풍량을 직접 맞춰 주세요.", "공조 > AUTO"),
        SettingItem("hvac.dual", "운전석/동승석 개별 온도", "공조", SettingType.SWITCH,
            hvac("HvacDualState", "HvacDual", "DualState"),
            "이 차는 온도를 하나로 맞춥니다.", "공조 > SYNC 해제"),
        SettingItem("hvac.recirculation", "내기/외기", "공조", SettingType.ENUM,
            hvac("HvacRecirculationState", "HvacRecirculation", "RecirculationState"),
            "공조 화면의 내기순환 버튼을 확인해 주세요.", "공조 > 내기순환",
            enumLabels = mapOf("RECIRCULATION" to "내기", "INTERNAL" to "내기", "FRESH" to "외기",
                "EXTERNAL" to "외기", "AUTO" to "자동", "ON" to "내기", "OFF" to "외기")),
        SettingItem("hvac.airCleaner", "공기청정", "공조", SettingType.SWITCH,
            hvac("HvacAirCleanerState", "HvacAirCleaner", "AirCleanerState"),
            "내기순환을 켜 두면 외부 먼지 유입을 줄일 수 있습니다.", "공조 > 공기청정"),
        SettingItem("hvac.seatHeat", "운전석 열선", "시트", SettingType.LEVEL,
            hvac("HvacSeatTemperature", "SeatTemperature") + ApiLocator(VehicleDomain.SEAT, "SeatTemperature"),
            "이 차에는 시트 열선이 없습니다. 공조 온도를 1~2도 높여 보세요.", "공조 > 시트 열선"),
        SettingItem("hvac.seatVent", "운전석 통풍", "시트", SettingType.LEVEL,
            hvac("HvacSeatVentilation", "SeatVentilation") + ApiLocator(VehicleDomain.SEAT, "SeatVentilation"),
            "이 차에는 시트 통풍이 없습니다. 풍량을 한 단계 올려 보세요.", "공조 > 시트 통풍"),
        SettingItem("hvac.steeringHeat", "핸들 열선", "시트", SettingType.SWITCH,
            hvac("HvacSteeringWheelHeatState", "HvacSteeringWheelHeat") +
                ApiLocator(VehicleDomain.STEERING_WHEEL, "SteeringWheelHeatState") +
                ApiLocator(VehicleDomain.STEERING_WHEEL, "SteeringWheelHeat"),
            "이 차에는 핸들 열선이 없습니다. 시트 열선을 한 단계 높이거나 공조 온도를 올려 보세요.",
            "공조 > 핸들 열선"),
        SettingItem("hvac.mirrorHeat", "사이드미러 열선", "외관", SettingType.SWITCH,
            hvac("HvacSideMirrorHeatState", "HvacSideMirrorHeat") +
                ApiLocator(VehicleDomain.SIDE_MIRROR, "SideMirrorHeatState") +
                ApiLocator(VehicleDomain.SIDE_MIRROR, "SideMirrorHeat"),
            "뒷유리 열선을 켜면 미러 열선이 같이 켜지는 차가 많습니다.", "공조 > 뒷유리/미러 열선"),

        // 실내 조명
        SettingItem("ambient.brightness", "앰비언트 밝기", "조명", SettingType.LEVEL,
            listOf(ApiLocator(VehicleDomain.LIGHT, "AmbientLightBrightness")),
            "이 차에는 앰비언트 라이트가 없습니다.", "설정 > 조명 > 앰비언트"),
        SettingItem("ambient.color", "앰비언트 색", "조명", SettingType.COLOR,
            listOf(ApiLocator(VehicleDomain.LIGHT, "AmbientLightBrightnessColor"),
                ApiLocator(VehicleDomain.LIGHT, "AmbientLightColor")),
            "이 차에는 앰비언트 색 설정이 없습니다.", "설정 > 조명 > 앰비언트 색"),
        SettingItem("ambient.autoByDriveMode", "주행 모드 연동 조명", "조명", SettingType.SWITCH,
            listOf(ApiLocator(VehicleDomain.LIGHT, "AmbientColorAutoByDriveModeEnabled"),
                ApiLocator(VehicleDomain.LIGHT, "AmbientColorAutoByDriveMode")),
            "앰비언트 색을 직접 골라 주세요.", "설정 > 조명 > 주행 모드 연동"),

        // 표시, 주행
        SettingItem("display.tempUnit", "온도 단위", "표시", SettingType.ENUM,
            listOf(ApiLocator(VehicleDomain.DISPLAY, "TemperatureDisplayUnit")),
            "설정 > 단위에서 바꿀 수 있습니다.", "설정 > 일반 > 단위",
            enumLabels = mapOf("CELSIUS" to "섭씨(°C)", "FAHRENHEIT" to "화씨(°F)")),
        SettingItem("drivingMode", "주행 모드", "주행", SettingType.ENUM,
            listOf(ApiLocator(VehicleDomain.DRIVING_MODE, "DrivingModeState"),
                ApiLocator(VehicleDomain.DRIVING_MODE, "DrivingMode")),
            "이 차에는 같은 이름의 주행 모드가 없습니다. 드라이브 모드 버튼으로 골라 주세요.",
            "센터 콘솔 > 드라이브 모드",
            enumLabels = mapOf("NORMAL" to "노멀", "COMFORT" to "컴포트", "ECO" to "에코", "SPORT" to "스포츠",
                "SMART" to "스마트", "SNOW" to "스노우", "MY_DRIVE" to "마이 드라이브")),
        SettingItem("toll.voiceVolume", "하이패스 안내 음량", "편의", SettingType.NUMBER,
            listOf(ApiLocator(VehicleDomain.TOLL, "EtcsVoiceGuidanceVolume")),
            "이 차에는 하이패스 단말기가 없습니다.", "설정 > 하이패스 > 음량"),
        SettingItem("smartGlass.level", "스마트 글라스 투명도", "편의", SettingType.LEVEL,
            listOf(ApiLocator(VehicleDomain.WINDOW, "SmartGlassLevel")),
            "이 차에는 스마트 글라스가 없습니다. 선쉐이드를 이용해 주세요.", "설정 > 스마트 글라스"),

        // 시트 위치: 형상이 달라 자동 적용하지 않고 참고 비율만 보여 준다
        seatPos("seat.foreAft", "시트 앞뒤", "SeatForeAftPosition"),
        seatPos("seat.height", "시트 높이", "SeatHeightPosition"),
        seatPos("seat.depth", "방석 길이", "SeatDepthPosition"),
        seatPos("seat.tilt", "방석 각도", "SeatTiltPosition"),
        seatPos("seat.backrest", "등받이 각도", "SeatBackrestAnglePosition"),
        seatPos("seat.lumbarForeAft", "허리 지지 앞뒤", "SeatLumbarForeAftPosition"),
        seatPos("seat.lumbarSide", "허리 측면 지지", "SeatLumbarSideSupportPosition"),
        seatPos("seat.headrestAngle", "헤드레스트 각도", "SeatHeadrestAnglePosition"),
        seatPos("seat.headrestForeAft", "헤드레스트 앞뒤", "SeatHeadrestForeAftPosition"),
        seatPos("seat.headrestHeight", "헤드레스트 높이", "SeatHeadrestHeightAftPosition", "SeatHeadrestHeightPosition"),
        seatPos("seat.beltHeight", "안전벨트 높이", "SeatBeltHeightPosition"),
    )

    private fun seatPos(id: String, label: String, vararg keys: String) = SettingItem(
        id, label, "시트 위치(참고)", SettingType.POSITION,
        keys.map { ApiLocator(VehicleDomain.SEAT, it) },
        "이 차에서는 시트를 직접 맞춰 주세요.", "시트 측면 스위치",
    )

    private val byId = items.associateBy { it.id }
    fun get(id: String): SettingItem? = byId[id]

    /** CarInfo는 세팅이 아니라 차종 표시용. 후보 키 순서대로 시도한다 [추정] */
    val carInfoMake = listOf("Manufacturer", "Make", "CarManufacturer")
    val carInfoModel = listOf("Model", "ModelName", "CarModel")
    val carInfoYear = listOf("ModelYear", "Year")
}
