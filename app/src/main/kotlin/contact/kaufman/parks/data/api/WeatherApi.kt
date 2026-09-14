package contact.kaufman.parks.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open-Meteo: free, keyless, and it logs nothing tied to a user. Fetched on demand
 * only — you already have a weather app, this is just the park-side glance.
 */
@Singleton
class WeatherApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun forecast(latitude: Double, longitude: Double): WeatherResponse =
        client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,is_day")
            parameter("hourly", "temperature_2m,precipitation_probability,weather_code")
            parameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,sunrise,sunset")
            parameter("temperature_unit", "fahrenheit")
            parameter("wind_speed_unit", "mph")
            parameter("precipitation_unit", "inch")
            parameter("timezone", "America/New_York")
            parameter("forecast_days", 2)
        }.body()
}

@Serializable
data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    val current: CurrentWeatherDto? = null,
    val hourly: HourlyWeatherDto? = null,
    val daily: DailyWeatherDto? = null,
)

@Serializable
data class CurrentWeatherDto(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val feelsLike: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
)

@Serializable
data class HourlyWeatherDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
)

@Serializable
data class DailyWeatherDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val high: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val low: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList(),
)
