# Widget active-styling redesign — "farven flytter fra baggrund til indhold"

**Dato:** 2026-07-11
**Status:** Godkendt (design), afventer implementeringsplan
**Berører:** `MultiEntityWidget` (den eneste widget med entity-rækker efter v0.2.68-konvergeringen)

## Baggrund / motivation

I dag får en aktiv/tændt on/off-entitet **fuld solid primary-baggrund** (`surfaceFor`,
`MultiEntityRendering.kt`). Brugeren oplever det som for voldsomt — særligt device_tracker
("nogen er hjemme") fylder hele rækken med farve. Hjemme-dashboards (HA's egne bubble-cards)
bruger i stedet farve på **ikon + tekst** og lader baggrunden være neutral. Denne ændring
flytter aktiv-signalet fra baggrunden til indholdet, med få bevidste undtagelser hvor en fuld
baggrund er ønsket (climate der varmer; en tænd/sluk-chip).

Ændringen er **ren rendering** — ingen Room-migration, ingen config-UI, ingen ny data.

## Beslutningstabel (den endelige styling)

Alle farver er tema-bevidste (mørkt/lyst). "primary" følger det valgte farvetema
(Blå/Grøn/Lilla/…); de neutrale/faded/chip-flader er farve-neutrale (delt på tværs af temaer,
som de eksisterende neutrale roller).

| Element / tilstand | Baggrund | Ikon | Label (navn) | Status/værdi |
|---|---|---|---|---|
| **Hoved-slot, aktiv** (on/off-domæne "tændt") | række-neutral | primary | neutral | primary |
| **Hoved-slot, inaktiv** ("slukket") | række-neutral | neutral | neutral | neutral |
| **Hoved-slot, info-domæne** (sensor/number/scene/script m.fl.) | række-neutral | neutral | neutral | neutral |
| **Hoved-slot, climate der VARMER** (`hvac_action=="heating"`) | **fuld orange** `#FF6D00` | hvid | hvid | hvid |
| **Hoved-slot, utilgængelig** | række-neutral | **faded grå** | faded grå | faded grå |
| **Chip, TOGGLE tændt** | **fuld primary** | onPrimary | — | onPrimary |
| **Chip, aktiv ikke-toggle** (fx device_tracker "hjemme") | **chip-neutral** (subtilt mørkere) | primary | dæmpet | primary |
| **Chip, TOGGLE slukket / inaktiv / info** | chip-neutral | dæmpet | dæmpet | dæmpet |
| **Chip, climate der varmer** | fuld orange | hvid | hvid | hvid |
| **Chip, utilgængelig** | chip-neutral | faded grå | faded grå | faded grå |

**Kaldenavn på hoved-stilen:** "D" (ikon + status farvet, label neutral) — valgt fremfor
"B" (også label farvet) og "C" (kun ikon farvet) efter side-om-side-vurdering: D er kompromiset
mellem tydelighed og ro.

**Borders:** ingen. CTA-affordance (kant = trykbar) blev fravalgt fordi opsætter og bruger er
samme person — der er ingen fremmed bruger at "lære" hvad der er trykbart. Kant-**kapaciteten**
bevares dog i koden (se arkitektur) til en fremtidig tema-editor.

**Chip-adskillelse:** chip-fladen er **subtilt** mørkere end hoved-rækken + chip-teksten er
dæmpet ("mørkere hvid"), så en chip løfter sig visuelt fra rækken (de flyder sammen i dag, hvor
begge bruger samme neutrale farve). Fast (ikke konfigurerbar) i denne omgang — se "Fremtidigt".

## Konkrete farveværdier

De aktive farver (`primary`/`onPrimary`) læses uændret fra `GlanceTheme.colors` og respekterer
dermed farvetema-valget automatisk. Følgende er NYE, farve-neutrale værdier (samme i alle
farvetemaer, forskellig pr. lys/mørk) der skal defineres som tema-bevidste `ColorProvider`'e i
`WidgetColors` (samme mønster som eksisterende `frameBackground`/`refreshOverlay`):

| Rolle | Mørkt | Lyst | Afledt af |
|---|---|---|---|
| Række-neutral baggrund | `#40484C` | `#E3E8EC` | = eksisterende `surfaceVariant` (uændret) |
| Række-neutral tekst/ikon | `#C0C8CC` | `#40484C` | = eksisterende `onSurfaceVariant` (uændret) |
| **Chip-neutral baggrund** | `#363B40` | `#D6DCE1` | NY — subtilt mørkere end række |
| **Chip dæmpet tekst/ikon** | `#AEB6BA` | `#4A535B` | NY — dæmpet ift. onSurfaceVariant |
| **Faded (utilgængelig)** | `#7A8085` | `#9AA1A8` | NY — nedtonet grå |
| Heating orange | `#FF6D00` | `#FF6D00` | = eksisterende `WidgetColors.heatingFill` (uændret) |
| onHeating | `#FFFFFF` | `#FFFFFF` | = eksisterende `WidgetColors.onHeating` (uændret) |

De præcise NYE hex-værdier må finjusteres i kode/QA (som tidligere farve-nuancer), men
lys/mørk-parringen og "subtilt mørkere + dæmpet"-forholdet er det bindende designkrav.

## Hvad ændrer sig i forhold til i dag

- **Fuld primary-baggrund på aktive hoved-rækker forsvinder** → bliver D. Hoved-rækkens
  baggrund er nu ALTID række-neutral (kun ikon/status-farve skiller aktiv fra inaktiv). Eneste
  hoved-række med ikke-neutral baggrund er climate-varmer (fuld orange); utilgængelig har også
  neutral baggrund, men med faded (nedtonet grå) indhold.
- **Chips får en distinkt (subtilt mørkere) flade + dæmpet tekst** i stedet for at dele
  rækkens neutrale farve.
- **"Outline når slukket"-ringen på on/off-chips fjernes** (v0.2.42/48-adfærd) — erstattes af
  den dæmpede neutrale chip-styling. Ingen synlige borders længere.
- **Utilgængelig skifter fra fuld rød error-container til nedtonet grå** (faded).
- **Climate-varmer (fuld orange) er UÆNDRET** — bevidst genbesøgt og bekræftet: den skal være
  det eneste "høje" element, netop fordi alt andet nu er stilfærdigt ("nu varmes der sgu'").

## Arkitektur — struktureret til en fremtidig tema-editor

Designmål (brugerkrav): en senere tema-editor skal kunne lade brugeren angive primær-,
baggrunds- og chip-baggrundsfarve, slå border på chips til/fra, osv. **uden** en
arkitektur-omskrivning. Derfor:

1. **Ét farve-sæt-struct** samler alle styling-input:
   `primary`, `onPrimary`, `rowBg`, `rowText`, `chipBg`, `chipText`, `faded`, `heating`,
   `onHeating`, `error`(reserveret), og `showChipBorder: Boolean`.
   I dag fyldes det fra `GlanceTheme.colors` + de nye `WidgetColors`-konstanter. Senere kan
   samme struct fyldes fra bruger-konfig. Dette er sømmen (seam) tema-editoren hænger på.

2. **Én style-resolver** afløser/udvider `surfaceFor`: tager (tilstand: aktiv/inaktiv/heating/
   unavailable/info, `isChip: Boolean`, `isToggle: Boolean`) + farve-sæt-structen → returnerer
   et fladt resultat: `bg`, `iconColor`, `labelColor`, `statusColor`, `showBorder`.
   Al betinget farvelogik bor ÉT sted; `SlotRow`/`SecondaryChip` læser kun det flade resultat.

3. **Border-kapaciteten bevares.** Den eksisterende 2-lags `StatefulSurface` (ydre ring-Box +
   indre fyld-Box) beholdes og drives af `showBorder`-flaget fra resolveren, som i denne omgang
   ALTID er `false`. Tema-editoren tænder det uden ny layout-kode.

Ingen del af tema-editoren bygges nu (YAGNI) — kun strukturen der gør den billig. Sporet er
noteret som separat opgave.

## Filer der berøres

- `widget/common/WidgetColors.kt` — nye tema-bevidste `ColorProvider`'e (chipBg, chipText,
  faded) + evt. farve-sæt-structen.
- `widget/multientity/MultiEntityRendering.kt` — `surfaceFor` → ny resolver; `SlotRow` og
  `SecondaryChip` bruger fladt resultat; fjern "outline når slukket"-grenen; behold
  `StatefulSurface` men border-drevet af flag.
- Ingen ændringer i data-lag, config-UI, manifest, Room. Ingen migration.

## Testing / QA

- **Unit:** resolveren er en ren funktion (tilstand + farve-sæt → fladt resultat) → dæk hver
  tilstands-kombination (aktiv/inaktiv/info/heating/unavailable × chip/row × toggle) med tests,
  inkl. at `showBorder` altid er `false` i denne version.
- **Emulator (`pixel_test`, ægte HA):** placeret multi-widget med blandede domæner — verificér
  hver tilstand fra beslutningstabellen i BÅDE lyst og mørkt tema (skift via app-tema-vælgeren),
  inkl. climate-varmer (orange) og en utilgængelig entitet (faded).
- **Device (Galaxy S23, `adb install -r`):** samme visuelle gennemgang på rigtig enhed/Nova,
  begge temaer.
- Følg `docs/ux-process.md` og den iterative rettelses-workflow i `CLAUDE.md`.

## Uden for scope (parkeret — separate opgaver)

- **Skydende værdier / delvis fyld-bjælke** (bubble-card-slidere: cover-position, lysstyrke,
  temp-i-range som baggrundsfyld). Ny Glance-render-teknik, eget spor.
- **Tema-editor** (bruger-angivne farver + chip-border-toggle). Denne ændring *strukturerer* for
  den, men bygger den ikke.
- **Chip-adskillelse valgbar pr. row-entity** (fast subtil nu).
- Farve på climate-KØLING (kun varme farves; køling forbliver neutral — climate `isActive` er i
  praksis aldrig true, jf. v0.2.34-fund).

## Versionsnote

Ny patch-version (næste efter v0.2.72) med `versionCode`/`versionName`-bump i
`app/build.gradle.kts` FØR build, jf. projektkonvention.
