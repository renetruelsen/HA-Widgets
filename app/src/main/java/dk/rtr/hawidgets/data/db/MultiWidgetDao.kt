package dk.rtr.hawidgets.data.db

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

    /** Soft-slettede widgets (removedAt sat) — nyeste først. Bruges af "Gendan fjernet widget". */
    @Query("SELECT * FROM multi_widget WHERE removedAt IS NOT NULL ORDER BY removedAt DESC")
    suspend fun getSoftDeleted(): List<MultiWidgetEntity>

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

    @Upsert
    suspend fun upsertChip(chip: MultiWidgetChipEntity)

    @Query("SELECT * FROM multi_widget_chip WHERE appWidgetId = :id ORDER BY slotIndex ASC, chipIndex ASC")
    suspend fun getChips(id: Int): List<MultiWidgetChipEntity>

    @Query("SELECT * FROM multi_widget_chip WHERE appWidgetId = :id ORDER BY slotIndex ASC, chipIndex ASC")
    fun observeChips(id: Int): Flow<List<MultiWidgetChipEntity>>

    @Query("DELETE FROM multi_widget_chip WHERE appWidgetId = :id")
    suspend fun deleteAllChips(id: Int)

    @Query("SELECT DISTINCT displayEntityId FROM multi_widget_slot WHERE displayEntityId IS NOT NULL")
    suspend fun allDisplayEntityIds(): List<String>

    @Query("SELECT DISTINCT actionEntityId FROM multi_widget_slot WHERE actionEntityId IS NOT NULL")
    suspend fun allActionEntityIds(): List<String>

    /** Alle sekundær-chip-entiteter (visning + handlings-mål) på tværs af alle widgets — bruges
     * sammen med [allDisplayEntityIds]/[allActionEntityIds] til periodisk fuld-sync. */
    @Query(
        """
        SELECT DISTINCT displayEntityId FROM multi_widget_chip
        UNION SELECT DISTINCT actionEntityId FROM multi_widget_chip
        """
    )
    suspend fun allSecondaryEntityIds(): List<String>

    /** True hvis [entityId] vises ELLER handles på i mindst én slot eller chip (på tværs af
     * widgets) — bruges til fan-out (skal denne entitets ændring udløse en widget-repaint?). */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM multi_widget_slot WHERE displayEntityId = :entityId OR actionEntityId = :entityId
            UNION
            SELECT 1 FROM multi_widget_chip WHERE displayEntityId = :entityId OR actionEntityId = :entityId
        )
        """
    )
    suspend fun isEntityUsed(entityId: String): Boolean
}
