package dk.akait.hawidgets.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "multi_widget")
data class MultiWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val showRefreshIcon: Boolean = true,
    /**
     * Soft-delete-tidsstempel (epoch millis). `null` = levende/bundet widget. Sættes når widgetten
     * ikke længere er bundet af AppWidgetManager (fjernet fra hjemskærmen); hard-slettes af
     * [dk.akait.hawidgets.data.reconcileWidgets] efter en grace-periode. Bevarer config i vinduet
     * så en ved-uheld-fjernet widget kan genanvendes via "Gendan fjernet widget".
     */
    val removedAt: Long? = null,
)
