package science.credo.mobiledetector.database

import android.content.Context
import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnore
import ninja.sakib.pultusorm.annotations.AutoIncrement
import ninja.sakib.pultusorm.annotations.Ignore
import ninja.sakib.pultusorm.annotations.PrimaryKey
import ninja.sakib.pultusorm.core.PultusORM
import ninja.sakib.pultusorm.core.PultusORMCondition
import ninja.sakib.pultusorm.core.PultusORMUpdater
import org.jetbrains.anko.doAsync
import science.credo.mobiledetector.detection.Hit
import science.credo.mobiledetector.events.StatsEvent
import science.credo.mobiledetector.info.ConfigurationInfo
import science.credo.mobiledetector.info.IdentityInfo
import science.credo.mobiledetector.network.ServerInterface
import science.credo.mobiledetector.network.exceptions.ServerException
import science.credo.mobiledetector.network.messages.BaseDeviceInfoRequest
import science.credo.mobiledetector.network.messages.DetectionRequest
import science.credo.mobiledetector.network.messages.PingRequest
import java.util.*

/**
 * Database management class.
 *
 * This class is used to store both recently detected and already server-synchronized hits. It's trimmed after certain period of time.
 *
 * @property context Android context object.
 */
class DataManager private constructor(val context: Context) {
    public val mAppPath: String = context.getFilesDir().getAbsolutePath()

    public val mDbFileName = "cache.db"
    private val mDbSchema = "4"
    private val mDb = PultusORM(mDbFileName, mAppPath)

    companion object {
        const val TAG = "DataManager"
        const val TRIM_PERIOD_HITS_DAYS = 365
        const val TRIM_PERIOD_HITS = 1000 * 3600 * 24 * TRIM_PERIOD_HITS_DAYS

        fun getDefault(context: Context): DataManager {
            return DataManager(context)
        }
    }

    init {
        checkAndUpdateDbSchema()
    }

    fun closeDb() {
        //mDb.close()
    }

    /**
     * Checks schema version, if version differs it also updates hits database.
     *
     * @return DataManager object (this).
     */
    private fun checkAndUpdateDbSchema() {
        if (ConfigurationWrapper(context).dbSchema != mDbSchema) {
            mDb.drop(Hit())
            mDb.drop(PingRequest())
            ConfigurationWrapper(context).dbSchema = mDbSchema
        }
    }

    /**
     * Stores hit in Hits database.
     *
     * @param hit Hit object which will be saved.
     */
    fun storeHit(hit: Hit) {
        mDb.save(hit)
    }

    fun storePing(message: PingRequest) {
        mDb.save(message)
    }

    /**
     * Retrieves detected hits from the database.
     *
     * @return MutableList<Hit> list containing found Hit objects.
     */
    fun getHits(): MutableList<Hit> {
        return try {
            mDb.find(Hit()) as MutableList<Hit>
        } catch (e: NullPointerException) {
            LinkedList()
        }
    }

    /**
     * Returns count of detected hits.
     */
    fun getHitsCount(): Long {
        return mDb.count(Hit())
    }

}
