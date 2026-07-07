package dk.akait.hawidgets.widget.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders as material3ColorProviders
import androidx.glance.unit.ColorProvider
import dk.akait.hawidgets.data.SecureStore
import dk.akait.hawidgets.ui.theme.HaWidgetsColorScheme
import dk.akait.hawidgets.ui.theme.HaWidgetsDarkColorScheme

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

    // Faste providers bygget af app'ens brand-color-schemes. Begge sider (day/night) er ens,
    // så et tvunget tema aldrig følger systemets nattilstand.
    private val lightProviders: ColorProviders =
        material3ColorProviders(light = HaWidgetsColorScheme, dark = HaWidgetsColorScheme)
    private val darkProviders: ColorProviders =
        material3ColorProviders(light = HaWidgetsDarkColorScheme, dark = HaWidgetsDarkColorScheme)

    /** [ColorProviders] svarende til det aktive tema-valg. system → dynamiske day/night
     * (uændret adfærd); light/dark → faste providers. */
    fun providers(context: Context): ColorProviders = when (SecureStore.get(context).themeMode) {
        SecureStore.THEME_LIGHT -> lightProviders
        SecureStore.THEME_DARK -> darkProviders
        else -> DynamicThemeColorProviders
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
 */
@Composable
fun WidgetGlanceTheme(context: Context, content: @Composable () -> Unit) {
    GlanceTheme(colors = WidgetColors.providers(context), content = content)
}
