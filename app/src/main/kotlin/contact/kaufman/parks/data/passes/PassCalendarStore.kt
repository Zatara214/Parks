package contact.kaufman.parks.data.passes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The last pass calendar response this phone received, exactly as it arrived. The raw
 *  body is kept so a better reader later can re-read an old copy without a request. */
@Serializable
data class CachedPassCalendar(
    val fetchedAtEpochSeconds: Long,
    val body: String,
)

interface PassCalendarStore {
    suspend fun read(): CachedPassCalendar?
    suspend fun write(entry: CachedPassCalendar)
}

/** One file in the cache directory — the same reasoning as the menu cache: losing it
 *  costs one request, so it is not worth a Room table and a migration. */
@Singleton
class FilePassCalendarStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : PassCalendarStore {

    private val file = File(context.cacheDir, "pass-calendar.json")

    override suspend fun read(): CachedPassCalendar? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<CachedPassCalendar>(file.readText()) }.getOrNull()
    }

    override suspend fun write(entry: CachedPassCalendar) = withContext(Dispatchers.IO) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(CachedPassCalendar.serializer(), entry))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        Unit
    }
}
