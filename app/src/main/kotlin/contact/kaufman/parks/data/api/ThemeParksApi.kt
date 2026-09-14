package contact.kaufman.parks.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject
import javax.inject.Singleton

/**
 * api.themeparks.wiki v1. No key, no account, no rate-limit gate — but it is a
 * volunteer-run service, so every call here is on-demand or cache-backed, never polled.
 */
@Singleton
class ThemeParksApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun live(entityId: String): LiveDataResponse =
        client.get("$BASE/entity/$entityId/live").body()

    suspend fun children(entityId: String): ChildrenResponse =
        client.get("$BASE/entity/$entityId/children").body()

    suspend fun schedule(entityId: String): ScheduleResponse =
        client.get("$BASE/entity/$entityId/schedule").body()

    private companion object {
        const val BASE = "https://api.themeparks.wiki/v1"
    }
}
