package dk.rtr.hawidgets.widget.common

import androidx.compose.ui.graphics.Color
import dk.rtr.hawidgets.data.SecureStore
import dk.rtr.hawidgets.ui.theme.HaBlueOnSurfaceVariant
import dk.rtr.hawidgets.ui.theme.HaBlueSurfaceVariant
import dk.rtr.hawidgets.ui.theme.HaWidgetsColorScheme
import dk.rtr.hawidgets.ui.theme.HaWidgetsDarkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColorPresetsTest {

    @Test fun bluePresetReusesExistingAppScheme() {
        val preset = presetFor(SecureStore.COLOR_BLUE)
        assertEquals(HaWidgetsColorScheme, preset.light)
        assertEquals(HaWidgetsDarkColorScheme, preset.dark)
    }

    @Test fun greenPresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_GREEN)
        assertEquals(Color(0xFF1E8E3E), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFF8FDB94), preset.dark.primary)
        assertEquals(Color(0xFF00390D), preset.dark.onPrimary)
    }

    @Test fun purplePresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_PURPLE)
        assertEquals(Color(0xFF6750A4), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFFD0BCFF), preset.dark.primary)
        assertEquals(Color(0xFF381E72), preset.dark.onPrimary)
    }

    @Test fun orangePresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_ORANGE)
        assertEquals(Color(0xFF9C5700), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFFFFB870), preset.dark.primary)
        assertEquals(Color(0xFF552F00), preset.dark.onPrimary)
    }

    @Test fun redPresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_RED)
        assertEquals(Color(0xFFC2185B), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFFFFB2C8), preset.dark.primary)
        assertEquals(Color(0xFF5E1133), preset.dark.onPrimary)
    }

    @Test fun tealPresetHasExpectedPrimaryColors() {
        val preset = presetFor(SecureStore.COLOR_TEAL)
        assertEquals(Color(0xFF00696B), preset.light.primary)
        assertEquals(Color(0xFFFFFFFF), preset.light.onPrimary)
        assertEquals(Color(0xFF4FD8DA), preset.dark.primary)
        assertEquals(Color(0xFF00373A), preset.dark.onPrimary)
    }

    @Test fun nonBluePresetsShareNeutralSurfaceVariantWithBlue() {
        val preset = presetFor(SecureStore.COLOR_GREEN)
        assertEquals(HaBlueSurfaceVariant, preset.light.surfaceVariant)
        assertEquals(HaBlueOnSurfaceVariant, preset.light.onSurfaceVariant)
    }

    @Test fun unknownColorThemeFallsBackToBlue() {
        val preset = presetFor("not-a-real-theme")
        assertEquals(HaWidgetsColorScheme, preset.light)
        assertEquals(HaWidgetsDarkColorScheme, preset.dark)
    }
}
