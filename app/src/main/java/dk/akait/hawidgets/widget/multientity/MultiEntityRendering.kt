package dk.akait.hawidgets.widget.multientity

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.defaultShowValueFor
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.WidgetColors
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatDisplayValue
import dk.akait.hawidgets.widget.common.formatEntityState
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.widget.common.isRawValueDomain
import dk.akait.hawidgets.widget.common.unitFromJson
import dk.akait.hawidgets.widget.common.isActiveState
import dk.akait.hawidgets.widget.common.isStale

// Ramme rundt om hele slot-listen — tema-bevidst (IKKE længere en hardcodet grå literal,
// jf. v0.2.26-code-review-fund om kontrast-risiko). Farven resolves nu via
// WidgetColors.frameBackground(context), så det globale tema-valg (lys/mørk/system) styrer
// hvilken side der bruges — for "system" er day/night-værdierne uændrede (ingen regression).
// Lav alpha, så tapetet stadig anes svagt igennem — "transparent ramme" jf. brugerens ord.
internal const val FRAME_PADDING_DP = 4
internal const val ROW_GAP_DP = 4
internal const val CHIP_GAP_DP = 4
internal const val REFRESH_STRIP_HEIGHT_DP = 24

/** En konfigureret sekundær-chip klar til rendering — null hvis den slot-plads er tom. */
private data class SecondaryChipData(
    val displayEntityId: String,
    val displayDomain: String,
    val actionEntityId: String,
    val actionDomain: String,
    val action: String,
    val showValue: Boolean,
    val confirmAction: Boolean,
    val displayPrecision: Int?,
    val datetimeFormat: String?,
    val rangeInputMode: String?,
)

private fun secondaryChipData(
    displayId: String?, displayDomain: String?,
    actionId: String?, actionDomain: String?,
    action: String?, showValue: Boolean?, confirmAction: Boolean?,
    displayPrecision: Int?, datetimeFormat: String?, rangeInputMode: String?,
): SecondaryChipData? {
    if (displayId == null || displayDomain == null || actionId == null || actionDomain == null || action == null) return null
    return SecondaryChipData(
        displayId, displayDomain, actionId, actionDomain, action,
        showValue ?: defaultShowValueFor(action), confirmAction ?: false,
        displayPrecision, datetimeFormat, rangeInputMode,
    )
}

private fun MultiWidgetSlotEntity.secondaryChips(): List<SecondaryChipData> = listOfNotNull(
    secondaryChipData(
        secondary1DisplayEntityId, secondary1DisplayDomain, secondary1ActionEntityId, secondary1ActionDomain,
        secondary1Action, secondary1ShowValue, secondary1ConfirmAction, secondary1DisplayPrecision, secondary1DatetimeFormat,
        secondary1RangeInputMode,
    ),
    secondaryChipData(
        secondary2DisplayEntityId, secondary2DisplayDomain, secondary2ActionEntityId, secondary2ActionDomain,
        secondary2Action, secondary2ShowValue, secondary2ConfirmAction, secondary2DisplayPrecision, secondary2DatetimeFormat,
        secondary2RangeInputMode,
    ),
    secondaryChipData(
        secondary3DisplayEntityId, secondary3DisplayDomain, secondary3ActionEntityId, secondary3ActionDomain,
        secondary3Action, secondary3ShowValue, secondary3ConfirmAction, secondary3DisplayPrecision, secondary3DatetimeFormat,
        secondary3RangeInputMode,
    ),
)

/** Domain-aware visningsværdi: rå/enheds-bærende domæner (sensor/number/input_* m.fl.) bruger
 * [formatDisplayValue] (precision/datetime-format-override, v0.3.0 C2); øvrige domæner (der har
 * en fast tekst-tabel i [formatEntityState], fx light/switch/climate) samt null/"unavailable"
 * bruger fortsat [formatEntityState] uændret. */
private fun displayValueFor(
    context: Context,
    domain: String,
    state: EntityStateEntity?,
    precision: Int?,
    datetimeFormat: String?,
): String {
    if (state == null || state.state == "unavailable" || !isRawValueDomain(domain)) {
        return formatEntityState(domain, state?.state, state?.attributesJson?.let { unitFromJson(it) })
    }
    val locale = context.resources.configuration.locales[0]
    return formatDisplayValue(domain, state.state, state.attributesJson, precision, datetimeFormat, locale)
}

@Composable
internal fun MultiEntityContent(
    context: Context,
    slots: List<MultiWidgetSlotEntity>,
    states: Map<String, EntityStateEntity?>,
    showRefreshIcon: Boolean,
) {
    val sorted = slots.sortedBy { it.slotIndex }

    // Rammen fylder hele det tildelte areal (fillMaxSize) — ved oversize efterlades tomrum
    // under listen/stripen i stedet for at strække indholdet (bevidst accepteret, se
    // brainstorm-konklusionen). LazyColumn scroller allerede ved undersize/overflow.
    //
    // ADR-3: refresh-stripen er et halvtransparent overlay der flyder OVEN PÅ listen (ikke en
    // fast bjælke under den) — brugerønske, "ser fedt ud". Strukturelt: en ydre Box lægger
    // LazyColumn'en (barn 0) og stripens Box (barn 1, bund-justeret) oven på hinanden. Glance
    // kompilerer Box til en rigtig FrameLayout hvor senere børn tegnes OVEN PÅ tidligere børn og
    // modtager touch FØRST (standard Android ViewGroup-opførsel) — stripen ligger derfor korrekt
    // øverst og forbliver klikbar, uanset listens indhold bagved. LazyColumn'en får en usynlig
    // spacer som SIDSTE element (kun når stripen vises) så et fuldt scroll ned viser den sidste
    // række helt fri af stripen, ikke delvist skjult bag den.
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetColors.frameBackground(context))
                .cornerRadius(16.dp)
                .padding(FRAME_PADDING_DP.dp),
        ) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(sorted, itemId = { it.slotIndex.toLong() }) { slot ->
                    Column {
                        SlotRow(context, slot, states)
                        Spacer(modifier = GlanceModifier.height(ROW_GAP_DP.dp))
                    }
                }
                if (showRefreshIcon) {
                    item { Spacer(modifier = GlanceModifier.height(REFRESH_STRIP_HEIGHT_DP.dp)) }
                }
            }
        }
        if (showRefreshIcon) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                RefreshStrip(context)
            }
        }
    }
}

@Composable
private fun RefreshStrip(context: Context) {
    // Hele rækken (ikke kun ikonet) er klikbar — et 16dp-bredt hit-areal alene ville være for
    // lille at ramme pålideligt i en kun 24dp høj bjælke. Halvtransparent baggrund (ADR-3) —
    // "glas"-strip oven på listen i stedet for en uigennemsigtig bjælke under den. cornerRadius
    // matcher rammens 16dp forneden, så stripen ikke stikker firkantet ud over rammens runde
    // silhuet (stripen ligger nu UDENFOR den clippede rammens Box, jf. overlay-omstruktureringen).
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(REFRESH_STRIP_HEIGHT_DP.dp)
            .background(WidgetColors.refreshOverlay(context))
            .cornerRadius(16.dp)
            .clickable(actionRunCallback<RefreshEntityAction>(actionParametersOf())),
        horizontalAlignment = Alignment.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = context.getString(R.string.multi_entity_refresh_all),
            modifier = GlanceModifier.size(16.dp).padding(end = 4.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun SlotRow(
    context: Context,
    slot: MultiWidgetSlotEntity,
    states: Map<String, EntityStateEntity?>,
) {
    val displayState = states[slot.displayEntityId]
    val actionState = states[slot.actionEntityId]
    val isUnavailable = displayState?.state == "unavailable"
    val isActive = displayState != null && isActiveState(slot.displayDomain, displayState.state)

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isActive -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isActive -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    val label = slot.label.ifEmpty {
        friendlyNameFromJson(displayState?.attributesJson ?: "{}") ?: slot.displayEntityId
    }
    val statusBase = displayValueFor(context, slot.displayDomain, displayState, slot.displayPrecision, slot.datetimeFormat)
    val statusText = if (displayState != null && displayState.isStale()) "$statusBase ~" else statusBase

    val rowModifier = clickModifier(
        context = context,
        base = GlanceModifier.fillMaxWidth().background(bgColor).cornerRadius(12.dp).padding(8.dp),
        action = slot.action,
        actionEntityId = slot.actionEntityId,
        actionDomain = slot.actionDomain,
        refreshEntityId = slot.displayEntityId,
        rangeLabel = label,
        actionState = actionState,
        confirmAction = slot.confirmAction,
        rangeInputMode = slot.rangeInputMode,
    )

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(domainIconResId(slot.displayDomain)),
            contentDescription = label,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(label, style = TextStyle(color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Text(statusText, style = TextStyle(color = contentColor, fontSize = 11.sp), maxLines = 1)
        }
        val chips = slot.secondaryChips()
        if (chips.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                chips.forEachIndexed { index, chip ->
                    if (index > 0) Spacer(modifier = GlanceModifier.width(CHIP_GAP_DP.dp))
                    SecondaryChip(context, chip, states)
                }
            }
        }
    }
}

@Composable
private fun SecondaryChip(
    context: Context,
    chip: SecondaryChipData,
    states: Map<String, EntityStateEntity?>,
) {
    val displayState = states[chip.displayEntityId]
    val actionState = states[chip.actionEntityId]
    val isUnavailable = displayState?.state == "unavailable"
    val isActive = displayState != null && isActiveState(chip.displayDomain, displayState.state)
    val isInfo = chip.action == "NONE"
    // Brugervalgt i config-UI'en (default via defaultShowValueFor, se secondaryChipData) —
    // ikke hardcodet til handlingstypen, så brugeren selv kan vise/skjule værditeksten pr. chip.
    val showsValueText = chip.showValue

    val bgColor = when {
        isUnavailable -> GlanceTheme.colors.errorContainer
        isInfo -> GlanceTheme.colors.surfaceVariant
        isActive -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.surfaceVariant
    }
    val contentColor = when {
        isUnavailable -> GlanceTheme.colors.onErrorContainer
        isInfo -> GlanceTheme.colors.onSurfaceVariant
        isActive -> GlanceTheme.colors.onPrimary
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    val label = friendlyNameFromJson(displayState?.attributesJson ?: "{}") ?: chip.displayEntityId

    // Eksplicit 48dp højde — Android-tilgængelighedens tap-target-minimum. Uden dette var
    // chippens reelle klik-areal kun icon+padding høj (~22dp), da den (i modsætning til
    // hoved-rækken) ikke får sin højde "gratis" fra en flerlinjet label-kolonne ved siden af.
    val chipModifier = clickModifier(
        context = context,
        base = GlanceModifier.background(bgColor).cornerRadius(10.dp).height(48.dp).padding(horizontal = 6.dp),
        action = chip.action,
        actionEntityId = chip.actionEntityId,
        actionDomain = chip.actionDomain,
        refreshEntityId = chip.displayEntityId,
        rangeLabel = label,
        actionState = actionState,
        confirmAction = chip.confirmAction,
        rangeInputMode = chip.rangeInputMode,
    )

    Row(modifier = chipModifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(domainIconResId(chip.displayDomain)),
            contentDescription = label,
            modifier = GlanceModifier.size(14.dp),
            colorFilter = ColorFilter.tint(contentColor),
        )
        if (showsValueText) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = displayValueFor(context, chip.displayDomain, displayState, chip.displayPrecision, chip.datetimeFormat),
                style = TextStyle(color = contentColor, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}
