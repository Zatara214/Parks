package contact.kaufman.parks.data.menu

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last menu response this phone received for one restaurant, exactly as it arrived.
 *
 * The raw body is kept rather than the parsed menu so a better parser later can re-read
 * old copies, and [slug] is kept so the next refresh asks the right address first instead
 * of guessing again.
 */
@Serializable
data class CachedMenu(
    val restaurantId: String,
    val slug: String,
    val fetchedAtEpochSeconds: Long,
    val body: String,
)

interface MenuStore {
    suspend fun read(restaurantId: String): CachedMenu?
    suspend fun write(entry: CachedMenu)
    suspend fun delete(restaurantId: String)
}

/**
 * One small JSON file per restaurant in the app's **cache** directory, not in Room.
 *
 * A cache is what this is: Android may clear it under storage pressure, and losing it costs
 * one request the next time a menu is opened. Keeping it out of Room means no schema
 * change and no migration — which, for data this easy to fetch again, would be all risk
 * and no benefit next to the parking and wait history that database guards.
 */
@Singleton
class FileMenuStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : MenuStore {

    private val directory = File(context.cacheDir, "menus")

    override suspend fun read(restaurantId: String): CachedMenu? = withContext(Dispatchers.IO) {
        val file = fileFor(restaurantId)
        if (!file.exists()) return@withContext null
        // An unreadable file is a cache miss, never an error: it gets refetched.
        runCatching { json.decodeFromString<CachedMenu>(file.readText()) }.getOrNull()
    }

    override suspend fun write(entry: CachedMenu) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = fileFor(entry.restaurantId)
        // Written beside the target and renamed over it, so a crash mid-write leaves the
        // previous copy rather than half a file.
        val temp = File(directory, "${target.name}.tmp")
        temp.writeText(json.encodeToString(CachedMenu.serializer(), entry))
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
        Unit
    }

    override suspend fun delete(restaurantId: String) = withContext(Dispatchers.IO) {
        fileFor(restaurantId).delete()
        Unit
    }

    /** Entity ids are UUIDs, but nothing about a file name should trust upstream. */
    private fun fileFor(restaurantId: String) =
        File(directory, restaurantId.filter { it.isLetterOrDigit() || it == '-' } + ".json")
}
