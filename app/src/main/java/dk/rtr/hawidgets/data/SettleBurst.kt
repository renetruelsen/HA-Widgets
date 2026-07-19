package dk.rtr.hawidgets.data

import dk.rtr.hawidgets.data.db.MultiWidgetChipEntity
import dk.rtr.hawidgets.data.db.MultiWidgetSlotEntity

/**
 * "Settle-burst": efter en bruger-handling (toggle/trigger/range/tænd-sluk) svarer HA OK med det
 * samme, men den FYSISKE enhed lander først sekunder-til-minutter senere — spaens `hvac_action`
 * flipper til "heating" når varmelegemet starter, Velux-vinduet kører til sin nye position osv.
 * Det ene øjeblikkelige refresh efter kommandoen fanger derfor overgangstilstanden, ikke
 * sluttilstanden, hvorefter widgetten venter på næste periodiske sync (op til 30 min som default
 * efter v0.2.85). Bursten lukker det hul ved at hente frisk tilstand nogle få gange over ~90 sek
 * efter handlingen — kun på et eksplicit bruger-tryk, tidsbegrænset, ingen vedvarende forbindelse.
 *
 * Kumulative tidspunkter fra kommandoen: 2, 5, 10, 20, 40, 60, 90 sek (7 runder). Dækker både
 * spaens opstartsforsinkelse og et Velux-vindue der kører < 1 min. Ingen "stop tidligt"-logik:
 * en forsinket ændring kan ikke skelnes fra "endnu ikke startet" uden domæne-specifik terminal-
 * viden, så en naiv "to ens runder → stop" ville risikere at stoppe FØR ændringen når frem.
 * Fast plan er korrekt, deterministisk og billig (få små GET pr. runde, kun ved tryk).
 */
val SETTLE_BURST_DELAYS_MS: LongArray = longArrayOf(2_000, 3_000, 5_000, 10_000, 20_000, 20_000, 30_000)

/** Sikkerhedsloft på antal entiteter i én burst (mod en absurd stor widget). */
const val SETTLE_BURST_MAX_ENTITIES = 40

/**
 * Ren resolution af "hele rækken": givet den handlede entitet, find alle entity-ID'er der bor i
 * SAMME række(r) — dvs. hvert slot (appWidgetId, slotIndex) hvor entiteten optræder som visning
 * ELLER action-mål ELLER i en af rækkens chips — inkl. slottets visnings-/action-entitet og alle
 * dens chips' visnings-/action-entiteter. Dækker asymmetriske slots (viser A, handler på B) og
 * chips der peger på andre entiteter end hoved-rækken.
 *
 * Returnerer altid mindst [actedEntityId] selv (også hvis den ikke findes i nogen række — fx en
 * handling udført lige før en config-ændring). Ren funktion → unit-testet ([SettleBurstTest]).
 */
internal fun entityIdsInSameRows(
    actedEntityId: String,
    slots: List<MultiWidgetSlotEntity>,
    chips: List<MultiWidgetChipEntity>,
): Set<String> {
    val matchingRows = HashSet<Pair<Int, Int>>()
    for (s in slots) {
        if (s.displayEntityId == actedEntityId || s.actionEntityId == actedEntityId) {
            matchingRows += s.appWidgetId to s.slotIndex
        }
    }
    for (c in chips) {
        if (c.displayEntityId == actedEntityId || c.actionEntityId == actedEntityId) {
            matchingRows += c.appWidgetId to c.slotIndex
        }
    }

    val result = LinkedHashSet<String>()
    result += actedEntityId
    if (matchingRows.isEmpty()) return result

    for (s in slots) {
        if ((s.appWidgetId to s.slotIndex) in matchingRows) {
            s.displayEntityId?.let(result::add)
            s.actionEntityId?.let(result::add)
        }
    }
    for (c in chips) {
        if ((c.appWidgetId to c.slotIndex) in matchingRows) {
            result += c.displayEntityId
            result += c.actionEntityId
        }
    }
    return result
}
