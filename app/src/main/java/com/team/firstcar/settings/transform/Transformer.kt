package com.team.firstcar.settings.transform

import com.team.firstcar.settings.model.CapabilityInfo
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class TransformOutcome(
    val status: CompareStatus,
    val proposed: SettingValue?,
    val note: String,
)

/**
 * 유사 환경 변환 규칙 (기획서 6.6). 전부 순수 함수다.
 * 원칙: 대상 차의 capability를 모르면 UNKNOWN. 모르는 것을 SAME으로 만들지 않는다.
 */
object Transformer {

    fun transform(saved: SettingValue, old: CapabilityInfo?, target: CapabilityInfo?): TransformOutcome {
        if (target == null || target.supported == null && saved !is SettingValue.Position) {
            return unknown("이 차의 지원 여부를 확인하지 못했어요")
        }
        if (target.supported == false) {
            return TransformOutcome(CompareStatus.UNSUPPORTED, null, "이 차에는 없는 기능이에요")
        }
        return when (saved) {
            is SettingValue.Switch -> TransformOutcome(CompareStatus.SAME, saved, "${ValueFormat.format(saved)} 유지")
            is SettingValue.Level -> level(saved, old, target)
            is SettingValue.Number -> number(saved, target)
            is SettingValue.Enum -> enumValue(saved, target)
            is SettingValue.Color -> color(saved, target)
            is SettingValue.Position -> position(saved, old, target)
        }
    }

    // ---------- Level: 비율 환산 ----------
    fun level(saved: SettingValue.Level, old: CapabilityInfo?, target: CapabilityInfo): TransformOutcome {
        val newMin = target.min?.roundToInt()
        val newMax = target.max?.roundToInt()
        if (newMin == null || newMax == null || newMax < newMin) {
            return unknown("이 차의 단계 정보를 얻지 못했어요")
        }
        val v = saved.value
        val oldMin = old?.min?.roundToInt()
        val oldMax = old?.max?.roundToInt()

        // 저장 당시 범위를 모르면 비율을 낼 수 없다. 같은 눈금이라고 보고 범위 안이면 그대로, 밖이면 끝값으로 맞춘다
        if (oldMin == null || oldMax == null || oldMax < oldMin) {
            val clamped = v.coerceIn(newMin, newMax)
            return if (clamped == v) {
                TransformOutcome(CompareStatus.SAME, saved, "${v}단계 유지 (저장 당시 범위 정보 없음)")
            } else {
                TransformOutcome(CompareStatus.ADAPTED, SettingValue.Level(clamped),
                    "${v}단계 → ${clamped}단계 (이 차 범위 ${newMin}~${newMax}에 맞춤)")
            }
        }
        if (oldMin == newMin && oldMax == newMax) {
            return TransformOutcome(CompareStatus.SAME, saved, "${ValueFormat.levelText(v, newMin)} 유지")
        }
        val mapped = mapLevel(v, oldMin, oldMax, newMin, newMax)
        val basis = "${oldMax}단계 중 ${v} → ${newMax}단계 중 ${mapped}"
        return if (mapped == v) {
            TransformOutcome(CompareStatus.SAME, saved, "${ValueFormat.levelText(v, newMin)} 유지 ($basis)")
        } else {
            TransformOutcome(CompareStatus.ADAPTED, SettingValue.Level(mapped), basis)
        }
    }

    /**
     * 0이 "꺼짐"인 단계형(열선 0~3)은 꺼짐을 유지하고, 켜진 단계 1..oldMax를 1..newMax로 비율 환산한다.
     * new = round(lo' + (v - lo) / (oldMax - lo) * (newMax - lo'))   (lo = 켜진 첫 단계)
     * 기획서 식 round(1 + (v-1)/(oldMax-1) * (newMax-1))을 최솟값이 1이 아닌 경우까지 일반화한 것.
     */
    fun mapLevel(v: Int, oldMin: Int, oldMax: Int, newMin: Int, newMax: Int): Int {
        if (v <= 0 && oldMin <= 0) return if (newMin <= 0) 0 else newMin // 꺼짐 유지
        val oldLo = maxOf(oldMin, 1)
        val newLo = maxOf(newMin, 1)
        if (newMax <= newLo) return newMax
        val ratio = if (oldMax <= oldLo) 1.0 else (v - oldLo).toDouble() / (oldMax - oldLo)
        val mapped = (newLo + ratio.coerceIn(0.0, 1.0) * (newMax - newLo)).roundToInt()
        return mapped.coerceIn(newLo, newMax)
    }

    // ---------- Number: 단위 환산, 클램프, 스텝 스냅 ----------
    fun number(saved: SettingValue.Number, target: CapabilityInfo): TransformOutcome {
        val targetUnit = target.unit ?: saved.unit
        var x = convert(saved.value, saved.unit, targetUnit)
        val unitChanged = saved.unit != null && targetUnit != null && saved.unit != targetUnit
        val min = target.min
        val max = target.max
        if (min == null || max == null) return unknown("이 차의 설정 범위를 얻지 못했어요")
        x = x.coerceIn(min, max)
        target.step?.takeIf { it > 0 }?.let { step -> x = (min + ((x - min) / step).roundToInt() * step).coerceIn(min, max) }
        x = (x * 10).roundToInt() / 10.0
        val proposed = SettingValue.Number(x, targetUnit)
        val changed = abs(x - saved.value) > 1e-6 || unitChanged
        return if (!changed) {
            TransformOutcome(CompareStatus.SAME, proposed, "${ValueFormat.format(proposed)} 유지")
        } else {
            val why = buildList {
                if (unitChanged) add("단위 환산")
                add("이 차 범위 ${ValueFormat.num(min)}~${ValueFormat.num(max)}")
                target.step?.let { add("${ValueFormat.num(it)} 단위") }
            }.joinToString(", ")
            TransformOutcome(CompareStatus.ADAPTED, proposed,
                "${ValueFormat.format(saved)} → ${ValueFormat.format(proposed)} ($why)")
        }
    }

    fun convert(value: Double, from: TempUnit?, to: TempUnit?): Double = when {
        from == null || to == null || from == to -> value
        from == TempUnit.CELSIUS -> value * 9 / 5 + 32
        else -> (value - 32) * 5 / 9
    }

    // ---------- Enum: 값 집합 비교 (대체 매핑 없음) ----------
    fun enumValue(saved: SettingValue.Enum, target: CapabilityInfo): TransformOutcome {
        val allowed = target.allowed ?: return unknown("이 차에서 고를 수 있는 값 목록을 얻지 못했어요")
        return if (allowed.any { it.equals(saved.name, ignoreCase = true) }) {
            TransformOutcome(CompareStatus.SAME, saved, "같은 값으로 유지")
        } else {
            TransformOutcome(CompareStatus.UNSUPPORTED, null, "이 차에는 같은 값이 없어요 (가능: ${allowed.joinToString()})")
        }
    }

    // ---------- Color: 지원 색 중 RGB 거리 최소 ----------
    fun color(saved: SettingValue.Color, target: CapabilityInfo): TransformOutcome {
        val palette = target.palette?.takeIf { it.isNotEmpty() } ?: return unknown("이 차의 지원 색 목록을 얻지 못했어요")
        val nearest = palette.minBy { rgbDistance(it, saved.rgb) }
        val proposed = SettingValue.Color(nearest, saved.brightness)
        return if (nearest == saved.rgb) {
            TransformOutcome(CompareStatus.SAME, proposed, "같은 색 유지")
        } else {
            TransformOutcome(CompareStatus.ADAPTED, proposed,
                "${ValueFormat.hex(saved.rgb)} → ${ValueFormat.hex(nearest)} (지원 색 ${palette.size}개 중 가장 가까운 색)")
        }
    }

    fun rgbDistance(a: Int, b: Int): Int {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    // ---------- Position: 참고용 비율만 ----------
    fun position(saved: SettingValue.Position, old: CapabilityInfo?, target: CapabilityInfo): TransformOutcome {
        val oMin = old?.min; val oMax = old?.max
        if (oMin == null || oMax == null || oMax <= oMin) {
            return TransformOutcome(CompareStatus.REFERENCE_ONLY, null, "참고용: 저장 당시 범위를 몰라 비율을 낼 수 없어요")
        }
        val ratio = ((saved.value - oMin) / (oMax - oMin)).coerceIn(0.0, 1.0)
        val pct = (ratio * 100).roundToInt()
        val nMin = target.min; val nMax = target.max
        return if (nMin != null && nMax != null && nMax > nMin) {
            val v = (nMin + ratio * (nMax - nMin)).roundToInt()
            TransformOutcome(CompareStatus.REFERENCE_ONLY, SettingValue.Position(v), "참고용: 범위의 ${pct}% 위치 (자동 적용 안 함)")
        } else {
            TransformOutcome(CompareStatus.REFERENCE_ONLY, null, "참고용: 내 차 범위의 ${pct}% 위치 (자동 적용 안 함)")
        }
    }

    private fun unknown(note: String) = TransformOutcome(CompareStatus.UNKNOWN, null, note)
}
