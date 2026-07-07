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
    // v0.3.0: Bekræft ved tryk (B1) — kun meningsfuld for TOGGLE/TRIGGER
    val confirmAction: Boolean = false,
    // v0.3.0: Værdi-formatering (C2) — null = auto
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
    // Task 13 (del A): RANGE input-tilstand — "SLIDER" (skyder) eller "FIELD" (indtast værdi).
    // null = "SLIDER" = uændret adfærd. Kun meningsfuld når action == "RANGE".
    val rangeInputMode: String? = null,
    // Sekundære info/handlings-chips (v0.2.28) — op til 3 pr. slot, hver med samme
    // visning/handling-uafhængighed som hoved-entiteten selv. Null = chip ikke i brug.
    val secondary1DisplayEntityId: String? = null,
    val secondary1DisplayDomain: String? = null,
    val secondary1ActionEntityId: String? = null,
    val secondary1ActionDomain: String? = null,
    val secondary1Action: String? = null,
    // Vis værditekst (ikke kun ikon) på chippen — brugervalgt, se defaultShowValueFor for forslag.
    val secondary1ShowValue: Boolean? = null,
    val secondary1ConfirmAction: Boolean? = null,
    val secondary1DisplayPrecision: Int? = null,
    val secondary1DatetimeFormat: String? = null,
    val secondary1RangeInputMode: String? = null,
    val secondary2DisplayEntityId: String? = null,
    val secondary2DisplayDomain: String? = null,
    val secondary2ActionEntityId: String? = null,
    val secondary2ActionDomain: String? = null,
    val secondary2Action: String? = null,
    val secondary2ShowValue: Boolean? = null,
    val secondary2ConfirmAction: Boolean? = null,
    val secondary2DisplayPrecision: Int? = null,
    val secondary2DatetimeFormat: String? = null,
    val secondary2RangeInputMode: String? = null,
    val secondary3DisplayEntityId: String? = null,
    val secondary3DisplayDomain: String? = null,
    val secondary3ActionEntityId: String? = null,
    val secondary3ActionDomain: String? = null,
    val secondary3Action: String? = null,
    val secondary3ShowValue: Boolean? = null,
    val secondary3ConfirmAction: Boolean? = null,
    val secondary3DisplayPrecision: Int? = null,
    val secondary3DatetimeFormat: String? = null,
    val secondary3RangeInputMode: String? = null,
)
