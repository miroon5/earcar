package com.team.firstcar

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.team.firstcar.domain.FirstRideStore
import com.team.firstcar.settings.compare.Comparator
import com.team.firstcar.settings.model.CarIdentity
import com.team.firstcar.settings.model.CompareResult
import com.team.firstcar.settings.model.CompareStatus
import com.team.firstcar.settings.model.ProfileSnapshot
import com.team.firstcar.settings.model.SavedItem
import com.team.firstcar.settings.reader.ApplyResult
import com.team.firstcar.settings.reader.ItemReadResult
import com.team.firstcar.settings.reader.SettingsWriter
import com.team.firstcar.settings.model.SettingType
import com.team.firstcar.settings.registry.SettingRegistry
import com.team.firstcar.settings.reader.SettingsReader
import com.team.firstcar.settings.source.CapabilityProvider
import com.team.firstcar.settings.source.DemoCapabilityProvider
import com.team.firstcar.settings.source.RealCapabilityProvider
import com.team.firstcar.settings.store.ProfileStore
import com.team.firstcar.vehicle.DrivingStateMonitor
import com.team.firstcar.vehicle.VehicleSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, SAVING, SUMMARY, DETAIL, APPLY, DIAGNOSTICS }

data class TargetOption(val id: String, val title: String, val description: String, val isDemo: Boolean)

data class UiState(
    val screen: Screen = Screen.HOME,
    val vehicleError: String? = null,
    val currentCar: CarIdentity? = null,
    val targets: List<TargetOption> = emptyList(),
    val targetId: String = RealCapabilityProvider.REAL_ID,
    val profile: ProfileSnapshot? = null,
    val saving: Boolean = false,
    val saveResult: List<ItemReadResult>? = null,
    val comparing: Boolean = false,
    val compare: List<CompareResult>? = null,
    val compareCar: CarIdentity? = null,
    val detailFilter: CompareStatus? = null, // 항목별 화면을 열 때 먼저 보여 줄 상태 (null이면 전체)
    val applying: Boolean = false,
    val applyResult: List<ApplyResult>? = null,
    val firstRidePrompt: Boolean = false,
    val diagnosing: Boolean = false,
    val diagnostics: List<ItemReadResult> = emptyList(),
    val catalog: Map<String, List<String>> = emptyMap(),
    val message: String? = null,
    val reduceMotion: Boolean = false,
) {
    val target: TargetOption? get() = targets.firstOrNull { it.id == targetId }
    val isDemoTarget: Boolean get() = target?.isDemo == true
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val driving = DrivingStateMonitor(app)
    private val session = VehicleSession(app)
    private val reader = SettingsReader(session)
    private val writer = SettingsWriter(session)
    private val store = ProfileStore(app)
    private val firstRide = FirstRideStore(app)

    private val real = RealCapabilityProvider(reader, writer)
    private val providers: List<CapabilityProvider> = listOf(real) + DemoCapabilityProvider.loadAll(app)

    private val _ui = MutableStateFlow(UiState(
        targets = providers.map { TargetOption(it.id, it.title, it.description, it.isDemo) },
        profile = store.load(),
        reduceMotion = runCatching {
            Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false),
    ))
    val ui: StateFlow<UiState> = _ui

    private var job: Job? = null

    init {
        driving.start()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { session.initialize() }
            _ui.update { it.copy(vehicleError = if (ok) null else session.initError) }
            refreshCurrentCar()
        }
        // 주행이 시작되면 진행 중 작업을 멈추고 모든 화면을 닫는다 (한 줄 상태만 남김)
        viewModelScope.launch {
            driving.state.collect { d -> if (d.restricted) onDrivingStarted() }
        }
    }

    private fun provider(): CapabilityProvider = providers.firstOrNull { it.id == _ui.value.targetId } ?: real

    /** 현재 차(또는 시연용 가상 차) 확인 후 첫 탑승이면 카드 표시 (F5) */
    private suspend fun refreshCurrentCar() {
        val car = withContext(Dispatchers.Default) { provider().identity() }
        val profile = _ui.value.profile
        val prompt = profile != null && profile.sourceCar.key != car.key && firstRide.isFirstRide(car.key)
        _ui.update { it.copy(currentCar = car, firstRidePrompt = prompt) }
    }

    private fun canAct(): Boolean = !driving.state.value.restricted

    // ---------- F1 저장 ----------
    fun save() {
        if (!canAct() || _ui.value.saving) return
        job?.cancel()
        _ui.update { it.copy(screen = Screen.SAVING, saving = true, saveResult = null, message = null) }
        job = viewModelScope.launch {
            val results = withContext(Dispatchers.Default) { reader.readAll() }
            val car = withContext(Dispatchers.Default) { real.identity() }
            val snapshot = ProfileSnapshot(
                savedAt = System.currentTimeMillis(),
                sourceCar = car,
                items = results.map { SavedItem(it.itemId, it.value, it.capability, it.failure) },
            )
            if (snapshot.readCount > 0) {
                runCatching { store.save(snapshot) }
                firstRide.markSeen(car.key)
                _ui.update { it.copy(saving = false, saveResult = results, profile = snapshot) }
            } else {
                // 하나도 못 읽었으면 기존 저장본을 덮어쓰지 않는다
                _ui.update { it.copy(saving = false, saveResult = results) }
            }
        }
    }

    // ---------- F2, F3 비교 ----------
    fun compare() = startCompare(thenApply = false)

    /** 첫 탑승 카드의 "적용하기": 이 차에 맞게 비교한 뒤 곧바로 적용까지 한다 */
    fun compareAndApply() = startCompare(thenApply = true)

    private fun startCompare(thenApply: Boolean) {
        val profile = _ui.value.profile ?: return
        if (!canAct() || _ui.value.comparing) return
        job?.cancel()
        _ui.update { it.copy(screen = Screen.SUMMARY, comparing = true, compare = null, firstRidePrompt = false) }
        job = viewModelScope.launch {
            val p = provider()
            val (car, results) = withContext(Dispatchers.Default) {
                val car = p.identity()
                car to Comparator.compare(profile, p.capabilities())
            }
            firstRide.markSeen(car.key)
            _ui.update { it.copy(comparing = false, compare = results, compareCar = car) }
            if (thenApply) { job = null; apply() }
        }
    }

    // ---------- F4 적용 ----------
    /**
     * 비교 결과에서 이 차에 맞게 바꾼 값(그대로, 비슷하게)을 실제 차에 설정한다.
     * 실제 차량은 SDK로 설정을 바꾸고, 파일로 정의한 차량(KONA, GRANDEUR)은 앱 안의 차량 상태를 바꾼다. 시트 위치는 적용하지 않는다 (직접 조절).
     */
    fun apply() {
        val results = _ui.value.compare ?: return
        if (!canAct() || _ui.value.applying) return
        val targets = results.mapNotNull { r ->
            val item = SettingRegistry.get(r.itemId) ?: return@mapNotNull null
            val value = r.proposed ?: return@mapNotNull null
            if (item.type == SettingType.POSITION) return@mapNotNull null
            if (r.status != CompareStatus.SAME && r.status != CompareStatus.ADAPTED) return@mapNotNull null
            item to value
        }
        job?.cancel()
        _ui.update { it.copy(screen = Screen.APPLY, applying = true, applyResult = null) }
        job = viewModelScope.launch {
            // 적용 전 값을 먼저 읽어 두고, 결과에 "이전 값"으로 붙인다
            val p = provider()
            val out = withContext(Dispatchers.Default) {
                val before = p.currentValues()
                p.apply(targets).map { it.copy(before = before[it.itemId]) }
            }
            _ui.update { it.copy(applying = false, applyResult = out) }
        }
    }

    fun dismissFirstRide() {
        _ui.value.currentCar?.let { firstRide.markSeen(it.key) }
        _ui.update { it.copy(firstRidePrompt = false) }
    }

    // ---------- 화면 이동 ----------
    fun open(screen: Screen) { if (canAct()) _ui.update { it.copy(screen = screen) } }

    /** 항목별 결과를 연다. [filter]를 주면 그 상태만 먼저 보여 준다 */
    fun openDetail(filter: CompareStatus? = null) {
        if (canAct()) _ui.update { it.copy(screen = Screen.DETAIL, detailFilter = filter) }
    }

    fun back() {
        _ui.update {
            it.copy(screen = when (it.screen) {
                Screen.DETAIL, Screen.APPLY -> Screen.SUMMARY
                else -> Screen.HOME
            })
        }
    }

    // ---------- 진단 ----------
    fun selectTarget(id: String) {
        _ui.update { it.copy(targetId = id, compare = null, compareCar = null) }
        viewModelScope.launch { refreshCurrentCar() }
    }

    fun runDiagnostics() {
        if (!canAct() || _ui.value.diagnosing) return
        _ui.update { it.copy(diagnosing = true) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) { reader.readAll() }
            _ui.update { it.copy(diagnosing = false, diagnostics = r) }
        }
    }

    fun loadCatalog() {
        viewModelScope.launch {
            val c = withContext(Dispatchers.Default) { reader.catalog() }
            _ui.update { it.copy(catalog = c) }
        }
    }

    /** F6 일부: 내 데이터 지우기 (공유 차량에서 내리기 전) */
    fun deleteMyData() {
        store.clear()
        firstRide.clear()
        _ui.update { it.copy(profile = null, compare = null, compareCar = null, saveResult = null,
            firstRidePrompt = false, message = "이 차에 저장된 내 세팅을 모두 지웠어요.") }
    }

    fun resetFirstRide() {
        firstRide.clear()
        viewModelScope.launch { refreshCurrentCar() }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    private fun onDrivingStarted() {
        val wasBusy = _ui.value.saving || _ui.value.comparing || _ui.value.diagnosing || _ui.value.applying
        job?.cancel()
        _ui.update {
            it.copy(screen = Screen.HOME, saving = false, comparing = false, diagnosing = false, applying = false,
                message = if (wasBusy) "주행이 시작되어 작업을 멈췄어요. 정차 후 다시 해 주세요." else it.message)
        }
    }

    override fun onCleared() {
        job?.cancel()
        session.release()
        driving.stop()
    }
}
