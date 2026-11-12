package com.team.firstcar

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.team.firstcar.ui.AppScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        r.forEach { (name, granted) -> Log.d("FirstCar", "permission $name granted=$granted") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppScreen(vm) }
        requestMissingPermissions()
    }

    /**
     * Manifest에 선언한 차량 퍼미션 중 아직 허용되지 않은 것을 요청한다.
     * 조회 퍼미션은 설치 때 허용되지만, 세팅 적용에 쓰는 제어(CONTROL_*) 퍼미션은 실행 중 허용이 필요할 수 있다.
     * 이미 허용돼 있으면 아무 일도 일어나지 않는다.
     */
    private fun requestMissingPermissions() {
        val declared = runCatching {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        }.getOrNull().orEmpty()
        val missing = declared.filter {
            it.startsWith("pleos.car.permission.") && checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        Log.d("FirstCar", "permissions missing=$missing")
        if (missing.isNotEmpty()) askPermissions.launch(missing.toTypedArray())
    }
}
