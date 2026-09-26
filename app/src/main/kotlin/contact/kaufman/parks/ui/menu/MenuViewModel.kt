package contact.kaufman.parks.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.menu.MenuRepository
import contact.kaufman.parks.data.menu.MenuResult
import contact.kaufman.parks.domain.Menus
import contact.kaufman.parks.domain.RestaurantMenu
import contact.kaufman.parks.ui.components.ParkTimeZone
import contact.kaufman.parks.ui.navigation.MenuKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class MenuUiState(
    val menu: RestaurantMenu? = null,
    val selectedPeriod: Int = 0,
    /** Nothing to show yet — no copy on the phone and the first fetch still out. */
    val isLoading: Boolean = true,
    /** Only for a pull the person made; a quiet background check draws no spinner. */
    val isRefreshing: Boolean = false,
    /** Disney answered, and has no menu under any address the app could work out. */
    val noMenu: Boolean = false,
    /** A refresh that failed. With a menu on screen it reads as "this may be out of date". */
    val error: String? = null,
)

@HiltViewModel(assistedFactory = MenuViewModel.Factory::class)
class MenuViewModel @AssistedInject constructor(
    @Assisted private val key: MenuKey,
    private val repository: MenuRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: MenuKey): MenuViewModel
    }

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    private var inFlight: Job? = null

    init {
        viewModelScope.launch {
            // The phone's copy first, instantly — this is what makes a menu open in a park
            // with one bar of signal. Then Disney, but only if that copy is a day old.
            val cached = repository.cached(key.restaurantId, key.name)
            if (cached != null) show(cached)
            if (cached == null || !repository.isFresh(cached)) load(force = false)
            else _state.update { it.copy(isLoading = false) }
        }
    }

    /** Pull-to-refresh: ask Disney now, whatever the age of the copy on screen. */
    fun refresh() = load(force = true)

    fun selectPeriod(index: Int) = _state.update { it.copy(selectedPeriod = index) }

    private fun load(force: Boolean) {
        if (inFlight?.isActive == true) return
        inFlight = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = force, error = null) }
            when (val result = repository.refresh(key.restaurantId, key.name, force = force)) {
                is MenuResult.Found -> show(result.menu)
                MenuResult.NoMenu -> _state.update {
                    it.copy(menu = null, noMenu = true, isLoading = false, isRefreshing = false)
                }
                // Whatever is already on screen stays there. A failed check is a reason to
                // say the menu might be out of date, never a reason to take it away.
                is MenuResult.Failed -> _state.update {
                    it.copy(error = result.message, isLoading = false, isRefreshing = false)
                }
            }
        }
    }

    private fun show(menu: RestaurantMenu) = _state.update { current ->
        val names = menu.periods.map { it.name }
        // A refresh keeps you on the meal you were reading, if it still exists.
        val keep = current.menu?.periods?.getOrNull(current.selectedPeriod)?.name
            ?.let { names.indexOf(it) }?.takeIf { it >= 0 }
        val hour = Clock.System.now().toLocalDateTime(ParkTimeZone).hour
        current.copy(
            menu = menu,
            selectedPeriod = keep ?: Menus.defaultPeriodIndex(names, hour),
            isLoading = false,
            isRefreshing = false,
            noMenu = false,
        )
    }
}
