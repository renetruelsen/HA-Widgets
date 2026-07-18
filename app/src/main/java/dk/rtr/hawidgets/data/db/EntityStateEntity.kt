package dk.rtr.hawidgets.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entity_state")
data class EntityStateEntity(
    @PrimaryKey val entityId: String,
    val state: String,
    val attributesJson: String,
    val lastUpdated: Long,  // epoch millis
)
