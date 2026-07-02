package dk.akait.hawidgets.widget.common

import dk.akait.hawidgets.R

/** De 12 domains valgbare i MultiEntityWidget (både til visning og som action-mål). */
val MULTI_ENTITY_DOMAINS = listOf(
    "light", "switch", "lock", "cover", "climate", "number",
    "automation", "scene", "script", "sensor", "binary_sensor", "device_tracker",
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
    "number" -> R.drawable.ic_number
    "device_tracker" -> R.drawable.ic_device_tracker
    else -> R.drawable.ic_sensor
}

/** Domain-specifik state-formattering — matcher docs/widget-settings-spec.md's tabel + de 3 nye domains. */
fun formatEntityState(domain: String, state: String?): String = when {
    state == null -> "…"
    state == "unavailable" -> "Utilgængelig"
    domain == "light" || domain == "switch" -> if (state == "on") "Tændt" else "Slukket"
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
    else -> state // sensor, number: rå værdi
}

/** Skal domænets "aktiv"-tilstand fremhæves med primary-farve? Matcher eksisterende widgets' konvention. */
fun isActiveState(domain: String, state: String?): Boolean = when (domain) {
    "light", "switch", "automation", "climate", "binary_sensor" -> state == "on"
    "lock" -> state == "locked"
    "cover" -> state == "open" || state == "opening"
    "device_tracker" -> state == "home"
    else -> false // sensor, number, scene, script: intet vedvarende on/off-udseende
}

/**
 * Action-typer der giver mening for et givent domæne SOM ACTION-MÅL. Ikke filtreret på
 * live kapabilitet (fx dimmable/positionable) — brugeren vælger selv, ligesom resten af
 * "brugervalgt action pr slot"-modellen.
 */
fun compatibleActionsFor(domain: String): List<String> = when (domain) {
    "light", "cover", "climate" -> listOf("NONE", "TOGGLE", "RANGE")
    "switch", "lock" -> listOf("NONE", "TOGGLE")
    "number" -> listOf("NONE", "RANGE")
    "automation" -> listOf("NONE", "TOGGLE", "TRIGGER")
    "scene", "script" -> listOf("NONE", "TRIGGER")
    else -> listOf("NONE") // sensor, binary_sensor, device_tracker: read-only
}
