package contact.kaufman.parks.di

import android.content.Context
import androidx.room.Room
import contact.kaufman.parks.BuildConfig
import contact.kaufman.parks.data.db.DailyWaitAverageDao
import contact.kaufman.parks.data.db.ParkCrowdDao
import contact.kaufman.parks.data.db.ParkingDao
import contact.kaufman.parks.data.db.ParksDatabase
import contact.kaufman.parks.data.db.WaitSampleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true // upstream adds fields without warning
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun httpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
        if (BuildConfig.DEBUG) {
            install(Logging) { level = LogLevel.INFO }
        }
        defaultRequest {
            // themeparks.wiki is volunteer-run; identify the client honestly so they can
            // see who is calling and get in touch if it ever misbehaves.
            header("User-Agent", "Parks/${BuildConfig.VERSION_NAME} (github.com/Zatara214/Parks)")
        }
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ParksDatabase =
        Room.databaseBuilder(context, ParksDatabase::class.java, ParksDatabase.NAME).build()

    @Provides fun waitSampleDao(db: ParksDatabase): WaitSampleDao = db.waitSamples()
    @Provides fun dailyAverageDao(db: ParksDatabase): DailyWaitAverageDao = db.dailyAverages()
    @Provides fun parkCrowdDao(db: ParksDatabase): ParkCrowdDao = db.parkCrowd()
    @Provides fun parkingDao(db: ParksDatabase): ParkingDao = db.parking()
}
