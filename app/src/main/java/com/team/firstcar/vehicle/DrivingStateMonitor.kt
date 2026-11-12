package com.team.firstcar.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 규제 상태. UI는 [restricted]만 보고 "한 줄 상태"와 "정차 중 화면"을 바꾼다.
 * 이 앱은 차속, 기어가 필요 없어서 CarPropertyManager(CAR_SPEED 퍼미션)는 쓰지 않는다.
 */
data class DrivingState(
    val restricted: Boolean = false,   // 주행 규제 활성 (주행 중)
    val noSetup: Boolean = false,      // 차량 설정 변경 금지
    val limitContent: Boolean = false, // 긴 목록, 긴 텍스트 축약
    val connected: Boolean = false,    // Car 서비스 연결 여부 (진단용)
)

class DrivingStateMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(DrivingState())
    val state: StateFlow<DrivingState> = _state

    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null

    fun start() {
        if (car != null) return
        val c = runCatching { Car.createCar(appContext) }.getOrNull() ?: return
        car = c
        uxManager = (runCatching { c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) }.getOrNull()
            as? CarUxRestrictionsManager)?.also { m ->
            _state.value = _state.value.copy(connected = true)
            runCatching { m.currentCarUxRestrictions }.getOrNull()?.let(::applyRestrictions)
            runCatching { m.registerListener { applyRestrictions(it) } }
        }
    }

    private fun applyRestrictions(r: CarUxRestrictions) {
        val restricted = r.isRequiresDistractionOptimization
        val active = r.activeRestrictions
        fun has(flag: Int) = restricted && (active and flag) != 0
        _state.value = _state.value.copy(
            restricted = restricted,
            noSetup = has(CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP),
            limitContent = has(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT),
        )
    }

    fun stop() {
        runCatching { uxManager?.unregisterListener() }
        runCatching { car?.disconnect() }
        uxManager = null; car = null
    }
}
