package com.team.firstcar.settings.model

/**
 * 순수 Kotlin 모델. UI와 도메인은 SDK 타입을 모르고 이 타입만 본다.
 * (Android, Pleos SDK에 의존하지 않으므로 JVM 단위 테스트가 가능하다)
 */

enum class SettingType { SWITCH, LEVEL, NUMBER, ENUM, COLOR, POSITION }

sealed interface SettingValue {
    data class Switch(val on: Boolean) : SettingValue
    data class Level(val value: Int) : SettingValue
    data class Number(val value: Double, val unit: TempUnit? = null) : SettingValue
    data class Enum(val name: String) : SettingValue
    /** rgb는 0xRRGGBB. brightness는 같은 값에 묶여 오는 경우만 채운다. */
    data class Color(val rgb: Int, val brightness: Int? = null) : SettingValue
    data class Position(val value: Int) : SettingValue
}

enum class TempUnit { CELSIUS, FAHRENHEIT }

/**
 * 한 차량에서 한 항목이 지원되는지와 범위.
 * 모든 필드에서 null은 "모름"이다. 모르는 것을 "같다"로 처리하지 않는다.
 */
data class CapabilityInfo(
    val supported: Boolean? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val allowed: Set<String>? = null,
    val palette: List<Int>? = null,
    val unit: TempUnit? = null,
)

data class CarIdentity(
    val key: String,          // 첫 탑승 판단용 키
    val displayName: String,  // 화면 표시용
    val isDemo: Boolean = false,
)

/** 저장 당시의 범위(capability)를 같이 저장해야 비율 환산이 가능하다. */
data class SavedItem(
    val itemId: String,
    val value: SettingValue?,
    val capability: CapabilityInfo?,
    val failure: String? = null, // 읽지 못한 이유. value == null일 때만 의미가 있다
)

data class ProfileSnapshot(
    val version: Int = CURRENT_VERSION,
    val savedAt: Long,
    val sourceCar: CarIdentity,
    val items: List<SavedItem>,
) {
    val readCount get() = items.count { it.value != null }
    val failedCount get() = items.count { it.value == null }

    companion object { const val CURRENT_VERSION = 1 }
}

enum class CompareStatus { SAME, ADAPTED, UNSUPPORTED, UNKNOWN, REFERENCE_ONLY }

data class CompareResult(
    val itemId: String,
    val status: CompareStatus,
    val saved: SettingValue?,
    val proposed: SettingValue?,
    val note: String,
)
