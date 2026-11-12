package com.team.firstcar.vehicle

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import com.team.firstcar.settings.registry.VehicleDomain
import kotlin.reflect.KClass

/**
 * Vehicle SDK 생명주기. ViewModel이 소유한다 (init에서 initialize, onCleared에서 release).
 * 도메인 객체는 vehicle.getXxx()로만 얻는다. getter 이름은 공식 샘플 VehicleApiExplorer와 같다 [검증].
 */
class VehicleSession(context: Context) {
    val vehicle = Vehicle(context.applicationContext)
    var initialized = false
        private set
    var initError: String? = null
        private set

    private val cache = mutableMapOf<VehicleDomain, Pair<KClass<*>, Any>>()

    fun initialize(): Boolean {
        if (initialized) return true
        return try {
            vehicle.initialize()
            initialized = true
            initError = null
            true
        } catch (e: Exception) {
            initError = "Vehicle 초기화 실패: ${e.message ?: e.javaClass.simpleName}"
            false
        }
    }

    /** 도메인 API 클래스와 인스턴스. 실패하면 null (그 도메인만 건너뛴다) */
    fun domain(d: VehicleDomain): Pair<KClass<*>, Any>? = synchronized(cache) {
        cache[d] ?: runCatching {
            val pair: Pair<KClass<*>, Any> = when (d) {
                VehicleDomain.HVAC -> ai.pleos.playground.vehicle.api.HVAC::class to vehicle.getHVAC()
                VehicleDomain.LIGHT -> ai.pleos.playground.vehicle.api.Light::class to vehicle.getLight()
                VehicleDomain.SEAT -> ai.pleos.playground.vehicle.api.Seat::class to vehicle.getSeat()
                VehicleDomain.STEERING_WHEEL -> ai.pleos.playground.vehicle.api.SteeringWheel::class to vehicle.getSteeringWheel()
                VehicleDomain.SIDE_MIRROR -> ai.pleos.playground.vehicle.api.SideMirror::class to vehicle.getSideMirror()
                VehicleDomain.DISPLAY -> ai.pleos.playground.vehicle.api.Display::class to vehicle.getDisplay()
                VehicleDomain.DRIVING_MODE -> ai.pleos.playground.vehicle.api.DrivingMode::class to vehicle.getDrivingMode()
                VehicleDomain.TOLL -> ai.pleos.playground.vehicle.api.Toll::class to vehicle.getToll()
                VehicleDomain.WINDOW -> ai.pleos.playground.vehicle.api.Window::class to vehicle.getWindow()
                VehicleDomain.CAR_INFO -> ai.pleos.playground.vehicle.api.CarInfo::class to vehicle.getCarInfo()
            }
            pair
        }.getOrNull()?.also { cache[d] = it }
    }

    fun release() {
        if (!initialized) return
        // 이 앱은 리스너를 등록하지 않는다 (저장은 구독이 아니라 1회 스냅샷). 해제할 리스너가 없다
        runCatching { vehicle.release() }
        initialized = false
        synchronized(cache) { cache.clear() }
    }
}
