package dk.akait.hawidgets.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface EntityWidgetDao {
    @Upsert
    suspend fun upsert(config: EntityWidgetEntity)

    @Query("SELECT * FROM entity_widget WHERE appWidgetId = :id")
    suspend fun get(id: Int): EntityWidgetEntity?

    @Query("SELECT DISTINCT entityId FROM entity_widget")
    suspend fun allEntityIds(): List<String>

    @Query("DELETE FROM entity_widget WHERE appWidgetId = :id")
    suspend fun delete(id: Int)
}
