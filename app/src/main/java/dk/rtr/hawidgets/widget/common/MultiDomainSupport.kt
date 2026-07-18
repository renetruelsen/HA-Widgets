package dk.rtr.hawidgets.widget.common

import android.content.Context
import dk.rtr.hawidgets.R

/** De 18 domains valgbare i MultiEntityWidget (både til visning og som action-mål) — inkl.
 * v0.2.29's "diverse inputs" (input_* helper-domæner, se docs/widget-settings-spec.md §9). */
val MULTI_ENTITY_DOMAINS = listOf(
    "light", "switch", "lock", "cover", "climate", "number",
    "automation", "scene", "script", "sensor", "binary_sensor", "device_tracker",
    "input_boolean", "input_number", "input_text", "input_datetime", "input_select", "input_button",
)

/** Samlet per-domæne "kapabilitet" — ikon, on/off-udseende, gyldige action-mål og state-formattering.
 * Konsoliderer hvad der tidligere var 5 uafhængige `when(domain)`-funktioner (v0.2.45-oprydning,
 * jf. v0.2.34-fund #10): at tilføje et nyt domæne kræver nu ét opslag i [DOMAIN_CAPABILITIES] i
 * stedet for fem separate steder der let kunne komme ud af trit med hinanden. */
private class DomainCapability(
    val iconResId: Int,
    val hasOnOffState: Boolean,
    val compatibleActions: List<String>,
    val isActive: (String) -> Boolean = { false },
    /** Formatteret, lokaliseret visningstekst for en KENDT, tilgængelig state (null/"unavailable"
     * håndteres centralt i [formatEntityState] før dette kaldes). [unit] er kun relevant for rå
     * værdi-domæner. */
    val stateText: (context: Context, state: String, unit: String?) -> String =
        { _, state, unit -> unit?.let { "$state $it" } ?: state },
)

private val DEFAULT_CAPABILITY = DomainCapability(
    iconResId = R.drawable.ic_sensor,
    hasOnOffState = false,
    compatibleActions = listOf("NONE", "HISTORY"),
)

private val DOMAIN_CAPABILITIES: Map<String, DomainCapability> = mapOf(
    "light" to DomainCapability(
        iconResId = R.drawable.ic_lightbulb,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "TOGGLE", "RANGE", "HISTORY"),
        isActive = { it == "on" },
        stateText = { c, state, _ -> c.getString(if (state == "on") R.string.state_on else R.string.state_off) },
    ),
    "switch" to DomainCapability(
        iconResId = R.drawable.ic_switch,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "TOGGLE", "HISTORY"),
        isActive = { it == "on" },
        stateText = { c, state, _ -> c.getString(if (state == "on") R.string.state_on else R.string.state_off) },
    ),
    "input_boolean" to DomainCapability(
        iconResId = R.drawable.ic_switch,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "TOGGLE", "HISTORY"),
        isActive = { it == "on" },
        stateText = { c, state, _ -> c.getString(if (state == "on") R.string.state_on else R.string.state_off) },
    ),
    "lock" to DomainCapability(
        iconResId = R.drawable.ic_lock,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "TOGGLE", "HISTORY"),
        isActive = { it == "locked" },
        stateText = { c, state, _ -> c.getString(if (state == "locked") R.string.state_locked else R.string.state_unlocked) },
    ),
    "cover" to DomainCapability(
        iconResId = R.drawable.ic_cover,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "TOGGLE", "RANGE", "HISTORY"),
        isActive = { it == "open" || it == "opening" },
        stateText = { c, state, _ ->
            when (state) {
                "open" -> c.getString(R.string.state_open)
                "closed" -> c.getString(R.string.state_closed)
                "opening" -> c.getString(R.string.state_opening)
                "closing" -> c.getString(R.string.state_closing)
                else -> state
            }
        },
    ),
    "climate" to DomainCapability(
        iconResId = R.drawable.ic_climate,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "TOGGLE", "RANGE", "HISTORY"),
        isActive = { it == "on" },
        stateText = { c, state, _ ->
            when (state) {
                "heat" -> c.getString(R.string.climate_heat)
                "cool" -> c.getString(R.string.climate_cool)
                "auto", "heat_cool" -> c.getString(R.string.climate_auto)
                "dry" -> c.getString(R.string.climate_dry)
                "fan_only" -> c.getString(R.string.climate_fan_only)
                "off" -> c.getString(R.string.state_off)
                else -> state
            }
        },
    ),
    "automation" to DomainCapability(
        iconResId = R.drawable.ic_automation,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "TOGGLE", "TRIGGER", "HISTORY"),
        isActive = { it == "on" },
        stateText = { c, state, _ -> c.getString(if (state == "on") R.string.state_active else R.string.state_deactivated) },
    ),
    "binary_sensor" to DomainCapability(
        iconResId = R.drawable.ic_binary_sensor,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "HISTORY"),
        isActive = { it == "on" },
        stateText = { c, state, _ -> c.getString(if (state == "on") R.string.state_active else R.string.state_inactive) },
    ),
    "device_tracker" to DomainCapability(
        iconResId = R.drawable.ic_device_tracker,
        hasOnOffState = true,
        compatibleActions = listOf("NONE", "HISTORY"),
        isActive = { it == "home" },
        stateText = { c, state, _ -> c.getString(if (state == "home") R.string.state_home else R.string.state_away) },
    ),
    "scene" to DomainCapability(
        iconResId = R.drawable.ic_scene,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "TRIGGER", "HISTORY"),
        stateText = { c, _, _ -> c.getString(R.string.state_scene_activate) },
    ),
    "script" to DomainCapability(
        iconResId = R.drawable.ic_script,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "TRIGGER", "HISTORY"),
        stateText = { c, state, _ -> c.getString(if (state == "on") R.string.state_running else R.string.state_ready) },
    ),
    "sensor" to DomainCapability(
        iconResId = R.drawable.ic_sensor,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "HISTORY"),
    ),
    "number" to DomainCapability(
        iconResId = R.drawable.ic_number,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "RANGE", "HISTORY"),
    ),
    "input_number" to DomainCapability(
        iconResId = R.drawable.ic_number,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "RANGE", "HISTORY"),
    ),
    "input_text" to DomainCapability(
        iconResId = R.drawable.ic_sensor,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "TEXT", "HISTORY"),
    ),
    "input_datetime" to DomainCapability(
        iconResId = R.drawable.ic_sensor,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "DATETIME", "HISTORY"),
    ),
    "input_select" to DomainCapability(
        iconResId = R.drawable.ic_script,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "HISTORY"),
    ),
    "input_button" to DomainCapability(
        iconResId = R.drawable.ic_script,
        hasOnOffState = false,
        compatibleActions = listOf("NONE", "TRIGGER", "HISTORY"),
    ),
)

private fun capabilityFor(domain: String): DomainCapability = DOMAIN_CAPABILITIES[domain] ?: DEFAULT_CAPABILITY

fun domainIconResId(domain: String): Int = capabilityFor(domain).iconResId

/** Domain-specifik state-formattering — matcher docs/widget-settings-spec.md's tabel + de 3 nye domains.
 * [unit], hvis angivet (fra entitetens `unit_of_measurement`-attribut), tilføjes efter rå værdier
 * (sensor/number/input_number m.fl.) — samme mønster som SensorWidgets `buildSensorValue`. */
fun formatEntityState(context: Context, domain: String, state: String?, unit: String? = null): String {
    if (state == null) return "…"
    if (state == "unavailable") return context.getString(R.string.state_unavailable)
    return capabilityFor(domain).stateText(context, state, unit)
}

/** Skal domænets "aktiv"-tilstand fremhæves med primary-farve? Matcher eksisterende widgets' konvention. */
fun isActiveState(domain: String, state: String?): Boolean = state != null && capabilityFor(domain).isActive(state)

/** Har domænet et vedvarende tændt/slukket-udseende (og dermed en meningsfuld "outline når slukket /
 * fuld farve når tændt"-styling)? Præcis de domæner hvor [isActiveState] kan blive true. Rene
 * værdi-/udløs-domæner (sensor/number/scene/script/…) er info-agtige og bruger et neutralt fyld. */
fun hasOnOffState(domain: String): Boolean = capabilityFor(domain).hasOnOffState

/**
 * Action-typer der giver mening for et givent domæne SOM ACTION-MÅL. Ikke filtreret på
 * live kapabilitet (fx dimmable/positionable) — brugeren vælger selv, ligesom resten af
 * "brugervalgt action pr slot"-modellen.
 */
fun compatibleActionsFor(domain: String): List<String> = capabilityFor(domain).compatibleActions

/** Standard-forslag for "vis værdi"-indstillingen på en sekundær-chip, når brugeren ikke selv
 * har valgt — værdi-bærende handlinger (info/range/tekst/dato-tid) foreslår værdi-tekst,
 * rene til/fra- og udløs-handlinger foreslår ikon-kun (tilstanden ses i baggrundsfarven). */
fun defaultShowValueFor(action: String): Boolean = action != "TOGGLE" && action != "TRIGGER"

/** Domæner uden en fast tekst-tabel i [formatEntityState] — rå værdi (+ evt. enhed) eller
 * datetime, og dermed kandidater til [dk.rtr.hawidgets.widget.common.formatDisplayValue]'s
 * precision/datetime-format-overrides (v0.3.0, C2). Matcher formatEntityState's "øvrige
 * rå værdi-domæner"-fallback-gren 1:1. */
fun isRawValueDomain(domain: String): Boolean = domain in setOf(
    "sensor", "number", "input_number", "input_text", "input_datetime", "input_select",
)
