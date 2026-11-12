package com.team.firstcar.settings.transform

import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import kotlin.math.roundToInt

/** 화면과 변환 근거에 쓰는 값 표시 (순수 함수) */
object ValueFormat {
    fun format(v: SettingValue?, enumLabels: Map<String, String> = emptyMap()): String = when (v) {
        null -> "-"
        is SettingValue.Switch -> if (v.on) "켜짐" else "꺼짐"
        is SettingValue.Level -> levelText(v.value, 0)
        is SettingValue.Number -> num(v.value) + when (v.unit) {
            TempUnit.CELSIUS -> "°C"; TempUnit.FAHRENHEIT -> "°F"; null -> ""
        }
        is SettingValue.Enum -> enumLabels[v.name.uppercase()] ?: v.name
        is SettingValue.Color -> hex(v.rgb) + (v.brightness?.let { ", 밝기 $it" } ?: "")
        is SettingValue.Position -> "위치 ${v.value}"
    }

    fun levelText(v: Int, min: Int): String = if (v <= 0 && min <= 0) "꺼짐" else "${v}단계"

    fun num(d: Double): String {
        val r = (d * 10).roundToInt() / 10.0
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }

    fun hex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)
}
