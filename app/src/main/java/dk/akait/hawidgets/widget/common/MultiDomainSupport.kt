package dk.akait.hawidgets.widget.common

import dk.akait.hawidgets.R

/** De 18 domains valgbare i MultiEntityWidget (både til visning og som action-mål) — inkl.
 * v0.2.29's "diverse inputs" (input_* helper-domæner, se docs/widget-settings-spec.md §9). */
val MULTI_ENTITY_DOMAINS = listOf(
    "light", "switch", "lock", "cover", "climate", "number",
    "automation", "scene", "script", "sensor", "binary_sensor", "device_tracker",
    "input_boolean", "input_number", "input_text", "input_datetime", "input_select", "input_button",
)

fun domainIconResId(domain: String): Int = when (domain) {
    "light" -> R.drawable.ic_lightbulb
    "switch" -> R.drawable.ic_switch
    "scene" -> R.drawable.ic_scene
    "script" -> R.drawable.ic_script
    "automation" -> R.drawable.ic_automation
    "sensor" -> R.drawable.ic_sensor
    "binary_sensor" -> R.drawable.ic_binary_sensor
    "cover" -> R.drawable.ic_cover
    "climate" -> R.drawable.ic_climate
    "lock" -> R.drawable.ic_lock
    "number", "input_number" -> R.drawable.ic_number
    "device_tracker" -> R.drawable.ic_device_tracker
    "input_boolean" -> R.drawable.ic_switch
    "input_button", "input_select" -> R.drawable.ic_script
    // input_text, input_datetime: intet dedikeret ikon — falder til generisk sensor-ikon.
    else -> R.drawable.ic_sensor
}

/** Domain-specifik state-formattering — matcher docs/widget-settings-spec.md's tabel + de 3 nye domains.
 * [unit], hvis angivet (fra entitetens `unit_of_measurement`-attribut), tilføjes efter rå værdier
 * (sensor/number/input_number m.fl.) — samme mønster som SensorWidgets `buildSensorValue`. */
fun formatEntityState(domain: String, state: String?, unit: String? = null): String = when {
    state == null -> "…"
    state == "unavailable" -> "Utilgængelig"
    domain == "light" || domain == "switch" || domain == "input_boolean" -> if (state == "on") "Tændt" else "Slukket"
    domain == "lock" -> if (state == "locked") "Låst" else "Ulåst"
    domain == "cover" -> when (state) {
        "open" -> "Åben"
        "closed" -> "Lukket"
        "opening" -> "Åbner…"
        "closing" -> "Lukker…"
        else -> state
    }
    domain == "climate" -> when (state) {
        "heat" -> "Opvarmning"
        "cool" -> "Køling"
        "auto", "heat_cool" -> "Auto"
        "dry" -> "Affugtning"
        "fan_only" -> "Ventilator"
        "off" -> "Slukket"
        else -> state
    }
    domain == "automation" -> if (state == "on") "Aktiv" else "Deaktiveret"
    domain == "binary_sensor" -> if (state == "on") "Aktiv" else "Inaktiv"
    domain == "scene" -> "Aktiver"
    domain == "script" -> if (state == "on") "Kører" else "Klar"
    domain == "device_tracker" -> if (state == "home") "Hjemme" else "Ude"
    unit != null -> "$state $unit" // sensor, number, input_number m.fl. med kendt enhed
    else -> state // øvrige rå værdi-domæner uden enhed
}

/** Skal domænets "aktiv"-tilstand fremhæves med primary-farve? Matcher eksisterende widgets' konvention. */
fun isActiveState(domain: String, state: String?): Boolean = when (domain) {
    "light", "switch", "automation", "climate", "binary_sensor", "input_boolean" -> state == "on"
    "lock" -> state == "locked"
    "cover" -> state == "open" || state == "opening"
    "device_tracker" -> state == "home"
    else -> false // sensor, number, scene, script, øvrige inputs: intet vedvarende on/off-udseende
}

/**
 * Action-typer der giver mening for et givent domæne SOM ACTION-MÅL. Ikke filtreret på
 * live kapabilitet (fx dimmable/positionable) — brugeren vælger selv, ligesom resten af
 * "brugervalgt action pr slot"-modellen.
 */
fun compatibleActionsFor(domain: String): List<String> = when (domain) {
    "light", "cover", "climate" -> listOf("NONE", "TOGGLE", "RANGE")
    "switch", "lock", "input_boolean" -> listOf("NONE", "TOGGLE")
    "number", "input_number" -> listOf("NONE", "RANGE")
    "automation" -> listOf("NONE", "TOGGLE", "TRIGGER")
    "scene", "script", "input_button" -> listOf("NONE", "TRIGGER")
    "input_text" -> listOf("NONE", "TEXT")
    "input_datetime" -> listOf("NONE", "DATETIME")
    // sensor, binary_sensor, device_tracker, input_select: read-only — input_select er bevidst
    // read-only i denne omgang (kræver en options-vælger-skærm at sætte en specifik værdi,
    // ikke en simpel 1-tryks toggle/range/trigger/tekst/dato-tid som resten).
    else -> listOf("NONE")
}

/** Standard-forslag for "vis værdi"-indstillingen på en sekundær-chip, når brugeren ikke selv
 * har valgt — værdi-bærende handlinger (info/range/tekst/dato-tid) foreslår værdi-tekst,
 * rene til/fra- og udløs-handlinger foreslår ikon-kun (tilstanden ses i baggrundsfarven). */
fun defaultShowValueFor(action: String): Boolean = action != "TOGGLE" && action != "TRIGGER"
