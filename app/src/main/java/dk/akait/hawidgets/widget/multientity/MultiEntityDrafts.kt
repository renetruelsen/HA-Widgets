package dk.akait.hawidgets.widget.multientity

import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.db.MultiSlotWithChips
import dk.akait.hawidgets.data.db.MultiWidgetChipEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.compatibleActionsFor
import dk.akait.hawidgets.widget.common.defaultShowValueFor

/** Praktisk UI-loft på antal chips pr. række (intet tilsvarende loft i databaseskemaet). */
internal const val MAX_SECONDARY_ENTITIES = 5

/** Samme visning/handling-uafhængighed som [SlotDraft] selv — se docs/widget-settings-spec.md §9. */
internal data class SecondarySlotDraft(
    val displayEntity: HaApiClient.EntityBrief,
    val actionEntity: HaApiClient.EntityBrief,
    val action: String,
    /** Vis værditekst (ikke kun ikon) på chippen — brugervalgt, default via [defaultShowValueFor]. */
    val showValue: Boolean = defaultShowValueFor(action),
    /** Bekræft ved tryk (v0.3.0, B1) — kun meningsfuld for TOGGLE/TRIGGER. Default TIL (v0.2.42). */
    val confirmAction: Boolean = true,
    /** Værdi-formatering (v0.3.0, C2) — null = auto. Kun relevant for rå/enheds-bærende domæner. */
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
    /** RANGE input-tilstand (Task 13) — "SLIDER"/"FIELD". null = "SLIDER". Kun relevant for RANGE. */
    val rangeInputMode: String? = null,
    /** Custom chip-label (v0.2.42) — vises på chippen over evt. værdi. Tom = ingen label. */
    val label: String = "",
    /** Skjul domæne-ikonet på chippen — brugervalgt, default vist. */
    val showIcon: Boolean = true,
    /** Vis rå skyder-værdi (fx "45%") i stedet for state-tekst — kun relevant for domæner i
     * RANGE_VALUE_DOMAINS (light/cover/climate), uafhængig af valgt handling. Default fra. */
    val showRangeValue: Boolean = false,
)

internal data class SlotDraft(
    val displayEntity: HaApiClient.EntityBrief? = null,
    val actionEntity: HaApiClient.EntityBrief? = null,
    val action: String = "NONE",
    val label: String = "",
    val secondaryEntities: List<SecondarySlotDraft> = emptyList(),
    /** Bekræft ved tryk (v0.3.0, B1) — kun meningsfuld for TOGGLE/TRIGGER. Default TIL (v0.2.42). */
    val confirmAction: Boolean = true,
    /** Værdi-formatering (v0.3.0, C2) — null = auto. Kun relevant for rå/enheds-bærende domæner. */
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
    /** RANGE input-tilstand (Task 13) — "SLIDER"/"FIELD". null = "SLIDER". Kun relevant for RANGE. */
    val rangeInputMode: String? = null,
    /** Skjul domæne-ikonet på hoved-rækken — brugervalgt, default vist. */
    val showIcon: Boolean = true,
    /** Pakkenavn for "Åbn app"-handlingen ([action] == "OPEN_APP"). Null ellers. Kun hoved-slotten. */
    val packageName: String? = null,
    /** Vis rå skyder-værdi (fx "45%"/"21.5°") i stedet for state-tekst — kun relevant for domæner i
     * RANGE_VALUE_DOMAINS (light/cover/climate), uafhængig af valgt handling. Default fra. */
    val showRangeValue: Boolean = false,
)

internal fun MultiWidgetChipEntity.showValueOrDefault(): Boolean = showValue ?: defaultShowValueFor(action)
internal fun MultiWidgetChipEntity.confirmActionOrDefault(): Boolean = confirmAction ?: false
internal fun MultiWidgetChipEntity.labelOrEmpty(): String = label?.trim() ?: ""
internal fun MultiWidgetChipEntity.showIconOrDefault(): Boolean = showIcon ?: true
internal fun MultiWidgetChipEntity.showRangeValueOrDefault(): Boolean = showRangeValue ?: false

/** Handlings-typer (ex. NONE) et domæne understøtter som action-mål. Tom = read-only. */
internal fun actionOptionsFor(domain: String): List<String> =
    compatibleActionsFor(domain).filter { it != "NONE" }

/** Default-handling når et mål (eller ny visnings-entitet) vælges: første rigtige handling, ellers
 * NONE for read-only domæner. Bruges til at snap'e action ved mål-skift, så UI aldrig står med en
 * handling målet ikke understøtter. */
internal fun defaultActionFor(domain: String): String =
    actionOptionsFor(domain).firstOrNull() ?: "NONE"

/** Slår en entitet op i den indlæste liste, eller returnerer en placeholder (bruges når en gemt
 * slot/chip refererer en entitet der ikke længere findes i cachen). */
internal fun entityOrPlaceholder(
    allEntities: List<HaApiClient.EntityBrief>,
    entityId: String,
    domain: String,
): HaApiClient.EntityBrief =
    allEntities.find { it.entityId == entityId }
        ?: HaApiClient.EntityBrief(entityId, entityId, "unknown", domain)

private fun MultiWidgetChipEntity.toDraft(allEntities: List<HaApiClient.EntityBrief>): SecondarySlotDraft =
    SecondarySlotDraft(
        displayEntity = entityOrPlaceholder(allEntities, displayEntityId, displayDomain),
        actionEntity = entityOrPlaceholder(allEntities, actionEntityId, actionDomain),
        action = action,
        showValue = showValueOrDefault(),
        confirmAction = confirmActionOrDefault(),
        displayPrecision = displayPrecision,
        datetimeFormat = datetimeFormat,
        rangeInputMode = rangeInputMode,
        label = labelOrEmpty(),
        showIcon = showIconOrDefault(),
        showRangeValue = showRangeValueOrDefault(),
    )

private fun SecondarySlotDraft.toChipEntity(appWidgetId: Int, slotIndex: Int, chipIndex: Int): MultiWidgetChipEntity =
    MultiWidgetChipEntity(
        appWidgetId = appWidgetId,
        slotIndex = slotIndex,
        chipIndex = chipIndex,
        displayEntityId = displayEntity.entityId,
        displayDomain = displayEntity.domain,
        actionEntityId = actionEntity.entityId,
        actionDomain = actionEntity.domain,
        action = action,
        showValue = showValue,
        confirmAction = confirmAction,
        displayPrecision = displayPrecision,
        datetimeFormat = datetimeFormat,
        rangeInputMode = rangeInputMode,
        label = label.trim().ifEmpty { null },
        showIcon = showIcon,
        showRangeValue = showRangeValue,
    )

/** [data]s slot + chips → redigerbar draft. En slot uden hoved-entitet (chips-only, se
 * [MultiWidgetSlotEntity]) giver en draft med `displayEntity == null` og `action == "NONE"`. */
internal fun draftFromSlotWithChips(
    data: MultiSlotWithChips,
    allEntities: List<HaApiClient.EntityBrief>,
): SlotDraft {
    val slot = data.slot
    val display = slot.displayEntityId?.let { id -> entityOrPlaceholder(allEntities, id, slot.displayDomain!!) }
    val actionEntity = slot.actionEntityId?.let { id -> entityOrPlaceholder(allEntities, id, slot.actionDomain!!) }
    // Normalisér gammelt data: en slot gemt med action=NONE men et ANDET mål var muligt i den
    // gamle UI, men er ugyldigt i den nye model (mål ≠ visning ⇒ altid en handling, ingen
    // "Kun visning"-radio at vælge). Snap til første gyldige handling, så en radio er valgt.
    // "OPEN_APP" er domæne-uafhængig og peger på en app (ikke actionEntity) — springer normalisering over.
    val normalizedAction = when {
        display == null -> "NONE"
        slot.action == "OPEN_APP" -> "OPEN_APP"
        actionEntity != null && actionEntity.entityId != display.entityId &&
            slot.action !in actionOptionsFor(actionEntity.domain) ->
            actionOptionsFor(actionEntity.domain).firstOrNull() ?: (slot.action ?: "NONE")
        else -> slot.action ?: "NONE"
    }
    val secondaries = data.chips.sortedBy { it.chipIndex }.map { it.toDraft(allEntities) }
    return SlotDraft(
        displayEntity = display,
        actionEntity = actionEntity ?: display,
        action = normalizedAction,
        label = slot.label,
        secondaryEntities = secondaries,
        confirmAction = slot.confirmAction,
        displayPrecision = slot.displayPrecision,
        datetimeFormat = slot.datetimeFormat,
        rangeInputMode = slot.rangeInputMode,
        showIcon = slot.showIcon,
        packageName = slot.actionPackageName,
        showRangeValue = slot.showRangeValue ?: false,
    )
}

/** Bygger en persisterbar [MultiSlotWithChips] fra draften. Returnerer null hvis hverken en
 * hoved-entitet ER valgt eller nogen chips er tilføjet (intet at gemme). En chips-only draft
 * (ingen hoved-entitet, mindst én chip) er gyldig og giver en slot med alle display-/action-felter
 * null. */
internal fun SlotDraft.toSlotWithChips(appWidgetId: Int, slotIndex: Int): MultiSlotWithChips? {
    if (displayEntity == null && secondaryEntities.isEmpty()) return null
    // "OPEN_APP" peger på en app, ikke en HA-entitet — action-mål-kolonnerne sættes til visningens
    // (så de er gyldige og targetDiffers=false ved genindlæsning), og pakkenavnet bæres separat.
    val isApp = action == "OPEN_APP"
    val target = displayEntity?.let { d -> if (isApp) d else (actionEntity ?: d) }
    val slot = MultiWidgetSlotEntity(
        appWidgetId = appWidgetId,
        slotIndex = slotIndex,
        displayEntityId = displayEntity?.entityId,
        displayDomain = displayEntity?.domain,
        actionEntityId = target?.entityId,
        actionDomain = target?.domain,
        action = displayEntity?.let { action },
        label = label.trim(),
        confirmAction = confirmAction,
        displayPrecision = displayPrecision,
        datetimeFormat = datetimeFormat,
        rangeInputMode = rangeInputMode,
        showIcon = showIcon,
        actionPackageName = if (isApp) packageName else null,
        showRangeValue = showRangeValue,
    )
    val chips = secondaryEntities.mapIndexed { index, sec -> sec.toChipEntity(appWidgetId, slotIndex, index) }
    return MultiSlotWithChips(slot, chips)
}
