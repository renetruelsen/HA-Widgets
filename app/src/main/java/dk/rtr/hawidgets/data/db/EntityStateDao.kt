package dk.rtr.hawidgets.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityStateDao {
    @Query("SELECT * FROM entity_state WHERE entityId = :entityId")
    suspend fun get(entityId: String): EntityStateEntity?

    @Query("SELECT * FROM entity_state WHERE entityId = :entityId")
    fun observe(entityId: String): Flow<EntityStateEntity?>

    @Upsert
    suspend fun upsert(entity: EntityStateEntity)

    @Query("DELETE FROM entity_state WHERE entityId = :entityId")
    suspend fun delete(entityId: String)
}
