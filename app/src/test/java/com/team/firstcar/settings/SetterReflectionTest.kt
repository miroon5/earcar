package com.team.firstcar.settings

import com.team.firstcar.settings.model.SettingType
import com.team.firstcar.settings.model.SettingValue
import com.team.firstcar.settings.model.TempUnit
import com.team.firstcar.settings.reader.ReflectiveApi
import com.team.firstcar.settings.reader.SettingsWriter
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.registry.VehicleDomain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.reflect.KClass

/**
 * 적용(set…) 호출이 실제 Vehicle SDK 인터페이스 시그니처와 맞는지 JVM에서 확인한다.
 * SDK 인터페이스를 가짜 구현(Proxy)으로 받아, 우리 코드가 넘긴 인자를 기록하고 성공/실패 콜백을 부른다.
 */
class SetterReflectionTest {

    private fun domainClass(d: VehicleDomain): KClass<*> = when (d) {
        VehicleDomain.HVAC -> ai.pleos.playground.vehicle.api.HVAC::class
        VehicleDomain.LIGHT -> ai.pleos.playground.vehicle.api.Light::class
        VehicleDomain.SEAT -> ai.pleos.playground.vehicle.api.Seat::class
        VehicleDomain.STEERING_WHEEL -> ai.pleos.playground.vehicle.api.SteeringWheel::class
        VehicleDomain.SIDE_MIRROR -> ai.pleos.playground.vehicle.api.SideMirror::class
        VehicleDomain.DISPLAY -> ai.pleos.playground.vehicle.api.Display::class
        VehicleDomain.DRIVING_MODE -> ai.pleos.playground.vehicle.api.DrivingMode::class
        VehicleDomain.TOLL -> ai.pleos.playground.vehicle.api.Toll::class
        VehicleDomain.WINDOW -> ai.pleos.playground.vehicle.api.Window::class
        VehicleDomain.CAR_INFO -> ai.pleos.playground.vehicle.api.CarInfo::class
    }

    /** 호출을 기록하고, [fail]이면 마지막 콜백(실패)을, 아니면 첫 콜백(성공)을 부르는 가짜 도메인 */
    private fun fake(cls: KClass<*>, fail: Boolean, seen: MutableList<List<Any?>>): Any =
        Proxy.newProxyInstance(cls.java.classLoader, arrayOf(cls.java)) { _, _, args ->
            val a = args.orEmpty().toList()
            seen += a
            val callbacks = a.filter { it is Function<*> }
            val values = a.filterNot { it is Function<*> }
            val cb = if (fail) callbacks.last() else callbacks.first()
            @Suppress("UNCHECKED_CAST")
            when (cb) {
                is Function0<*> -> cb.invoke()
                is Function1<*, *> -> (cb as (Any?) -> Any?).invoke(if (fail) IllegalStateException("거부됨") else values.last())
                is Function2<*, *, *> -> (cb as (Any?, Any?) -> Any?).invoke(values.first(), values.last())
            }
            null
        }

    private fun sample(type: SettingType): SettingValue = when (type) {
        SettingType.SWITCH -> SettingValue.Switch(true)
        SettingType.LEVEL -> SettingValue.Level(2)
        SettingType.NUMBER -> SettingValue.Number(22.5, TempUnit.CELSIUS)
        SettingType.ENUM -> SettingValue.Enum("ON")
        SettingType.COLOR -> SettingValue.Color(0x2F80ED)
        SettingType.POSITION -> SettingValue.Position(10)
    }

    @Test
    fun everySetterInTheSdkAcceptsOurArguments() = runBlocking {
        val report = StringBuilder()
        val missing = mutableSetOf<String>()
        for (item in SettingRegistry.items.filter { it.type != SettingType.POSITION }) {
            val hit = item.locators.firstNotNullOfOrNull { loc ->
                val cls = domainClass(loc.domain)
                ReflectiveApi.resolveSetter(cls, loc.key)?.let { cls to it }
            }
            if (hit == null) { report.appendLine("${item.id}: 설정 메서드 없음"); missing += item.id; continue }
            val (cls, fn) = hit
            val value = sample(item.type)
            val seen = mutableListOf<List<Any?>>()
            val ok = ReflectiveApi.call(fake(cls, false, seen), fn, 2_000) { t -> SettingsWriter.toRaw(value, t) }
            val bad = ReflectiveApi.call(fake(cls, true, mutableListOf()), fn, 2_000) { t -> SettingsWriter.toRaw(value, t) }
            report.appendLine("${item.id}: ${fn.name} args=${seen.firstOrNull()?.filterNot { it is Function<*> }} ok=$ok bad=$bad")
            assertTrue("${item.id} 성공 콜백이 성공으로 처리돼야 함: $ok", ok is ReflectiveApi.Outcome.Success)
            assertTrue("${item.id} 실패 콜백이 실패로 처리돼야 함: $bad", bad is ReflectiveApi.Outcome.Failure)
        }
        println(report)
        // SDK 2.3.2에 설정 메서드가 없는 것은 온도 단위와 주행 모드뿐이다. 나머지는 전부 찾아야 한다
        assertEquals(report.toString(), setOf("display.tempUnit", "drivingMode"), missing)
    }

    @Test
    fun valuesConvertToSetterParameterTypes() {
        assertEquals(true, SettingsWriter.toRaw(SettingValue.Enum("RECIRCULATION"), Boolean::class))
        assertEquals(false, SettingsWriter.toRaw(SettingValue.Enum("OFF"), Boolean::class))
        assertEquals(1, SettingsWriter.toRaw(SettingValue.Switch(true), Int::class))
        assertEquals(22.5f, SettingsWriter.toRaw(SettingValue.Number(22.5), Float::class))
        assertEquals(3, SettingsWriter.toRaw(SettingValue.Level(3), Int::class))
    }
}
