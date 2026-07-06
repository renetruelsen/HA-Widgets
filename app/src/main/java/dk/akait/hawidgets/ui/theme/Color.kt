package dk.akait.hawidgets.ui.theme

import androidx.compose.ui.graphics.Color

// Seeded from the app's existing brand blue (ic_launcher_background, #03A9F4)
// instead of Material 3's default purple baseline.
val HaBluePrimary = Color(0xFF0B6FA4)
val HaBlueOnPrimary = Color(0xFFFFFFFF)
val HaBluePrimaryContainer = Color(0xFFCDE5FF)
val HaBlueOnPrimaryContainer = Color(0xFF001E2E)
val HaBlueSurfaceVariant = Color(0xFFE3E8EC)
val HaBlueOnSurfaceVariant = Color(0xFF40484C)

// Dark-tema-palet, afledt af samme brand-blå familie. Primær-tonen er lysnet
// (#7FC3E8, jf. den brugergodkendte mockup) så den har tilstrækkelig kontrast mod
// mørke flader — en mørk primær ville forsvinde. De øvrige roller følger Material3's
// mørk-tema-konventioner (lav-luminans surfaces, dæmpet on-farver, mørk error-container
// med lys tekst). De konkrete non-primær-værdier var eksplicit overladt til at blive
// "finjusteret i kode" i design-dokumentet.
val HaBlueDarkPrimary = Color(0xFF7FC3E8)
val HaBlueDarkOnPrimary = Color(0xFF00344C)
val HaBlueDarkPrimaryContainer = Color(0xFF004C6E)
val HaBlueDarkOnPrimaryContainer = Color(0xFFCDE5FF)
val HaBlueDarkSurfaceVariant = Color(0xFF40484C)
val HaBlueDarkOnSurfaceVariant = Color(0xFFC0C8CC)
val HaBlueDarkBackground = Color(0xFF121417)
val HaBlueDarkOnBackground = Color(0xFFE2E2E5)
val HaBlueDarkSurface = Color(0xFF121417)
val HaBlueDarkOnSurface = Color(0xFFE2E2E5)
val HaBlueDarkError = Color(0xFFFFB4AB)
val HaBlueDarkOnError = Color(0xFF690005)
val HaBlueDarkErrorContainer = Color(0xFF93000A)
val HaBlueDarkOnErrorContainer = Color(0xFFFFDAD6)
