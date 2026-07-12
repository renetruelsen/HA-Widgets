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

    @Query("SELECT * FROM multi_widget")
    suspend fun getAll(): List<MultiWidgetEntity>

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

    /** Alle sekundær-chip-entiteter (visning + handlings-mål, op til 4 pr. slot) — bruges
     * sammen med [allDisplayEntityIds]/[allActionEntityIds] til periodisk fuld-sync. */
    @Query(
        """
        SELECT secondary1DisplayEntityId FROM multi_widget_slot WHERE secondary1DisplayEntityId IS NOT NULL
        UNION SELECT secondary1ActionEntityId FROM multi_widget_slot WHERE secondary1ActionEntityId IS NOT NULL
        UNION SELECT secondary2DisplayEntityId FROM multi_widget_slot WHERE secondary2DisplayEntityId IS NOT NULL
        UNION SELECT secondary2ActionEntityId FROM multi_widget_slot WHERE secondary2ActionEntityId IS NOT NULL
        UNION SELECT secondary3DisplayEntityId FROM multi_widget_slot WHERE secondary3DisplayEntityId IS NOT NULL
        UNION SELECT secondary3ActionEntityId FROM multi_widget_slot WHERE secondary3ActionEntityId IS NOT NULL
        UNION SELECT secondary4DisplayEntityId FROM multi_widget_slot WHERE secondary4DisplayEntityId IS NOT NULL
        UNION SELECT secondary4ActionEntityId FROM multi_widget_slot WHERE secondary4ActionEntityId IS NOT NULL
        """
    )
    suspend fun allSecondaryEntityIds(): List<String>

    /** Alle slots (på tværs af widgets) der viser ELLER handler på [entityId] — bruges til fan-out.
     * Tjekker også de 4 sekundær-chips' visning/handlings-mål (v0.2.28), ikke kun hoved-entiteten. */
    @Query(
        """
        SELECT * FROM multi_widget_slot WHERE displayEntityId = :entityId OR actionEntityId = :entityId
        OR secondary1DisplayEntityId = :entityId OR secondary1ActionEntityId = :entityId
        OR secondary2DisplayEntityId = :entityId OR secondary2ActionEntityId = :entityId
        OR secondary3DisplayEntityId = :entityId OR secondary3ActionEntityId = :entityId
        OR secondary4DisplayEntityId = :entityId OR secondary4ActionEntityId = :entityId
        """
    )
    suspend fun slotsForEntity(entityId: String): List<MultiWidgetSlotEntity>
}
