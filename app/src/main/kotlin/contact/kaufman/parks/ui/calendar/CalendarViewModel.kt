package contact.kaufman.parks.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.prefs.SettingsStore
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.data.repo.ParksRepository
import contact.kaufman.parks.domain.CalendarDay
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkCalendar
import contact.kaufman.parks.domain.ParkKind
import contact.kaufman.parks.domain.Resort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import javax.inject.Inject

data class CalendarUiState(
    val resort: Resort = Resort.WALT_DISNEY_WORLD,
    /** Resorts with at least one park shown on the dashboard. One entry draws no chooser. */
    val resorts: List<Resort> = emptyList(),
    val today: LocalDate? = null,
    val days: List<CalendarDay> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

/**
 * The coming days in one place: each park's published hours, its extra sessions and
 * ticketed nights, and the resort's weather for the day.
 *
 * Everything here comes from requests the app already makes — the park schedules the
 * dashboard reads, and one weather request per resort — so opening it right after the
 * dashboard usually costs a single outlook request and nothing else.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: ParksRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val temperatureUnit: StateFlow<TemperatureUnit> = settings.settings
        .map { it.temperatureUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemperatureUnit.FAHRENHEIT)

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        viewModelScope.launch {
            // The same parks the dashboard shows. Someone who has hidden Epic Universe does
            // not want its hours on every day of the calendar either.
            val resorts = parksShown().map { it.resort }.distinct()
            val start = if (Resort.WALT_DISNEY_WORLD in resorts) Resort.WALT_DISNEY_WORLD else resorts.firstOrNull()
            _state.update { it.copy(resorts = resorts, resort = start ?: Resort.WALT_DISNEY_WORLD) }
            load(force = false)
        }
    }

    fun selectResort(resort: Resort) {
        if (resort == _state.value.resort) return
        _state.update { it.copy(resort = resort, days = emptyList(), isLoading = true) }
        load(force = false)
    }

    /** Pull-to-refresh: ask for fresh hours and weather rather than the recent copies. */
    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        // Switching resort mid-load must not let the old resort's answer land on the new one.
        loading?.cancel()
        val resort = _state.value.resort
        loading = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = force) }
            val parks = parksShown().filter { it.resort == resort }
            val today = repository.today()
            val dates = (0 until DAYS).map { today.plus(it, DateTimeUnit.DAY) }

            val (schedules, outlook) = coroutineScope {
                val schedules = parks.map { park -> async { park to repository.schedule(park, force) } }
                val outlook = async { repository.outlook(resort, force) }
                schedules.awaitAll().toMap() to outlook.await()
            }

            if (_state.value.resort != resort) return@launch
            _state.update {
                it.copy(
                    today = today,
                    days = ParkCalendar.build(dates, schedules, outlook),
                    isLoading = false,
                    isRefreshing = false,
                )
            }
        }
    }

    /** Theme parks only: Disney Springs publishes no hours to put on a calendar. */
    private suspend fun parksShown(): List<Park> =
        settings.settings.first().visibleParks().filter { it.kind == ParkKind.THEME_PARK }

    private companion object {
        /** Matches the weather outlook, so every day on screen has both halves. */
        const val DAYS = 10
    }
}
