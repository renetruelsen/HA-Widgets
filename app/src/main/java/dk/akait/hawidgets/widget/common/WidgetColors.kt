package dk.akait.hawidgets.widget.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders as material3ColorProviders
import androidx.glance.unit.ColorProvider
import dk.akait.hawidgets.data.SecureStore

/** Hvilken [ColorProviders]-konstruktion et (farvetema, tema-tilstand)-par resulterer i. */
internal enum class ColorMode { DYNAMIC, FORCED_LIGHT, FORCED_DARK, SYSTEM_PAIR }

/**
 * Ren beslutningsfunktion (ingen Context/Android-afhængighed, unit-testbar): kun Blå+System bevarer
 * den historiske Android-dynamiske Material You-farve. Enhver anden kombination bruger presettets
 * faste lys/mørk-par — farvetema-valget vinder altid over systemets dynamiske farve, jf. spec.
 */
internal fun resolveColorMode(colorTheme: String, themeMode: String): ColorMode = when {
    colorTheme == SecureStore.COLOR_BLUE && themeMode == SecureStore.THEME_SYSTEM -> ColorMode.DYNAMIC
    themeMode == SecureStore.THEME_LIGHT -> ColorMode.FORCED_LIGHT
    themeMode == SecureStore.THEME_DARK -> ColorMode.FORCED_DARK
    else -> ColorMode.SYSTEM_PAIR
}

/**
 * Central kilde til tema-bevidste farver for ALLE Glance-widgets.
 *
 * Baggrund: hver widget wrapper sit indhold i [GlanceTheme] og læser farver via
 * `GlanceTheme.colors.X`. Uden argumenter bruger [GlanceTheme] Glance's
 * [DynamicThemeColorProviders] — dvs. dynamiske Material You-farver (API 31+) der følger
 * systemets nattilstand. Det er den HISTORISKE adfærd.
 *
 * Det globale tema-valg ([SecureStore.themeMode]) skal kunne TVINGE lys eller mørk
 * uafhængigt af systemet. Løsningen: i stedet for at ændre alle `GlanceTheme.colors.X`-
 * opslag (dusinvis af steder, høj regressionsrisiko) skifter hver widget kun sin
 * wrapper fra `GlanceTheme { … }` til [WidgetGlanceTheme]`(context) { … }`. Denne
 * vælger de rigtige [ColorProviders]:
 *  - "system" → NØJAGTIG den nuværende adfærd (Glance's dynamiske day/night-providers),
 *    så majoriteten af brugere ser ingen visuel ændring.
 *  - "light"/"dark" → et FAST [ColorProviders] hvor day- og night-siden er identiske,
 *    så widgetten ignorerer systemets nattilstand og altid tegner det valgte tema.
 *
 * Widgets gen-renderes efter et tema-skift ved at MainActivity kalder `updateAll()` på
 * hver provider-klasse (ADR-5) — [themeMode] bor i SharedPreferences, ikke i Room, så
 * der er ingen reaktiv Flow at hænge på.
 */
object WidgetColors {

    /** Fyld-/indhold-farve for en climate-række/chip der FAKTISK varmer (hvac_action == "heating",
     * brugerønske v0.2.48) — rød i stedet for climate's normale blå. Fast (tema-uafhængig); hvidt
     * indhold har god kontrast i begge temaer. Nuance v0.2.57: #FF6D00 — matcher Home Assistants
     * egen orange varme-farve (brugerønske, sammenlignet direkte mod et HA-dashboard-skærmbillede
     * med en climate-kortets "Aktuel temp"-badge). Erstatter den tidligere dæmpede rose (#CF6679,
     * v0.2.50). */
    val heatingFill: ColorProvider = ColorProvider(Color(0xFFFF6D00))
    val onHeating: ColorProvider = ColorProvider(Color(0xFFFFFFFF))

    /** Chips PÅ en varmende (fuld orange) hoved-række bruger disse FASTE farver (tema-uafhængige,
     * ligesom [heatingFill]/[onHeating] — den orange baggrund er selv tema-uafhængig). En
     * TOGGLE-tændt chip får den fulde [heatingChipAccent]-flade (skiller sig ud som kontakt); alt
     * andet chip-indhold (aktiv D, info, TOGGLE-slukket) bruger [heatingChipDim] på den normale
     * chip-scrim. Utilgængelig bruger [heatingChipUnavailable] (lysere end den normale FADED-grå,
     * som ville være ulæselig på orange). Nuancer valgt via mockup-sammenligning m. bruger (v0.2.73). */
    val heatingChipAccent: ColorProvider = ColorProvider(Color(0xFFFF9F4D))
    val heatingChipDim: ColorProvider = ColorProvider(Color(0xFFFFE8D6))
    val heatingChipUnavailable: ColorProvider = ColorProvider(Color(0xFFD8D3CE))

    /**
     * Chip-flade: subtilt MØRKERE end hoved-rækkens neutrale baggrund, så en chip løfter sig visuelt
     * fra rækken (de deler ellers samme neutrale farve og flyder sammen). Realiseret som en let mørk
     * scrim (samme værdi i begge temaer) der lægges OVEN PÅ rækkens baggrund → adapterer automatisk
     * til den underliggende farve, inkl. Blå+system's dynamiske Material You-flade. Fast (ikke
     * konfigurerbar) i v0.2.73; en fremtidig tema-editor kan gøre den bruger-styret.
     */
    val chipBackground: ColorProvider = ColorProvider(Color(0x22000000))

    /** Dæmpet ("mørkere hvid") tekst/ikon på en chip — nedtonet ift. rækkens onSurfaceVariant, så
     * chip-indhold underordner sig hoved-rækken. Fast lys/mørk-par, tvunget-tema-bevidst. */
    fun chipDimText(context: Context): ColorProvider =
        themed(context, day = Color(0xFF4A535B), night = Color(0xFFAEB6BA))

    /** Nedtonet grå for en UTILGÆNGELIG entitet (v0.2.73: erstatter den fulde røde error-container
     * — en offline entitet er ikke en fejl brugeren skal rette). Fast lys/mørk-par. */
    fun fadedContent(context: Context): ColorProvider =
        themed(context, day = Color(0xFF9AA1A8), night = Color(0xFF7A8085))

    /** Fælles lys/mørk-vælger: tvinger en side ved forced light/dark (så farven matcher
     * GlanceTheme, som også tvinges), ellers en dynamisk day/night-provider. Samme mønster som
     * [frameBackground]/[refreshOverlay]. */
    private fun themed(context: Context, day: Color, night: Color): ColorProvider =
        when (SecureStore.get(context).themeMode) {
            SecureStore.THEME_LIGHT -> ColorProvider(day)
            SecureStore.THEME_DARK -> ColorProvider(night)
            else -> androidx.glance.color.ColorProvider(day = day, night = night)
        }

    /** [ColorProviders] svarende til det aktive farvetema + tema-tilstand. Se [resolveColorMode]
     * for selve beslutningslogikken (testet separat uden Context). */
    fun providers(context: Context): ColorProviders {
        val store = SecureStore.get(context)
        return providersFor(store.widgetColorTheme, store.themeMode)
    }

    /** Ren (Context-fri) udgave: (farvetema, tema-tilstand) → [ColorProviders]. Bruges af den
     * reaktive [WidgetGlanceTheme] med værdier fra `SecureStore.observeThemeSettings()`, så et
     * tema-/farveskift altid udløser en re-komposition med de nye farver. */
    fun providersFor(colorTheme: String, themeMode: String): ColorProviders {
        val preset = presetFor(colorTheme)
        return when (resolveColorMode(colorTheme, themeMode)) {
            ColorMode.DYNAMIC -> DynamicThemeColorProviders
            ColorMode.FORCED_LIGHT -> material3ColorProviders(light = preset.light, dark = preset.light)
            ColorMode.FORCED_DARK -> material3ColorProviders(light = preset.dark, dark = preset.dark)
            ColorMode.SYSTEM_PAIR -> material3ColorProviders(light = preset.light, dark = preset.dark)
        }
    }

    /**
     * Tema-bevidst udgave af MultiEntityWidget's ramme-baggrund. Bevarer PRÆCIS de
     * eksisterende day/night-alpha-værdier for "system"-mode (ingen visuel regression),
     * men låser til én side når temaet tvinges.
     */
    fun frameBackground(context: Context): ColorProvider {
        val day = Color(0x1F1C1B1F)
        val night = Color(0x1FE6E1E5)
        return when (SecureStore.get(context).themeMode) {
            SecureStore.THEME_LIGHT -> ColorProvider(day)
            SecureStore.THEME_DARK -> ColorProvider(night)
            else -> androidx.glance.color.ColorProvider(day = day, night = night)
        }
    }

    /**
     * Tema-bevidst udgave af MultiEntityWidget's refresh-strips halvtransparente
     * overlay-baggrund (ADR-3: stripen flyder OVEN PÅ listen, ikke som en fast bjælke
     * under den, så indhold skimtes bag den under scroll). ~65% alpha hvid/mørkegrå —
     * "glas"-effekt, hverken uigennemsigtig eller usynlig. Samme mønster som
     * [frameBackground]: fast side ved tvunget lys/mørk, dynamisk day/night ellers.
     */
    fun refreshOverlay(context: Context): ColorProvider {
        val day = Color(0xA6FFFFFF)
        val night = Color(0xA6202124)
        return when (SecureStore.get(context).themeMode) {
            SecureStore.THEME_LIGHT -> ColorProvider(day)
            SecureStore.THEME_DARK -> ColorProvider(night)
            else -> androidx.glance.color.ColorProvider(day = day, night = night)
        }
    }
}

/**
 * Erstatning for et bart `GlanceTheme { … }`-kald: vælger farve-providers ud fra det
 * globale tema-valg, men lader alle `GlanceTheme.colors.X`-opslag i indholdet stå
 * uændret. For "system" er adfærden identisk med det tidligere `GlanceTheme { … }`.
 *
 * REAKTIVT: temaet collectes fra [SecureStore.observeThemeSettings] i stedet for at læses
 * imperativt én gang. Uden dette re-læste `updateAll()` kun temaet hvis kompositionen
 * tilfældigvis re-komponerede af anden grund → tema-/farveskift "trådte ikke altid i kraft
 * øjeblikkeligt" (bekræftet via logcat på S23). Nu udløser selve pref-ændringen en emission →
 * re-komposition → nye farver, uanset om noget andet ændrer sig.
 */
@Composable
fun WidgetGlanceTheme(context: Context, content: @Composable () -> Unit) {
    val store = remember { SecureStore.get(context) }
    val settings by store.observeThemeSettings().collectAsState(initial = store.themeSettings())
    GlanceTheme(colors = WidgetColors.providersFor(settings.colorTheme, settings.themeMode), content = content)
}
