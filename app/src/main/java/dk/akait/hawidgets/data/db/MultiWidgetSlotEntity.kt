package dk.akait.hawidgets.data.db

import androidx.room.Entity

/**
 * Visning ([displayEntityId]/[displayDomain]) og action-mål ([actionEntityId]/[actionDomain])
 * er bevidst uafhængige felter — en slot kan vise én entitet (fx en batteri-sensor) men
 * handle på en helt anden (fx udløse en automatisering). Config-UI'en foreslår action =
 * samme entitet som default, men brugeren kan ændre det.
 */
@Entity(
    tableName = "multi_widget_slot",
    primaryKeys = ["appWidgetId", "slotIndex"],
)
data class MultiWidgetSlotEntity(
    val appWidgetId: Int,
    val slotIndex: Int, // 0..4, venstre-til-højre rækkefølge
    val displayEntityId: String,
    val displayDomain: String,
    val actionEntityId: String,
    val actionDomain: String,
    val action: String, // "TOGGLE" | "RANGE" | "TRIGGER" | "NONE"
    val label: String, // tom = brug friendly_name fra displayEntityId, maks 12 tegn
)
