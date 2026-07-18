package dk.rtr.hawidgets.widget.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Trin-logik for −/+ knapperne i [RangeControlActivity] (Task 13, variant B — gælder ALLE widgets
 * der bruger den delte RangeControlActivity, jf. plan-ADR-2).
 *
 * Ren funktion, unit-testet i RangeSteppingTest — al FP-følsom aritmetik lever her, ikke i
 * Activity'ens Compose-state, så den kan verificeres uden en emulator.
 */

/** Trin-størrelse: 0.5 for et lille range (≤ 20), ellers 1.0. Matcher mockup-beslutningen. */
fun stepFor(min: Double, max: Double): Double = if (max - min <= 20.0) 0.5 else 1.0

/**
 * Snap [current] til nærmeste trin, flyt så ét trin i [direction] (-1/+1), clamp til [[min], [max]].
 *
 * Første tryk afrunder ALTID til nærmeste trin først:
 *  - `stepValue(23.7, -1, 0.5, 16, 30)` → 23.5 (23.7 ligger ikke på et 0.5-trin: snap ned til 23.5)
 *  - `stepValue(23.7, +1, 0.5, 16, 30)` → 24.0 (snap til 23.5, så +0.5)
 *  - `stepValue(23.5, +1, 0.5, 16, 30)` → 24.0 (ligger på trin: +0.5)
 *  - `stepValue(30.0, +1, 0.5, 16, 30)` → 30.0 (clamp ved max)
 *
 * Detalje: når værdien IKKE ligger på et trin og retningen er negativ, er selve snappet (til
 * nærmeste, dvs. potentielt nedad via HALF_UP) allerede "ét skridt ned" — der lægges derfor ikke et
 * ekstra trin til i den gren. I alle andre tilfælde flyttes ét fuldt trin fra det snappede punkt.
 */
fun stepValue(current: Double, direction: Int, step: Double, min: Double, max: Double): Double {
    val snapped = Math.round(current / step) * step
    val next = if (snapped != current && direction < 0) snapped else snapped + direction * step
    // Afrund FP-akkumulering væk (fx 23.5 + 0.5 kan give 24.000000000000004) — bevar op til
    // 4 decimaler, rigeligt til de trin (0.5/1.0) og ranges vi arbejder med.
    val cleaned = BigDecimal(next).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toDouble()
    return cleaned.coerceIn(min, max)
}
