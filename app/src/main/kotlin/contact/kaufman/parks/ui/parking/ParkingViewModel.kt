package contact.kaufman.parks.ui.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.db.ParkingRecordEntity
import contact.kaufman.parks.data.location.LocationProvider
import contact.kaufman.parks.data.repo.ParkingRepository
import contact.kaufman.parks.domain.Park
import contact.kaufman.parks.domain.ParkingAreas
import contact.kaufman.parks.domain.ParkingLots
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParkingFormState(
    val park: Park? = null,
    val lot: String = "",
    /** Universal garages only — the level is the first digit of the posted number. */
    val level: String = "",
    val row: String = "",
    val note: String = "",
    val saved: Boolean = false,
    /** The lot a GPS fix put you in, once one has been asked for. */
    val detected: ParkingAreas.Area? = null,
    val locating: Boolean = false,
    /** A fix was obtained and matched nothing — worth saying, rather than silence. */
    val detectionMissed: Boolean = false,
) {
    /** A spot with neither a lot nor a row records nothing useful. */
    val canSave: Boolean get() = park != null && (lot.isNotBlank() || row.isNotBlank())

    /** What the sign actually reads: Universal merges level and row into one number. */
    fun signRow(): String = when {
        level.isBlank() -> row.trim()
        row.isBlank() -> level.trim()
        else -> level.trim() + row.trim().padStart(2, '0')
    }

    /**
     * Take what a GPS fix worked out, without overwriting anything chosen by hand.
     *
     * An assist, never an authority. The person standing next to the car knows better than
     * a fix that may have drifted, so an existing choice always wins — having your own
     * answer silently replaced is worse than having to type it.
     *
     * Never touches the row or the level: rows are not mapped anywhere, and a garage level
     * is invisible to GPS through a concrete deck.
     */
    fun withDetected(area: ParkingAreas.Area?): ParkingFormState {
        val next = copy(locating = false, detected = area, detectionMissed = area == null)
        if (area == null) return next
        val park = this.park ?: area.park
        // Only offer the section when it belongs to the park now selected. If a different
        // park is already picked, the lot would be a name that park does not have.
        val lot = when {
            this.lot.isNotBlank() -> this.lot
            park != null && park == area.park -> area.lot.orEmpty()
            else -> ""
        }
        return next.copy(park = park, lot = lot)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParkingViewModel @Inject constructor(
    private val repository: ParkingRepository,
    private val location: LocationProvider,
) : ViewModel() {

    init {
        viewModelScope.launch { repository.expireStale() }
    }

    private val _form = MutableStateFlow(ParkingFormState())
    val form: StateFlow<ParkingFormState> = _form.asStateFlow()

    val active: StateFlow<ParkingRecordEntity?> = repository.active()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<ParkingRecordEntity>> = repository.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Rows already used in the selected lot, offered as one-tap chips. */
    val recentRows: StateFlow<List<String>> = _form
        .map { it.park to it.lot }
        .flatMapLatest { (park, lot) ->
            if (park == null || lot.isBlank()) flowOf(emptyList())
            else repository.recentRows(park, lot)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Fill in whatever a single GPS fix can settle: the park, and at Disney the named
     * section too. See [ParkingFormState.withDetected] for what it refuses to touch.
     *
     * Failing quietly is fine. Every field is still typeable, so a missed fix costs a
     * prefill and nothing else.
     */
    fun detectSpot() {
        if (_form.value.locating || !location.hasPermission()) return
        viewModelScope.launch {
            _form.update { it.copy(locating = true, detectionMissed = false) }
            val fix = location.current()
            _form.update { it.withDetected(fix?.let { f -> ParkingAreas.at(f.latitude, f.longitude) }) }
        }
    }

    fun setPark(park: Park?) = _form.update { current ->
        val next = park ?: current.park
        // Lot names do not carry across parks, so keep a stale one out of the field.
        if (next != current.park) {
            current.copy(park = next, lot = "", level = "", row = "", saved = false)
        } else {
            current.copy(park = next)
        }
    }

    fun setLot(value: String) = _form.update { it.copy(lot = value, saved = false) }

    fun setLevel(value: String) = _form.update { it.copy(level = value.filter(Char::isDigit), saved = false) }

    fun setRow(value: String) = _form.update { it.copy(row = value.filter(Char::isDigit), saved = false) }

    fun setNote(value: String) = _form.update { it.copy(note = value, saved = false) }

    fun save() {
        val form = _form.value
        val park = form.park ?: return
        if (!form.canSave) return
        viewModelScope.launch {
            repository.park(park = park, lot = form.lot, row = form.signRow(), note = form.note)
            _form.update { it.copy(lot = "", level = "", row = "", note = "", saved = true) }
        }
    }

    /** Leaving the park. The record stays in history; it just stops being the current one. */
    fun clearActive() {
        viewModelScope.launch { repository.clearActive() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun lotGroups(park: Park?) = park?.let(ParkingLots::groupsFor).orEmpty()

    fun hasLevels(park: Park?) = park?.let(ParkingLots::hasLevels) == true
}
