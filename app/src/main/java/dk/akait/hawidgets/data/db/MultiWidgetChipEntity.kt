package dk.akait.hawidgets.data.db

import androidx.room.Entity

/**
 * Én sekundær info/handlings-chip — normaliseret tabel (v0.3.x) der erstatter de tidligere
 * secondary1-4-kolonner på [MultiWidgetSlotEntity]. [chipIndex] er venstre-til-højre rækkefølge
 * (0-baseret) inden for sin (appWidgetId, slotIndex). Ingen hardcoded loft i skemaet — UI'en
 * håndhæver MAX_SECONDARY_ENTITIES. En rækkes eksistens betyder chippen er i brug (ingen
 * null-som-tom-plads-semantik som i den gamle flade model).
 */
@Entity(
    tableName = "multi_widget_chip",
    primaryKeys = ["appWidgetId", "slotIndex", "chipIndex"],
)
data class MultiWidgetChipEntity(
    val appWidgetId: Int,
    val slotIndex: Int,
    val chipIndex: Int,
    val displayEntityId: String,
    val displayDomain: String,
    val actionEntityId: String,
    val actionDomain: String,
    val action: String,
    // Vis værditekst (ikke kun ikon) på chippen — brugervalgt, se defaultShowValueFor for forslag.
    val showValue: Boolean? = null,
    val confirmAction: Boolean? = null,
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
    val rangeInputMode: String? = null,
    // Custom chip-label — vises på chippen (linje over evt. værdi). Null/tom = ingen label.
    val label: String? = null,
    // Skjul domæne-ikonet på chippen — null = vist (default).
    val showIcon: Boolean? = null,
    // Vis rå skyder-værdi (fx "45%") i stedet for formatEntityState-tekst — kun relevant for
    // domæner i RANGE_VALUE_DOMAINS (light/cover/climate). null = false (uændret standardtekst).
    val showRangeValue: Boolean? = null,
)
