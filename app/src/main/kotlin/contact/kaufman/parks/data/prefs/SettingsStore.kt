package contact.kaufman.parks.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import contact.kaufman.parks.domain.Park
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class TemperatureUnit { FAHRENHEIT, CELSIUS }

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class Settings(
    val dynamicColor: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    /** Parks the dashboard hides. Empty means show all seven. */
    val hiddenParks: Set<Park> = emptySet(),
) {
    fun visibleParks(): List<Park> = Park.entries.filterNot { it in hiddenParks }
}

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            themeMode = prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            temperatureUnit = prefs[TEMPERATURE_UNIT]
                ?.let { runCatching { TemperatureUnit.valueOf(it) }.getOrNull() }
                ?: TemperatureUnit.FAHRENHEIT,
            // An unknown name means the park catalog changed under a stored preference;
            // dropping it silently is right, since the alternative is a crash on launch.
            hiddenParks = prefs[HIDDEN_PARKS].orEmpty()
                .mapNotNull { name -> Park.entries.firstOrNull { it.name == name } }
                .toSet(),
        )
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { it[TEMPERATURE_UNIT] = unit.name }
    }

    suspend fun setParkVisible(park: Park, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val hidden = prefs[HIDDEN_PARKS].orEmpty().toMutableSet()
            if (visible) hidden.remove(park.name) else hidden.add(park.name)
            // Hiding every park would leave a blank dashboard with no way back except
            // Settings, so the last visible one cannot be switched off.
            if (hidden.size >= Park.entries.size) return@edit
            prefs[HIDDEN_PARKS] = hidden
        }
    }

    private companion object {
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val HIDDEN_PARKS = stringSetPreferencesKey("hidden_parks")
    }
}
