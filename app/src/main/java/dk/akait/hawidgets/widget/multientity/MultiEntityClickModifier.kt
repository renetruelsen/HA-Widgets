package dk.akait.hawidgets.widget.multientity

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import dk.akait.hawidgets.data.DisplayMode
import dk.akait.hawidgets.data.db.EntityStateEntity
import dk.akait.hawidgets.web.WebViewActivity
import dk.akait.hawidgets.widget.common.ConfirmActionActivity
import dk.akait.hawidgets.widget.common.DateTimeControlActivity
import dk.akait.hawidgets.widget.common.NumberInputActivity
import dk.akait.hawidgets.widget.common.RangeControlActivity
import dk.akait.hawidgets.widget.common.TextControlActivity
import dk.akait.hawidgets.widget.common.RefreshEntityAction
import dk.akait.hawidgets.widget.common.ToggleEntityAction
import dk.akait.hawidgets.widget.common.TriggerEntityAction
import dk.akait.hawidgets.widget.common.friendlyNameFromJson
import dk.akait.hawidgets.widget.common.isActiveState
import dk.akait.hawidgets.widget.common.rangeCurrentValue
import dk.akait.hawidgets.widget.common.rangeMax
import dk.akait.hawidgets.widget.common.rangeMin
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Fælles klik-håndtering for både hoved-rækken og sekundær-chips: NONE → opdatér kun
 * [refreshEntityId]; ellers TOGGLE/RANGE/TRIGGER på ([actionEntityId], [actionDomain]) — kan
 * være en anden entitet end den der vises (se designbeslutning i docs/widget-settings-spec.md §9). */
internal fun clickModifier(
    context: Context,
    base: GlanceModifier,
    action: String,
    actionEntityId: String,
    actionDomain: String,
    refreshEntityId: String,
    rangeLabel: String,
    actionState: EntityStateEntity?,
    confirmAction: Boolean,
    rangeInputMode: String?,
    packageName: String? = null,
): GlanceModifier {
    // "Åbn app": åbner en anden app på telefonen — helt uafhængigt af HA-tilstand (placeret FØR
    // unavailable-guarden nedenfor). Selve launch-intent-opslaget + "app ikke fundet"-fallback sker
    // i AppLaunchActivity, så et afinstalleret pakkenavn giver en toast frem for et tavst/døende tap.
    if (action == "OPEN_APP") {
        val pkg = packageName ?: return base
        val intent = Intent(context, dk.akait.hawidgets.widget.common.AppLaunchActivity::class.java).apply {
            putExtra(dk.akait.hawidgets.widget.common.AppLaunchActivity.EXTRA_PACKAGE, pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return base.clickable(actionStartActivity(intent))
    }
    if (action == "NONE") {
        return base.clickable(
            actionRunCallback<RefreshEntityAction>(
                actionParametersOf(RefreshEntityAction.entityIdKey to refreshEntityId)
            )
        )
    }
    // Rent visnings-kald, ingen HA-kommando afsendes → virker uanset actionState (også en
    // aktuelt utilgængelig entitet, hvor historikken ofte er præcis det man vil se). Åbner
    // det indbyggede /logbook-panel ("Aktivitet" på dansk) direkte i ét skud — ikke det
    // formodede standard-dashboard "lovelace" (findes ikke på alle HA-instanser, se v0.2.62-
    // root-cause) og ikke /history (viser kun grafen, ingen aktivitet — HA har intet indbygget
    // panel med begge dele uden en gyldig Lovelace-dashboard-kontekst, se v0.2.62). entity_id
    // sendes med i selve dashboard-stien (ikke som separat SPA-pushState bagefter) — HA's
    // url-sync-mixin reagerer kun på entity_id ved en RIGTIG sideindlæsning, ikke en
    // efterfølgende pushState (forsøgt og forkastet i v0.2.62).
    if (action == "HISTORY") {
        // Kontinuerte, numeriske domæner logger normalt intet i HA's logbog (HA's egen
        // logbook-filtrering ekskluderer dem) — /logbook viser derfor altid "ingen aktivitet"
        // for dem, mens /history's graf rent faktisk er brugbar. HA's egen more-info-dialog gør
        // det samme (viser kun graf for disse domæner). Alle andre domæner (light/switch/lock/
        // binary_sensor/automation osv.) har diskrete tilstandsskift der VISES fint i logbogen
        // (v0.2.62/63, brugerbekræftet), så de beholder /logbook (v0.2.63, bruger-ønske).
        val panel = if (actionDomain in setOf("sensor", "number", "input_number")) "history" else "logbook"
        // 36-timers vindue (i stedet for panelets eget ~3-timers standard-interval) — begge
        // paneler læser start_date/end_date (ISO 8601, ms-præcision) fra URL'en ved selve
        // sideindlæsningen, samme mekanisme som entity_id.
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val start = now.minus(36, ChronoUnit.HOURS)
        val path = "$panel?entity_id=$actionEntityId&start_date=$start&end_date=$now"
        val intent = Intent(context, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_DASHBOARD_PATH, path)
            putExtra(WebViewActivity.EXTRA_DISPLAY_MODE, DisplayMode.OVERLAY.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return base.clickable(actionStartActivity(intent))
    }
    if (actionState == null || actionState.state == "unavailable") return base
    // "Bekræft ved tryk" (B1): kun meningsfuldt for TOGGLE/TRIGGER (RANGE/TEXT/DATETIME åbner
    // allerede en dialog med Gem-knap, så en ekstra bekræftelse ville være redundant). Grenen er
    // rent ADDITIV: når confirmAction er false falder vi igennem til de UÆNDREDE original-grene
    // nedenfor. Dialogen navngiver ALTID handlings-målet (ADR-1) — actionState ER action-målets
    // state-entitet (states[actionEntityId]), så dens friendly_name er målets navn, ikke visningens.
    if (confirmAction && (action == "TOGGLE" || action == "TRIGGER")) {
        val targetName = friendlyNameFromJson(actionState.attributesJson) ?: actionEntityId
        val intent = Intent(context, ConfirmActionActivity::class.java).apply {
            putExtra(ConfirmActionActivity.EXTRA_ENTITY_ID, actionEntityId)
            putExtra(ConfirmActionActivity.EXTRA_DOMAIN, actionDomain)
            putExtra(ConfirmActionActivity.EXTRA_LABEL, targetName)
            putExtra(ConfirmActionActivity.EXTRA_ACTION, action)
            putExtra(ConfirmActionActivity.EXTRA_IS_ON, isActiveState(actionDomain, actionState.state))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return base.clickable(actionStartActivity(intent))
    }
    return when (action) {
        "TOGGLE" -> base.clickable(
            actionRunCallback<ToggleEntityAction>(
                actionParametersOf(
                    ToggleEntityAction.entityIdKey to actionEntityId,
                    ToggleEntityAction.domainKey to actionDomain,
                )
            )
        )
        "RANGE" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val current = rangeCurrentValue(actionDomain, actionState, attrs)
            val min = rangeMin(actionDomain, attrs)
            val max = rangeMax(actionDomain, attrs)
            val unit = if (actionDomain == "number" || actionDomain == "input_number") {
                attrs.optString("unit_of_measurement", "")
            } else ""
            // Task 13 (del A): "FIELD" → indtast-værdi-dialog (NumberInputActivity); ellers
            // (null/"SLIDER") den uændrede skyder-dialog (RangeControlActivity). Begge afsender
            // samme service via den delte sendRangeValue.
            val intent = if (rangeInputMode == "FIELD") {
                Intent(context, NumberInputActivity::class.java).apply {
                    putExtra(NumberInputActivity.EXTRA_ENTITY_ID, actionEntityId)
                    putExtra(NumberInputActivity.EXTRA_LABEL, rangeLabel)
                    putExtra(NumberInputActivity.EXTRA_DOMAIN, actionDomain)
                    putExtra(NumberInputActivity.EXTRA_CURRENT_VALUE, current)
                    putExtra(NumberInputActivity.EXTRA_MIN_VALUE, min)
                    putExtra(NumberInputActivity.EXTRA_MAX_VALUE, max)
                    putExtra(NumberInputActivity.EXTRA_UNIT_SUFFIX, unit)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(context, RangeControlActivity::class.java).apply {
                    putExtra(RangeControlActivity.EXTRA_ENTITY_ID, actionEntityId)
                    putExtra(RangeControlActivity.EXTRA_LABEL, rangeLabel)
                    putExtra(RangeControlActivity.EXTRA_DOMAIN, actionDomain)
                    putExtra(RangeControlActivity.EXTRA_IS_ON, actionState.state != "off" && actionState.state != "closed")
                    // Sendes som præcise (decimal) værdier — number/input_number kan have en
                    // fraktioneret state/step (fx 21.5), som ikke må afrundes væk.
                    putExtra(RangeControlActivity.EXTRA_CURRENT_VALUE_PRECISE, current)
                    putExtra(RangeControlActivity.EXTRA_MIN_VALUE_PRECISE, min)
                    putExtra(RangeControlActivity.EXTRA_MAX_VALUE_PRECISE, max)
                    if (actionDomain == "number" || actionDomain == "input_number") {
                        putExtra(RangeControlActivity.EXTRA_UNIT_SUFFIX, unit)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            base.clickable(actionStartActivity(intent))
        }
        "TEXT" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val intent = Intent(context, TextControlActivity::class.java).apply {
                putExtra(TextControlActivity.EXTRA_ENTITY_ID, actionEntityId)
                putExtra(TextControlActivity.EXTRA_LABEL, rangeLabel)
                putExtra(TextControlActivity.EXTRA_CURRENT_VALUE, actionState.state)
                putExtra(TextControlActivity.EXTRA_MAX_LENGTH, attrs.optInt("max", 255))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            base.clickable(actionStartActivity(intent))
        }
        "DATETIME" -> {
            val attrs = try { JSONObject(actionState.attributesJson) } catch (_: Exception) { JSONObject() }
            val intent = Intent(context, DateTimeControlActivity::class.java).apply {
                putExtra(DateTimeControlActivity.EXTRA_ENTITY_ID, actionEntityId)
                putExtra(DateTimeControlActivity.EXTRA_HAS_DATE, attrs.optBoolean("has_date", true))
                putExtra(DateTimeControlActivity.EXTRA_HAS_TIME, attrs.optBoolean("has_time", true))
                putExtra(DateTimeControlActivity.EXTRA_CURRENT_VALUE, actionState.state)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            base.clickable(actionStartActivity(intent))
        }
        "TRIGGER" -> {
            val service = when (actionDomain) {
                "automation" -> "trigger"
                "input_button" -> "press"
                else -> "turn_on" // scene, script
            }
            base.clickable(
                actionRunCallback<TriggerEntityAction>(
                    actionParametersOf(
                        TriggerEntityAction.entityIdKey to actionEntityId,
                        TriggerEntityAction.domainKey to actionDomain,
                        TriggerEntityAction.serviceKey to service,
                    )
                )
            )
        }
        // Ukendt/uventet action-værdi (bør ikke forekomme — compatibleActionsFor begrænser hvad
        // der kan gemmes) → ingen klik-handling i stedet for at antage TRIGGER.
        else -> base
    }
}
