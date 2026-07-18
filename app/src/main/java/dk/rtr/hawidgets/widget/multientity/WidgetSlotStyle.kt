package dk.rtr.hawidgets.widget.multientity

/**
 * Semantiske farveroller for en widget-række/chip. Oversættes til faktiske Glance-[ColorProvider]'e
 * i MultiEntityRendering.colorFor(). At holde resolveren i rene roller gør den unit-testbar UDEN
 * Android/Glance, og giver ét sted en fremtidig tema-editor kan bytte farve-sættet ud.
 *
 * HEATING_ACCENT/HEATING_DIM/HEATING_UNAVAIL er FASTE (tema-uafhængige) farver til chips der sidder
 * PÅ en varmende (fuld orange) hoved-række — den orange baggrund er selv tema-uafhængig, så en
 * tema-afhængig tekstfarve ovenpå ville give to forskellige nuancer på samme flade.
 */
enum class ColorRole {
    PRIMARY, ON_PRIMARY, ROW_BG, CHIP_BG, NEUTRAL, CHIP_DIM, FADED, HEATING_BG, ON_HEATING,
    HEATING_ACCENT, HEATING_DIM, HEATING_UNAVAIL,
}

/** Fladt styling-resultat for én række/chip: hvilken rolle hvert visuelt element får, samt om
 * label/navnet skal overstreges (kun ved utilgængelig — status/værdi overstreges ALDRIG). */
data class StyleTokens(
    val bg: ColorRole,
    val icon: ColorRole,
    val label: ColorRole,
    val status: ColorRole,
    val showBorder: Boolean,
    val strikeLabel: Boolean = false,
)

/**
 * Ren beslutningsfunktion for active-styling (v0.2.73-redesign, se
 * docs/superpowers/specs/2026-07-11-widget-active-styling-design.md).
 *
 * - Hoved-slot aktiv = "D": ikon+status primary, label neutral, neutral baggrund.
 * - Chip TOGGLE tændt = fuld primary; øvrige aktive chips = D på chip-flade; inaktiv/info = dæmpet.
 * - Climate der varmer = fuld orange (egen varme, højeste prioritet efter unavailable).
 * - Utilgængelig = nedtonet grå indhold, NAVNET overstreget (status ikke).
 *
 * [rowHeating] gælder kun chips: chippen sidder på en varmende (orange) hoved-række. Da rækken
 * allerede skriger "varmer", holdes chip-indholdet simpelt — kun en TOGGLE-tændt chip skiller sig
 * ud (fuld [HEATING_ACCENT]-flade, hvid tekst); alt andet indhold (aktiv D, info, TOGGLE-slukket)
 * bruger den samme lyse [HEATING_DIM] på chip-fladen. En chip der SELV varmer ([heating]) vinder
 * over [rowHeating] og bliver fuld orange.
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
    rowHeating: Boolean = false,
): StyleTokens {
    val bgNeutral = if (isChip) ColorRole.CHIP_BG else ColorRole.ROW_BG
    // "dæmpet" er CHIP_DIM på en chip, men almindelig NEUTRAL på hoved-rækken (rækken har ikke en
    // subtilt-mørkere flade at dæmpe imod).
    val dim = if (isChip) ColorRole.CHIP_DIM else ColorRole.NEUTRAL
    return when {
        // Utilgængelig (højeste prioritet): på en varmende række er FADED-grå ulæselig → brug den
        // lysere HEATING_UNAVAIL. Navnet overstreges uanset kontekst.
        unavailable -> {
            val c = if (rowHeating) ColorRole.HEATING_UNAVAIL else ColorRole.FADED
            StyleTokens(bgNeutral, c, c, c, showBorder = false, strikeLabel = true)
        }
        // Egen varme (chippens/rækkens egen climate varmer) → fuld orange, vinder over rowHeating.
        heating -> StyleTokens(ColorRole.HEATING_BG, ColorRole.ON_HEATING, ColorRole.ON_HEATING, ColorRole.ON_HEATING, false)
        // Chip på en varmende hoved-række (ikke selv varmende, ikke utilgængelig). Kun chips arver
        // en forælder-rækkes varme; en hoved-række har ingen forælder.
        isChip && rowHeating -> if (isToggle && isActive) {
            StyleTokens(ColorRole.HEATING_ACCENT, ColorRole.ON_HEATING, ColorRole.ON_HEATING, ColorRole.ON_HEATING, false)
        } else {
            StyleTokens(ColorRole.CHIP_BG, ColorRole.HEATING_DIM, ColorRole.HEATING_DIM, ColorRole.HEATING_DIM, false)
        }
        // Normal række-kontekst:
        isChip && isToggle && isActive ->
            StyleTokens(ColorRole.PRIMARY, ColorRole.ON_PRIMARY, ColorRole.ON_PRIMARY, ColorRole.ON_PRIMARY, false)
        isActive -> StyleTokens(bgNeutral, ColorRole.PRIMARY, dim, ColorRole.PRIMARY, false)
        else -> StyleTokens(bgNeutral, dim, dim, dim, false)
    }
}
