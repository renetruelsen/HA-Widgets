package dk.rtr.hawidgets.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "multi_widget")
data class MultiWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    val showRefreshIcon: Boolean = true,
    /**
     * Række-densitet (pr. widget): "COMPACT"/"NORMAL"/"LARGE" → indholdshøjde 44/48/52dp
     * (se [dk.rtr.hawidgets.widget.multientity.RowDensity]). Default "NORMAL" = uændret 60dp-række.
     * Den vertikale padding er fast 6dp uanset niveau; kun indholdshøjden varierer, hvilket bevarer
     * lige-høje rækker (samme konstant driver både chip og en chipløs rækkes indhold) og de
     * afrundede hjørner (44dp ≫ 2×10dp radius).
     */
    val rowDensity: String = "NORMAL",
    /**
     * Soft-delete-tidsstempel (epoch millis). `null` = levende/bundet widget. Sættes når widgetten
     * ikke længere er bundet af AppWidgetManager (fjernet fra hjemskærmen); hard-slettes af
     * [dk.rtr.hawidgets.data.reconcileWidgets] efter en grace-periode. Bevarer config i vinduet
     * så en ved-uheld-fjernet widget kan genanvendes via "Gendan fjernet widget".
     */
    val removedAt: Long? = null,
)
