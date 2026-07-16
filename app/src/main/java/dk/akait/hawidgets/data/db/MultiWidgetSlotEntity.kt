package dk.akait.hawidgets.data.db

import androidx.room.Entity

/**
 * Visning ([displayEntityId]/[displayDomain]) og action-mål ([actionEntityId]/[actionDomain])
 * er bevidst uafhængige felter — en slot kan vise én entitet (fx en batteri-sensor) men
 * handle på en helt anden (fx udløse en automatisering). Config-UI'en foreslår action =
 * samme entitet som default, men brugeren kan ændre det.
 *
 * Alle fem (display-/action-felter + action) er null SAMTIDIG for en "chips-only" række (ingen
 * hoved-entitet — rækken viser kun sine chips, se [MultiWidgetChipEntity]). UI'en sørger for at
 * de altid sættes/ryddes samlet; der findes ingen delvis-null-tilstand i praksis.
 */
@Entity(
    tableName = "multi_widget_slot",
    primaryKeys = ["appWidgetId", "slotIndex"],
)
data class MultiWidgetSlotEntity(
    val appWidgetId: Int,
    val slotIndex: Int, // venstre-til-højre rækkefølge
    val displayEntityId: String?,
    val displayDomain: String?,
    val actionEntityId: String?,
    val actionDomain: String?,
    val action: String?, // "TOGGLE" | "RANGE" | "TRIGGER" | "NONE" | "OPEN_APP" | null (chips-only)
    val label: String, // tom = brug friendly_name fra displayEntityId, maks 22 tegn
    // v0.3.0: Bekræft ved tryk (B1) — kun meningsfuld for TOGGLE/TRIGGER
    val confirmAction: Boolean = false,
    // v0.3.0: Værdi-formatering (C2) — null = auto
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
    // Task 13 (del A): RANGE input-tilstand — "SLIDER" (skyder) eller "FIELD" (indtast værdi).
    // null = "SLIDER" = uændret adfærd. Kun meningsfuld når action == "RANGE".
    val rangeInputMode: String? = null,
    // Skjul domæne-ikonet på hoved-rækken — default true (vist), uændret adfærd for eksisterende rækker.
    val showIcon: Boolean = true,
    // v0.2.72: "Åbn app"-handling (kun hoved-slotten). Når action == "OPEN_APP" peger trykket på
    // denne pakke i stedet for en HA-handling; displayet forbliver en HA-entitet. Null ellers.
    val actionPackageName: String? = null,
    // Vis rå skyder-værdi (fx "45%") i stedet for formatEntityState-tekst — kun relevant for
    // domæner i RANGE_VALUE_DOMAINS (light/cover/climate). null = false (uændret standardtekst).
    val showRangeValue: Boolean? = null,
)
