package dk.akait.hawidgets.widget.multientity

import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.compatibleActionsFor
import dk.akait.hawidgets.widget.common.defaultShowValueFor

internal const val MAX_SECONDARY_ENTITIES = 4

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
)

/**
 * Flad repræsentation af ÉN sekundær-chips kolonner (index 0..2). Findes udelukkende for at
 * samle den ellers 3×-gentagne `secondary1/2/3…`-udrulning ét sted ([secondaryColumns] læser,
 * [withSecondaryColumns] skriver), så resten af koden kan iterere over en liste i stedet for at
 * kopiere de ~11 felter tre gange (pragmatisk DRY — det flade Room-skema bevares uændret).
 */
internal data class SecondaryColumns(
    val displayEntityId: String?,
    val displayDomain: String?,
    val actionEntityId: String?,
    val actionDomain: String?,
    val action: String?,
    val showValue: Boolean?,
    val confirmAction: Boolean?,
    val displayPrecision: Int?,
    val datetimeFormat: String?,
    val rangeInputMode: String?,
    val label: String?,
    val showIcon: Boolean?,
)

/** Fælles udlednings-regler for "uset kolonne"-defaults, delt af render-siden ([toChipData]) og
 * config-siden ([toDraft]) så de to aldrig kan divergere på hvad en null-kolonne betyder
 * (v0.2.44-cleanup). Kaldes altid efter at [action] er verificeret ikke-null. */
internal fun SecondaryColumns.showValueOrDefault(): Boolean = showValue ?: defaultShowValueFor(action ?: "NONE")
internal fun SecondaryColumns.confirmActionOrDefault(): Boolean = confirmAction ?: false
internal fun SecondaryColumns.labelOrEmpty(): String = label?.trim() ?: ""
internal fun SecondaryColumns.showIconOrDefault(): Boolean = showIcon ?: true

/** De 3 sekundær-pladser som liste (i rækkefølge) — tomme pladser har null-felter. */
internal fun MultiWidgetSlotEntity.secondaryColumns(): List<SecondaryColumns> = listOf(
    SecondaryColumns(
        secondary1DisplayEntityId, secondary1DisplayDomain, secondary1ActionEntityId, secondary1ActionDomain,
        secondary1Action, secondary1ShowValue, secondary1ConfirmAction, secondary1DisplayPrecision,
        secondary1DatetimeFormat, secondary1RangeInputMode, secondary1Label, secondary1ShowIcon,
    ),
    SecondaryColumns(
        secondary2DisplayEntityId, secondary2DisplayDomain, secondary2ActionEntityId, secondary2ActionDomain,
        secondary2Action, secondary2ShowValue, secondary2ConfirmAction, secondary2DisplayPrecision,
        secondary2DatetimeFormat, secondary2RangeInputMode, secondary2Label, secondary2ShowIcon,
    ),
    SecondaryColumns(
        secondary3DisplayEntityId, secondary3DisplayDomain, secondary3ActionEntityId, secondary3ActionDomain,
        secondary3Action, secondary3ShowValue, secondary3ConfirmAction, secondary3DisplayPrecision,
        secondary3DatetimeFormat, secondary3RangeInputMode, secondary3Label, secondary3ShowIcon,
    ),
    SecondaryColumns(
        secondary4DisplayEntityId, secondary4DisplayDomain, secondary4ActionEntityId, secondary4ActionDomain,
        secondary4Action, secondary4ShowValue, secondary4ConfirmAction, secondary4DisplayPrecision,
        secondary4DatetimeFormat, secondary4RangeInputMode, secondary4Label, secondary4ShowIcon,
    ),
)

/** Skriver op til 4 sekundær-pladser tilbage til de flade kolonner (resten nulstilles). */
internal fun MultiWidgetSlotEntity.withSecondaryColumns(cols: List<SecondaryColumns>): MultiWidgetSlotEntity {
    val c0 = cols.getOrNull(0)
    val c1 = cols.getOrNull(1)
    val c2 = cols.getOrNull(2)
    val c3 = cols.getOrNull(3)
    return copy(
        secondary1DisplayEntityId = c0?.displayEntityId, secondary1DisplayDomain = c0?.displayDomain,
        secondary1ActionEntityId = c0?.actionEntityId, secondary1ActionDomain = c0?.actionDomain,
        secondary1Action = c0?.action, secondary1ShowValue = c0?.showValue, secondary1ConfirmAction = c0?.confirmAction,
        secondary1DisplayPrecision = c0?.displayPrecision, secondary1DatetimeFormat = c0?.datetimeFormat,
        secondary1RangeInputMode = c0?.rangeInputMode, secondary1Label = c0?.label, secondary1ShowIcon = c0?.showIcon,
        secondary2DisplayEntityId = c1?.displayEntityId, secondary2DisplayDomain = c1?.displayDomain,
        secondary2ActionEntityId = c1?.actionEntityId, secondary2ActionDomain = c1?.actionDomain,
        secondary2Action = c1?.action, secondary2ShowValue = c1?.showValue, secondary2ConfirmAction = c1?.confirmAction,
        secondary2DisplayPrecision = c1?.displayPrecision, secondary2DatetimeFormat = c1?.datetimeFormat,
        secondary2RangeInputMode = c1?.rangeInputMode, secondary2Label = c1?.label, secondary2ShowIcon = c1?.showIcon,
        secondary3DisplayEntityId = c2?.displayEntityId, secondary3DisplayDomain = c2?.displayDomain,
        secondary3ActionEntityId = c2?.actionEntityId, secondary3ActionDomain = c2?.actionDomain,
        secondary3Action = c2?.action, secondary3ShowValue = c2?.showValue, secondary3ConfirmAction = c2?.confirmAction,
        secondary3DisplayPrecision = c2?.displayPrecision, secondary3DatetimeFormat = c2?.datetimeFormat,
        secondary3RangeInputMode = c2?.rangeInputMode, secondary3Label = c2?.label, secondary3ShowIcon = c2?.showIcon,
        secondary4DisplayEntityId = c3?.displayEntityId, secondary4DisplayDomain = c3?.displayDomain,
        secondary4ActionEntityId = c3?.actionEntityId, secondary4ActionDomain = c3?.actionDomain,
        secondary4Action = c3?.action, secondary4ShowValue = c3?.showValue, secondary4ConfirmAction = c3?.confirmAction,
        secondary4DisplayPrecision = c3?.displayPrecision, secondary4DatetimeFormat = c3?.datetimeFormat,
        secondary4RangeInputMode = c3?.rangeInputMode, secondary4Label = c3?.label, secondary4ShowIcon = c3?.showIcon,
    )
}

/** Handlings-typer (ex. NONE) et domæne understøtter som action-mål. Tom = read-only. */
internal fun actionOptionsFor(domain: String): List<String> =
    compatibleActionsFor(domain).filter { it != "NONE" }

/** Default-handling når et mål (eller ny visnings-entitet) vælges: første rigtige handling, ellers
 * NONE for read-only domæner. Bruges til at snap'e action ved mål-skift, så UI aldrig står med en
 * handling målet ikke understøtter. */
internal fun defaultActionFor(domain: String): String =
    actionOptionsFor(domain).firstOrNull() ?: "NONE"

/** Slår en entitet op i den indlæste liste, eller returnerer en placeholder (bruges når en gemt
 * slot refererer en entitet der ikke længere findes i cachen). */
internal fun entityOrPlaceholder(
    allEntities: List<HaApiClient.EntityBrief>,
    entityId: String,
    domain: String,
): HaApiClient.EntityBrief =
    allEntities.find { it.entityId == entityId }
        ?: HaApiClient.EntityBrief(entityId, entityId, "unknown", domain)

/** Én sekundær-plads (kolonner) → draft, eller null hvis pladsen er tom. */
private fun SecondaryColumns.toDraft(allEntities: List<HaApiClient.EntityBrief>): SecondarySlotDraft? {
    if (displayEntityId == null || displayDomain == null || actionEntityId == null || actionDomain == null || action == null) return null
    return SecondarySlotDraft(
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
    )
}

/** Draft → sekundær-plads (kolonner) til persistering. */
private fun SecondarySlotDraft.toColumns(): SecondaryColumns = SecondaryColumns(
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
)

internal fun draftFromSlot(
    slot: MultiWidgetSlotEntity,
    allEntities: List<HaApiClient.EntityBrief>,
): SlotDraft {
    val display = entityOrPlaceholder(allEntities, slot.displayEntityId, slot.displayDomain)
    val actionEntity = entityOrPlaceholder(allEntities, slot.actionEntityId, slot.actionDomain)
    // Normalisér gammelt data: en slot gemt med action=NONE men et ANDET mål var muligt i den
    // gamle UI, men er ugyldigt i den nye model (mål ≠ visning ⇒ altid en handling, ingen
    // "Kun visning"-radio at vælge). Snap til første gyldige handling, så en radio er valgt.
    val targetDiffers = actionEntity.entityId != display.entityId
    val opts = actionOptionsFor(actionEntity.domain)
    val normalizedAction = if (targetDiffers && slot.action !in opts) {
        opts.firstOrNull() ?: slot.action
    } else {
        slot.action
    }
    val secondaries = slot.secondaryColumns().mapNotNull { it.toDraft(allEntities) }
    return SlotDraft(
        display, actionEntity, normalizedAction, slot.label, secondaries, slot.confirmAction,
        slot.displayPrecision, slot.datetimeFormat, slot.rangeInputMode, slot.showIcon,
    )
}

/** Bygger en persisterbar [MultiWidgetSlotEntity] fra draften. Returnerer null hvis ingen
 * visnings-entitet er valgt (samme guard som den tidligere saveSlot). */
internal fun SlotDraft.toSlotEntity(appWidgetId: Int, slotIndex: Int): MultiWidgetSlotEntity? {
    val display = displayEntity ?: return null
    val action = actionEntity ?: display
    return MultiWidgetSlotEntity(
        appWidgetId = appWidgetId,
        slotIndex = slotIndex,
        displayEntityId = display.entityId,
        displayDomain = display.domain,
        actionEntityId = action.entityId,
        actionDomain = action.domain,
        action = this.action,
        label = label.trim(),
        confirmAction = confirmAction,
        displayPrecision = displayPrecision,
        datetimeFormat = datetimeFormat,
        rangeInputMode = rangeInputMode,
        showIcon = showIcon,
    ).withSecondaryColumns(secondaryEntities.map { it.toColumns() })
}
