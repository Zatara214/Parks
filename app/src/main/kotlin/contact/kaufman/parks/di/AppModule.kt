package contact.kaufman.parks.di

import android.content.Context
import androidx.room.Room
import contact.kaufman.parks.BuildConfig
import contact.kaufman.parks.data.db.DailyWaitAverageDao
import contact.kaufman.parks.data.db.ParkCrowdDao
import contact.kaufman.parks.data.db.ParkingDao
import contact.kaufman.parks.data.db.ParkSightingDao
import contact.kaufman.parks.data.db.ParksDatabase
import contact.kaufman.parks.data.db.WaitSampleDao
import contact.kaufman.parks.data.menu.FileMenuStore
import contact.kaufman.parks.data.menu.MenuStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
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
        // themeparks.wiki is volunteer-run; identify the client honestly so they can see
        // who is calling and get in touch if it ever misbehaves.
        //
        // The UserAgent plugin rather than a defaultRequest header, and the difference is
        // not cosmetic: defaultRequest *appends* its value even when a request has set its
        // own, so the Disney menu request went out claiming to be a browser and Parks at
        // once. The plugin steps aside for a request that names itself. Pinned by
        // MenuRepositoryTest.
        install(UserAgent) {
            agent = "Parks/${BuildConfig.VERSION_NAME} (github.com/Zatara214/Parks)"
        }
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ParksDatabase =
        Room.databaseBuilder(context, ParksDatabase::class.java, ParksDatabase.NAME)
            .addMigrations(ParksDatabase.MIGRATION_1_2)
            .build()

    @Provides fun waitSampleDao(db: ParksDatabase): WaitSampleDao = db.waitSamples()
    @Provides fun dailyAverageDao(db: ParksDatabase): DailyWaitAverageDao = db.dailyAverages()
    @Provides fun parkCrowdDao(db: ParksDatabase): ParkCrowdDao = db.parkCrowd()
    @Provides fun parkingDao(db: ParksDatabase): ParkingDao = db.parking()
    @Provides fun parkSightingDao(db: ParksDatabase): ParkSightingDao = db.parkSightings()

    /** Menus cache as files, not in Room — see [FileMenuStore] for why. */
    @Provides fun menuStore(store: FileMenuStore): MenuStore = store
}
