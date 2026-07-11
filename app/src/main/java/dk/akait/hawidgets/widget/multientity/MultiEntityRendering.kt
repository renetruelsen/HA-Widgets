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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dk.akait.hawidgets.R
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.data.db.MultiWidgetSlotEntity
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.WidgetColors
import dk.akait.hawidgets.widget.common.domainIconResId
import dk.akait.hawidgets.widget.common.formatDisplayValue
import dk.akait.hawidgets.widget.common.formatEntityState
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.widget.common.hasOnOffState
import dk.akait.hawidgets.widget.common.hvacActionFromJson
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

// v0.2.42 række/chip-styling: tændt = fuld primary-farve, slukket (on/off-domæner) = kun outline.
// Glance har ingen border-modifier, så en "outline" laves med to lag: en ydre Box med ring-farve
// + lille padding, og en indre Box med fyld-farven → ringen ses som en kant. SURFACE_BORDER_DP er
// ringens tykkelse; de indre paddinger er skåret så den samlede kant+padding matcher det tidligere
// 8dp minus 4dp (brugervalgt lavere rækkehøjde) = 6dp pr. side.
private const val SURFACE_BORDER_DP = 2
private const val ROW_INNER_PAD_DP = 4      // + 2dp ring = 6dp pr. side (outline-tilstand)
private const val ROW_SINGLE_PAD_DP = 6     // fyldt/uden ring: 6dp pr. side
private const val CHIP_INNER_H_PAD_DP = 4   // + 2dp ring = 6dp (venstre side, ikon vist)
private const val CHIP_SINGLE_H_PAD_DP = 6  // venstre side, ikon vist
// Uden ikon mistes ikonets 14dp+4dp-spacer "buffer" foran teksten, så den stramme 6dp-kant bliver
// synlig/følelig (brugerfeedback) — bump til 8dp total pr. side når chip.showIcon == false.
private const val CHIP_INNER_H_PAD_NO_ICON_DP = 6   // + 2dp ring = 8dp pr. side
private const val CHIP_SINGLE_H_PAD_NO_ICON_DP = 8
// Ikonets eget indre "luft" i sit 14dp-felt (v0.2.60-fund) gør teksten visuelt skæv når ikonet ER
// vist — højre side (efter teksten) bumpes til 8dp for at kompensere, venstre side (før ikonet)
// forbliver 6dp (CHIP_INNER_H_PAD_DP/CHIP_SINGLE_H_PAD_DP ovenfor).
private const val CHIP_INNER_H_PAD_END_DP = 6   // + 2dp ring = 8dp (højre side, ikon vist)
private const val CHIP_SINGLE_H_PAD_END_DP = 8  // højre side, ikon vist
private const val ROW_CORNER_DP = 12
private const val CHIP_CORNER_DP = 10

/** Farvelag for en række/chip: [outer] = ring-farve (null = ingen ring, ét enkelt lag),
 * [inner] = fyld-farve, [content] = ikon/tekst-farve. */
private class Surface(val outer: ColorProvider?, val inner: ColorProvider, val content: ColorProvider)

@Composable
private fun surfaceFor(stateful: Boolean, active: Boolean, unavailable: Boolean, heating: Boolean, isChip: Boolean): Surface {
    val c = GlanceTheme.colors
    return when {
        unavailable -> Surface(null, c.errorContainer, c.onErrorContainer)
        // Climate der FAKTISK varmer (hvac_action == "heating", v0.2.48): fuld rød i stedet for blå.
        // Ingen ring — hverken chip eller række (v0.2.50: aktive/varme chips står solidt uden kant).
        // Andre climate-tilstande (idle/cool/auto/off) falder videre ned og bliver neutrale.
        heating -> Surface(null, WidgetColors.heatingFill, WidgetColors.onHeating)
        // Info-agtige (sensor/number/scene/script + rene visnings-slots): neutralt fyld, ingen on/off.
        !stateful -> Surface(null, c.surfaceVariant, c.onSurfaceVariant)
        // v0.2.61: ring-farven for en TÆNDT chip er nu `primary` (samme som fyldet) i stedet for
        // `surfaceVariant` — ingen synlig kant på en tændt chip (kun SLUKKET chips har en synlig,
        // temafarvet ring). Tilbagevenden til v0.2.50-beslutningen, som en senere session ændrede.
        active -> if (isChip) Surface(c.primary, c.primary, c.onPrimary) else Surface(null, c.primary, c.onPrimary)
        // Slukket on/off: chips beholder outline (primary ring), men hoved-rækken har KUN neutralt
        // gråt fyld uden ring (brugerønske v0.2.48) — kun det fyldte lag skiller tændt fra slukket.
        else -> if (isChip) Surface(c.primary, c.surfaceVariant, c.onSurfaceVariant) else Surface(null, c.surfaceVariant, c.onSurfaceVariant)
    }
}

/** Oversætter en semantisk [ColorRole] til en faktisk Glance-[ColorProvider]. Aktive farver læses
 * fra GlanceTheme (respekterer farvetema); neutrale/faded/chip-flader fra WidgetColors. */
@Composable
private fun colorFor(role: ColorRole, context: Context): ColorProvider = when (role) {
    ColorRole.PRIMARY -> GlanceTheme.colors.primary
    ColorRole.ON_PRIMARY -> GlanceTheme.colors.onPrimary
    ColorRole.ROW_BG -> GlanceTheme.colors.surfaceVariant
    ColorRole.NEUTRAL -> GlanceTheme.colors.onSurfaceVariant
    ColorRole.CHIP_BG -> WidgetColors.chipBackground
    ColorRole.CHIP_DIM -> WidgetColors.chipDimText(context)
    ColorRole.FADED -> WidgetColors.fadedContent(context)
    ColorRole.HEATING_BG -> WidgetColors.heatingFill
    ColorRole.ON_HEATING -> WidgetColors.onHeating
}

/** True når en climate-entitet FAKTISK varmer lige nu (hvac_action == "heating"). Kun climate-
 * domænet kan give true — øvrige domæner rapporterer ikke hvac_action. */
private fun isHeating(domain: String, state: EntityStateEntity?): Boolean =
    domain == "climate" && state != null &&
        hvacActionFromJson(state.attributesJson) == "heating"

// v0.2.42 række/chip-styling: tændt = fuld primary-farve, slukket (on/off-domæner) = kun outline.
// Glance har ingen border-modifier, så en "outline" laves med to lag: en ydre Box med ring-farve
// + lille padding om en indre Box med fyld-farven → ringen ses som en kant.
/** Renderer et [Surface] enten som ét fyldt lag (outer == null) eller to lag (ring om fyld).
 * Samler den ellers 4×-gentagne branch-struktur fra [SlotRow] og [SecondaryChip] ét sted
 * (v0.2.44-cleanup): [outerBase]/[innerBase] bærer sizing (fillMaxWidth vs height/fillMaxHeight),
 * [ringInnerPad]/[singlePad] er content-padding for hhv. ring- og enkelt-lag-tilstand, og
 * [makeClickable] wrapper det yderste lag med det rette klik-modifier. */
@Composable
private fun StatefulSurface(
    surface: Surface,
    cornerDp: Int,
    outerBase: GlanceModifier,
    innerBase: GlanceModifier,
    ringInnerPad: GlanceModifier,
    singlePad: GlanceModifier,
    makeClickable: (GlanceModifier) -> GlanceModifier,
    content: @Composable () -> Unit,
) {
    if (surface.outer != null) {
        Box(
            modifier = makeClickable(
                outerBase.background(surface.outer).cornerRadius(cornerDp.dp).padding(SURFACE_BORDER_DP.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = innerBase.background(surface.inner)
                    .cornerRadius((cornerDp - SURFACE_BORDER_DP).dp).then(ringInnerPad),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    } else {
        Box(
            modifier = makeClickable(
                outerBase.background(surface.inner).cornerRadius(cornerDp.dp).then(singlePad),
            ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

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
    val label: String,
    val showIcon: Boolean,
)

private fun SecondaryColumns.toChipData(): SecondaryChipData? {
    if (displayEntityId == null || displayDomain == null || actionEntityId == null || actionDomain == null || action == null) return null
    return SecondaryChipData(
        displayEntityId, displayDomain, actionEntityId, actionDomain, action,
        showValueOrDefault(), confirmActionOrDefault(),
        displayPrecision, datetimeFormat, rangeInputMode, labelOrEmpty(), showIconOrDefault(),
    )
}

private fun MultiWidgetSlotEntity.secondaryChips(): List<SecondaryChipData> =
    secondaryColumns().mapNotNull { it.toChipData() }

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
        return formatEntityState(context, domain, state?.state, state?.attributesJson?.let { unitFromJson(it) })
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
    val isUnavailable = displayState?.state == "unavailable" ||
        (slot.action != "NONE" && actionState?.state == "unavailable")
    val isActive = displayState != null && isActiveState(slot.displayDomain, displayState.state)
    val heating = isHeating(slot.displayDomain, displayState) ||
        (slot.action != "NONE" && isHeating(slot.actionDomain, actionState))

    val tokens = resolveStyle(
        isChip = false,
        isActive = isActive,
        isToggle = false,
        heating = heating,
        unavailable = isUnavailable,
    )
    val bgColor = colorFor(tokens.bg, context)
    val iconColor = colorFor(tokens.icon, context)
    val labelColor = colorFor(tokens.label, context)
    val statusColor = colorFor(tokens.status, context)

    val label = slot.label.ifEmpty {
        friendlyNameFromJson(displayState?.attributesJson ?: "{}") ?: slot.displayEntityId
    }
    val statusBase = displayValueFor(context, slot.displayDomain, displayState, slot.displayPrecision, slot.datetimeFormat)
    val statusText = if (displayState != null && displayState.isStale()) "$statusBase ~" else statusBase

    val chips = slot.secondaryChips()
    val rowContent: @Composable () -> Unit = {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (slot.showIcon) {
                Image(
                    provider = ImageProvider(domainIconResId(slot.displayDomain)),
                    contentDescription = label,
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(iconColor),
                )
                Spacer(modifier = GlanceModifier.width(10.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(label, style = TextStyle(color = labelColor, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(statusText, style = TextStyle(color = statusColor, fontSize = 11.sp), maxLines = 1)
            }
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

    fun withClick(base: GlanceModifier) = clickModifier(
        context = context,
        base = base,
        action = slot.action,
        actionEntityId = slot.actionEntityId,
        actionDomain = slot.actionDomain,
        refreshEntityId = slot.displayEntityId,
        rangeLabel = label,
        actionState = actionState,
        confirmAction = slot.confirmAction,
        rangeInputMode = slot.rangeInputMode,
        packageName = slot.actionPackageName,
    )

    // Border altid false i v0.2.73 → altid ét fyldt lag. StatefulSurface bevares (Task 5) til den
    // fremtidige border-kapacitet; her renderes det enkle lag direkte.
    Box(
        modifier = withClick(
            GlanceModifier.fillMaxWidth().background(bgColor).cornerRadius(ROW_CORNER_DP.dp).padding(ROW_SINGLE_PAD_DP.dp),
        ),
        contentAlignment = Alignment.Center,
    ) { rowContent() }
}

@Composable
private fun SecondaryChip(
    context: Context,
    chip: SecondaryChipData,
    states: Map<String, EntityStateEntity?>,
) {
    val displayState = states[chip.displayEntityId]
    val actionState = states[chip.actionEntityId]
    val isUnavailable = displayState?.state == "unavailable" ||
        (chip.action != "NONE" && actionState?.state == "unavailable")
    val stateful = chip.action != "NONE" && hasOnOffState(chip.actionDomain)
    val isActive = stateful && actionState != null && isActiveState(chip.actionDomain, actionState.state)
    val heating = isHeating(chip.displayDomain, displayState) ||
        (chip.action != "NONE" && isHeating(chip.actionDomain, actionState))

    val tokens = resolveStyle(
        isChip = true,
        isActive = isActive,
        isToggle = chip.action == "TOGGLE",
        heating = heating,
        unavailable = isUnavailable,
    )
    val bgColor = colorFor(tokens.bg, context)
    val iconColor = colorFor(tokens.icon, context)
    val labelColor = colorFor(tokens.label, context)
    val valueColor = colorFor(tokens.status, context)

    val labelText = chip.label
    val valueText = if (chip.showValue) {
        displayValueFor(context, chip.displayDomain, displayState, chip.displayPrecision, chip.datetimeFormat)
    } else null

    val hasText = labelText.isNotEmpty() || valueText != null
    val chipContent: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (chip.showIcon) {
                Image(
                    provider = ImageProvider(domainIconResId(chip.displayDomain)),
                    contentDescription = labelText.ifEmpty { chip.displayEntityId },
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(iconColor),
                )
                if (hasText) Spacer(modifier = GlanceModifier.width(4.dp))
            }
            if (hasText) {
                Column {
                    if (labelText.isNotEmpty()) {
                        Text(labelText, style = TextStyle(color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    if (valueText != null) {
                        Text(valueText, style = TextStyle(color = valueColor, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
    }

    fun withClick(base: GlanceModifier) = clickModifier(
        context = context,
        base = base,
        action = chip.action,
        actionEntityId = chip.actionEntityId,
        actionDomain = chip.actionDomain,
        refreshEntityId = chip.displayEntityId,
        rangeLabel = labelText.ifEmpty { chip.displayEntityId },
        actionState = actionState,
        confirmAction = chip.confirmAction,
        rangeInputMode = chip.rangeInputMode,
    )

    // Asymmetrisk padding når ikon vises (v0.2.60): 6dp før ikon / 8dp efter tekst; symmetrisk 8dp
    // uden ikon (v0.2.59). Border altid false i v0.2.73 → ét fyldt lag.
    val chipPad = if (chip.showIcon) {
        GlanceModifier.padding(start = CHIP_SINGLE_H_PAD_DP.dp, end = CHIP_SINGLE_H_PAD_END_DP.dp)
    } else {
        GlanceModifier.padding(horizontal = CHIP_SINGLE_H_PAD_NO_ICON_DP.dp)
    }
    Box(
        modifier = withClick(
            GlanceModifier.height(48.dp).background(bgColor).cornerRadius(CHIP_CORNER_DP.dp).then(chipPad),
        ),
        contentAlignment = Alignment.Center,
    ) { chipContent() }
}
