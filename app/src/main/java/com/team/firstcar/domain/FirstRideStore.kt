package com.team.firstcar.domain

import android.content.Context

/**
 * 첫 탑승 판단 (F5). 차량 키 기준으로 "이미 본 차"를 기록한다.
 * Pleos의 차량 프로필(사용자)은 Android 사용자 단위로 분리되므로 앱 저장소도 사용자별로 분리된다.
 */
class FirstRideStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("first_ride", Context.MODE_PRIVATE)

    fun isFirstRide(carKey: String): Boolean = carKey !in seen()

    fun markSeen(carKey: String) {
        prefs.edit().putStringSet(KEY, seen() + carKey).apply()
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun seen(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    private companion object { const val KEY = "seen_cars" }
}
