package dk.akait.hawidgets.widget.multientity

import dk.akait.hawidgets.data.HaApiClient
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.compatibleActionsFor
import dk.akait.hawidgets.widget.common.defaultShowValueFor

internal const val MAX_SECONDARY_ENTITIES = 3

/** Samme visning/handling-uafhængighed som [SlotDraft] selv — se docs/widget-settings-spec.md §9. */
internal data class SecondarySlotDraft(
    val displayEntity: HaApiClient.EntityBrief,
    val actionEntity: HaApiClient.EntityBrief,
    val action: String,
    /** Vis værditekst (ikke kun ikon) på chippen — brugervalgt, default via [defaultShowValueFor]. */
    val showValue: Boolean = defaultShowValueFor(action),
    /** Bekræft ved tryk (v0.3.0, B1) — kun meningsfuld for TOGGLE/TRIGGER. */
    val confirmAction: Boolean = false,
    /** Værdi-formatering (v0.3.0, C2) — null = auto. Kun relevant for rå/enheds-bærende domæner. */
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
    /** RANGE input-tilstand (Task 13) — "SLIDER"/"FIELD". null = "SLIDER". Kun relevant for RANGE. */
    val rangeInputMode: String? = null,
)

internal data class SlotDraft(
    val displayEntity: HaApiClient.EntityBrief? = null,
    val actionEntity: HaApiClient.EntityBrief? = null,
    val action: String = "NONE",
    val label: String = "",
    val secondaryEntities: List<SecondarySlotDraft> = emptyList(),
    /** Bekræft ved tryk (v0.3.0, B1) — kun meningsfuld for TOGGLE/TRIGGER. */
    val confirmAction: Boolean = false,
    /** Værdi-formatering (v0.3.0, C2) — null = auto. Kun relevant for rå/enheds-bærende domæner. */
    val displayPrecision: Int? = null,
    val datetimeFormat: String? = null,
    /** RANGE input-tilstand (Task 13) — "SLIDER"/"FIELD". null = "SLIDER". Kun relevant for RANGE. */
    val rangeInputMode: String? = null,
)

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

private fun secondaryDraftFrom(
    allEntities: List<HaApiClient.EntityBrief>,
    displayId: String?, displayDomain: String?,
    actionId: String?, actionDomain: String?,
    action: String?, showValue: Boolean?, confirmAction: Boolean?,
    displayPrecision: Int?, datetimeFormat: String?, rangeInputMode: String?,
): SecondarySlotDraft? {
    if (displayId == null || displayDomain == null || actionId == null || actionDomain == null || action == null) return null
    return SecondarySlotDraft(
        displayEntity = entityOrPlaceholder(allEntities, displayId, displayDomain),
        actionEntity = entityOrPlaceholder(allEntities, actionId, actionDomain),
        action = action,
        showValue = showValue ?: defaultShowValueFor(action),
        confirmAction = confirmAction ?: false,
        displayPrecision = displayPrecision,
        datetimeFormat = datetimeFormat,
        rangeInputMode = rangeInputMode,
    )
}

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
    val secondaries = listOfNotNull(
        secondaryDraftFrom(
            allEntities,
            slot.secondary1DisplayEntityId, slot.secondary1DisplayDomain,
            slot.secondary1ActionEntityId, slot.secondary1ActionDomain, slot.secondary1Action,
            slot.secondary1ShowValue, slot.secondary1ConfirmAction,
            slot.secondary1DisplayPrecision, slot.secondary1DatetimeFormat, slot.secondary1RangeInputMode,
        ),
        secondaryDraftFrom(
            allEntities,
            slot.secondary2DisplayEntityId, slot.secondary2DisplayDomain,
            slot.secondary2ActionEntityId, slot.secondary2ActionDomain, slot.secondary2Action,
            slot.secondary2ShowValue, slot.secondary2ConfirmAction,
            slot.secondary2DisplayPrecision, slot.secondary2DatetimeFormat, slot.secondary2RangeInputMode,
        ),
        secondaryDraftFrom(
            allEntities,
            slot.secondary3DisplayEntityId, slot.secondary3DisplayDomain,
            slot.secondary3ActionEntityId, slot.secondary3ActionDomain, slot.secondary3Action,
            slot.secondary3ShowValue, slot.secondary3ConfirmAction,
            slot.secondary3DisplayPrecision, slot.secondary3DatetimeFormat, slot.secondary3RangeInputMode,
        ),
    )
    return SlotDraft(
        display, actionEntity, normalizedAction, slot.label, secondaries, slot.confirmAction,
        slot.displayPrecision, slot.datetimeFormat, slot.rangeInputMode,
    )
}

/** Bygger en persisterbar [MultiWidgetSlotEntity] fra draften. Returnerer null hvis ingen
 * visnings-entitet er valgt (samme guard som den tidligere saveSlot). */
internal fun SlotDraft.toSlotEntity(appWidgetId: Int, slotIndex: Int): MultiWidgetSlotEntity? {
    val display = displayEntity ?: return null
    val action = actionEntity ?: display
    val sec = secondaryEntities
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
        secondary1DisplayEntityId = sec.getOrNull(0)?.displayEntity?.entityId,
        secondary1DisplayDomain = sec.getOrNull(0)?.displayEntity?.domain,
        secondary1ActionEntityId = sec.getOrNull(0)?.actionEntity?.entityId,
        secondary1ActionDomain = sec.getOrNull(0)?.actionEntity?.domain,
        secondary1Action = sec.getOrNull(0)?.action,
        secondary1ShowValue = sec.getOrNull(0)?.showValue,
        secondary1ConfirmAction = sec.getOrNull(0)?.confirmAction,
        secondary1DisplayPrecision = sec.getOrNull(0)?.displayPrecision,
        secondary1DatetimeFormat = sec.getOrNull(0)?.datetimeFormat,
        secondary1RangeInputMode = sec.getOrNull(0)?.rangeInputMode,
        secondary2DisplayEntityId = sec.getOrNull(1)?.displayEntity?.entityId,
        secondary2DisplayDomain = sec.getOrNull(1)?.displayEntity?.domain,
        secondary2ActionEntityId = sec.getOrNull(1)?.actionEntity?.entityId,
        secondary2ActionDomain = sec.getOrNull(1)?.actionEntity?.domain,
        secondary2Action = sec.getOrNull(1)?.action,
        secondary2ShowValue = sec.getOrNull(1)?.showValue,
        secondary2ConfirmAction = sec.getOrNull(1)?.confirmAction,
        secondary2DisplayPrecision = sec.getOrNull(1)?.displayPrecision,
        secondary2DatetimeFormat = sec.getOrNull(1)?.datetimeFormat,
        secondary2RangeInputMode = sec.getOrNull(1)?.rangeInputMode,
        secondary3DisplayEntityId = sec.getOrNull(2)?.displayEntity?.entityId,
        secondary3DisplayDomain = sec.getOrNull(2)?.displayEntity?.domain,
        secondary3ActionEntityId = sec.getOrNull(2)?.actionEntity?.entityId,
        secondary3ActionDomain = sec.getOrNull(2)?.actionEntity?.domain,
        secondary3Action = sec.getOrNull(2)?.action,
        secondary3ShowValue = sec.getOrNull(2)?.showValue,
        secondary3ConfirmAction = sec.getOrNull(2)?.confirmAction,
        secondary3DisplayPrecision = sec.getOrNull(2)?.displayPrecision,
        secondary3DatetimeFormat = sec.getOrNull(2)?.datetimeFormat,
        secondary3RangeInputMode = sec.getOrNull(2)?.rangeInputMode,
    )
}
