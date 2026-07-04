package dk.akait.hawidgets.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "multi_widget")
data class MultiWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val title: String, // tom streng = ingen titel-linje på widgetten
    val showRefreshIcon: Boolean = true,
)
