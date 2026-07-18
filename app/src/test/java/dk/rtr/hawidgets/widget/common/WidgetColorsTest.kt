package dk.rtr.hawidgets.widget.common

import dk.rtr.hawidgets.data.SecureStore
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColorsTest {

    @Test fun blueSystemUsesDynamicColors() {
        assertEquals(
            ColorMode.DYNAMIC,
            resolveColorMode(SecureStore.COLOR_BLUE, SecureStore.THEME_SYSTEM)
        )
    }

    @Test fun blueLightUsesForcedLight() {
        assertEquals(
            ColorMode.FORCED_LIGHT,
            resolveColorMode(SecureStore.COLOR_BLUE, SecureStore.THEME_LIGHT)
        )
    }

    @Test fun blueDarkUsesForcedDark() {
        assertEquals(
            ColorMode.FORCED_DARK,
            resolveColorMode(SecureStore.COLOR_BLUE, SecureStore.THEME_DARK)
        )
    }

    @Test fun greenSystemUsesSystemPairNotDynamic() {
        // Farvetema-valget skal vinde over Android's dynamiske farve for alle temaer UNDTAGEN Blå.
        assertEquals(
            ColorMode.SYSTEM_PAIR,
            resolveColorMode(SecureStore.COLOR_GREEN, SecureStore.THEME_SYSTEM)
        )
    }

    @Test fun greenLightUsesForcedLight() {
        assertEquals(
            ColorMode.FORCED_LIGHT,
            resolveColorMode(SecureStore.COLOR_GREEN, SecureStore.THEME_LIGHT)
        )
    }

    @Test fun greenDarkUsesForcedDark() {
        assertEquals(
            ColorMode.FORCED_DARK,
            resolveColorMode(SecureStore.COLOR_GREEN, SecureStore.THEME_DARK)
        )
    }

    @Test fun unknownColorThemeWithSystemBehavesLikeNonBlue() {
        assertEquals(
            ColorMode.SYSTEM_PAIR,
            resolveColorMode("not-a-real-theme", SecureStore.THEME_SYSTEM)
        )
    }
}
