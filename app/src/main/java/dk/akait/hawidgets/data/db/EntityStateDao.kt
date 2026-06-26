package dk.akait.hawidgets.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface EntityStateDao {
    @Query("SELECT * FROM entity_state WHERE entityId = :entityId")
    suspend fun get(entityId: String): EntityStateEntity?

    @Upsert
    suspend fun upsert(entity: EntityStateEntity)

    @Query("DELETE FROM entity_state WHERE entityId = :entityId")
    suspend fun delete(entityId: String)
}
