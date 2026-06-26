package dk.akait.hawidgets.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entity_widget")
data class EntityWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val entityId: String,
    val domain: String,
    val label: String,  // empty = use friendly_name from state attributes
)
