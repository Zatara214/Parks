package contact.kaufman.parks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import contact.kaufman.parks.data.prefs.Settings
import contact.kaufman.parks.data.prefs.SettingsStore
import contact.kaufman.parks.data.prefs.TemperatureUnit
import contact.kaufman.parks.data.prefs.ThemeMode
import contact.kaufman.parks.domain.Park
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<Settings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { store.setDynamicColor(enabled) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }

    fun setTemperatureUnit(unit: TemperatureUnit) =
        viewModelScope.launch { store.setTemperatureUnit(unit) }

    fun setParkVisible(park: Park, visible: Boolean) =
        viewModelScope.launch { store.setParkVisible(park, visible) }
}
