package info.nightscout.database.impl.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.TABLE_DEVICE_STATUS
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single

@Suppress("FunctionName")
@Dao
internal interface DeviceStatusDao {

    @Insert
    fun insert(entry: DeviceStatus): Long

    @Update
    fun update(entry: DeviceStatus)

    @Query("SELECT * FROM $TABLE_DEVICE_STATUS WHERE id = :id")
    fun findById(id: Long): DeviceStatus?

    @Query("DELETE FROM $TABLE_DEVICE_STATUS")
    fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_DEVICE_STATUS WHERE timestamp < :than")
    fun deleteOlderThan(than: Long): Int

    @Query("SELECT id FROM $TABLE_DEVICE_STATUS ORDER BY id DESC limit 1")
    fun getLastId(): Long?

    @Query("DELETE FROM $TABLE_DEVICE_STATUS WHERE id NOT IN (SELECT MAX(id) FROM $TABLE_DEVICE_STATUS)")
    fun deleteAllEntriesExceptLast()

    @Query("SELECT * FROM $TABLE_DEVICE_STATUS WHERE nightscoutId = :nsId")
    fun findByNSId(nsId: String): DeviceStatus?

    // This query will be used with v3 to get all changed records
    @Query("SELECT * FROM $TABLE_DEVICE_STATUS WHERE id > :id AND nightscoutId IS NULL ORDER BY id ASC")
    fun getModifiedFrom(id: Long): Single<List<DeviceStatus>>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_DEVICE_STATUS WHERE id > :id AND nightscoutId IS NULL ORDER BY id ASC limit 1")
    fun getNextModifiedOrNewAfter(id: Long): Maybe<DeviceStatus>
}