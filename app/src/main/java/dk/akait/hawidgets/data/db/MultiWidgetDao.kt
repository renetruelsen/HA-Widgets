package dk.akait.hawidgets.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MultiWidgetDao {
    @Upsert
    suspend fun upsert(config: MultiWidgetEntity)

    @Query("SELECT * FROM multi_widget WHERE appWidgetId = :id")
    suspend fun get(id: Int): MultiWidgetEntity?

    @Query("SELECT * FROM multi_widget WHERE appWidgetId = :id")
    fun observe(id: Int): Flow<MultiWidgetEntity?>

    @Query("DELETE FROM multi_widget WHERE appWidgetId = :id")
    suspend fun delete(id: Int)

    @Upsert
    suspend fun upsertSlot(slot: MultiWidgetSlotEntity)

    @Query("SELECT * FROM multi_widget_slot WHERE appWidgetId = :id ORDER BY slotIndex ASC")
    suspend fun getSlots(id: Int): List<MultiWidgetSlotEntity>

    @Query("SELECT * FROM multi_widget_slot WHERE appWidgetId = :id ORDER BY slotIndex ASC")
    fun observeSlots(id: Int): Flow<List<MultiWidgetSlotEntity>>

    @Query("DELETE FROM multi_widget_slot WHERE appWidgetId = :id AND slotIndex = :slotIndex")
    suspend fun deleteSlot(id: Int, slotIndex: Int)

    @Query("DELETE FROM multi_widget_slot WHERE appWidgetId = :id")
    suspend fun deleteAllSlots(id: Int)

    @Query("SELECT DISTINCT displayEntityId FROM multi_widget_slot")
    suspend fun allDisplayEntityIds(): List<String>

    @Query("SELECT DISTINCT actionEntityId FROM multi_widget_slot")
    suspend fun allActionEntityIds(): List<String>

    /** Alle slots (på tværs af widgets) der viser ELLER handler på [entityId] — bruges til fan-out. */
    @Query("SELECT * FROM multi_widget_slot WHERE displayEntityId = :entityId OR actionEntityId = :entityId")
    suspend fun slotsForEntity(entityId: String): List<MultiWidgetSlotEntity>
}
