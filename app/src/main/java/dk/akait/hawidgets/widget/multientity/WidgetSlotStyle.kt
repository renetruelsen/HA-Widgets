package dk.akait.hawidgets.widget.multientity

/**
 * Semantiske farveroller for en widget-række/chip. Oversættes til faktiske Glance-[ColorProvider]'e
 * i MultiEntityRendering.colorFor(). At holde resolveren i rene roller gør den unit-testbar UDEN
 * Android/Glance, og giver ét sted en fremtidig tema-editor kan bytte farve-sættet ud.
 */
enum class ColorRole { PRIMARY, ON_PRIMARY, ROW_BG, CHIP_BG, NEUTRAL, CHIP_DIM, FADED, HEATING_BG, ON_HEATING }

/** Fladt styling-resultat for én række/chip: hvilken rolle hvert visuelt element får. */
data class StyleTokens(
    val bg: ColorRole,
    val icon: ColorRole,
    val label: ColorRole,
    val status: ColorRole,
    val showBorder: Boolean,
)

/**
 * Ren beslutningsfunktion for active-styling (v0.2.73-redesign, se
 * docs/superpowers/specs/2026-07-11-widget-active-styling-design.md).
 *
 * - Hoved-slot aktiv = "D": ikon+status primary, label neutral, neutral baggrund.
 * - Chip TOGGLE tændt = fuld primary; øvrige aktive chips = D på chip-flade; inaktiv/info = dæmpet.
 * - Climate varmer = fuld orange (højeste prioritet efter unavailable).
 * - Utilgængelig = nedtonet grå indhold på neutral baggrund.
 *
 * [showBorder] er altid false i denne version; 2-lags-border-kapaciteten bevares til en fremtidig
 * tema-editor (så resolveren blot skal begynde at sætte flaget).
 */
fun resolveStyle(
    isChip: Boolean,
    isActive: Boolean,
    isToggle: Boolean,
    heating: Boolean,
    unavailable: Boolean,
): StyleTokens {
    val bgNeutral = if (isChip) ColorRole.CHIP_BG else ColorRole.ROW_BG
    // "dæmpet" er CHIP_DIM på en chip, men almindelig NEUTRAL på hoved-rækken (rækken har ikke en
    // subtilt-mørkere flade at dæmpe imod).
    val dim = if (isChip) ColorRole.CHIP_DIM else ColorRole.NEUTRAL
    return when {
        unavailable -> StyleTokens(bgNeutral, ColorRole.FADED, ColorRole.FADED, ColorRole.FADED, false)
        heating -> StyleTokens(ColorRole.HEATING_BG, ColorRole.ON_HEATING, ColorRole.ON_HEATING, ColorRole.ON_HEATING, false)
        isChip && isToggle && isActive ->
            StyleTokens(ColorRole.PRIMARY, ColorRole.ON_PRIMARY, ColorRole.ON_PRIMARY, ColorRole.ON_PRIMARY, false)
        isActive -> StyleTokens(bgNeutral, ColorRole.PRIMARY, dim, ColorRole.PRIMARY, false)
        else -> StyleTokens(bgNeutral, dim, dim, dim, false)
    }
}
