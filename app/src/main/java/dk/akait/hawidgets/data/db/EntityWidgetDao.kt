package dk.akait.hawidgets.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityWidgetDao {
    @Upsert
    suspend fun upsert(config: EntityWidgetEntity)

    @Query("SELECT * FROM entity_widget WHERE appWidgetId = :id")
    suspend fun get(id: Int): EntityWidgetEntity?

    @Query("SELECT * FROM entity_widget WHERE appWidgetId = :id")
    fun observe(id: Int): Flow<EntityWidgetEntity?>

    @Query("SELECT DISTINCT entityId FROM entity_widget")
    suspend fun allEntityIds(): List<String>

    /** Alle widgets (på tværs af typer) der viser samme entity — bruges til fan-out. */
    @Query("SELECT * FROM entity_widget WHERE entityId = :entityId")
    suspend fun widgetsForEntity(entityId: String): List<EntityWidgetEntity>

    @Query("DELETE FROM entity_widget WHERE appWidgetId = :id")
    suspend fun delete(id: Int)
}
