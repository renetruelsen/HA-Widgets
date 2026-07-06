# ADR'er — Widget UX Pack (2026-07-06)

Fanget under grill-session af `docs/superpowers/specs/2026-07-06-widget-ux-pack-design.md`.

## ADR-1: Bekræft-dialog navngiver altid handlings-målet

**Kontekst:** En chip/slot kan vise én entitet men handle på en anden (v0.2.29).
**Beslutning:** Dialogteksten bruger ALTID handlings-målets friendly name ("Sluk Spa-pumpe?"),
aldrig den viste entitets navn.
**Begrundelse:** Dialogen er en sikkerhedsmekanisme — den skal beskrive hvad der faktisk sker,
ikke hvad brugeren kigger på.

## ADR-2: RANGE-forbedringer rammer den DELTE RangeControlActivity

**Kontekst:** `RangeControlActivity` bruges af både multi-widget og single-entity widgets
(Light/Climate/Cover).
**Beslutning:** Forbedringer (Variant B −/+ knapper, hvis valgt) gælder ALLE brugere af
aktiviteten. Variant A's config-valg implementeres kun i multi-config, men en evt.
felt-dialog bygges genbrugelig.
**Begrundelse:** Konsistens; forgrening af delt aktivitet er mere kode for dårligere UX.

## ADR-3: Refresh-overlay + bund-spacer i listen

**Kontekst:** Baren som halvtransparent overlay over `LazyColumn` kan permanent skjule
bunden af sidste række.
**Beslutning:** Usynligt spacer-element (24dp = bar-højden) sidst i listen. Fuldt
nedscrollet er alle rækker synlige over baren; glas-effekten ses under scroll/overflow.
**Begrundelse:** Ingen tilstands-afhængig placering (inkonsistens), intet informationstab.

## ADR-4: Tema-valg omfatter IKKE WebView-dashboardet

**Kontekst:** CLAUDE.md (v0.2.6) hævder `replyExternalConfig` sender `themes:{darkMode}` —
koden gør det ikke (verificeret 2026-07-06: kun capabilities sendes). WebView-kobling ville
være nyt arbejde.
**Beslutning:** Tema-valget (lys/mørk/system) gælder kun app-UI + widgets. Dashboardet
følger HA-serverens eget tema.
**Begrundelse:** Brugerbeslutning under grill; mindre scope. Kan tilføjes senere.
**Følgevirkning:** CLAUDE.md's v0.2.6-beskrivelse er forældet ift. koden — bør rettes ved
lejlighed (ikke del af denne opgave).

## ADR-5: Widget-re-render ved tema-skift via updateAll, ikke Room-Flow

**Kontekst:** Widgets rekomponerer i dag reaktivt via Room `Flow`. `themeMode` bor i
SecureStore (SharedPreferences), som ikke er reaktiv.
**Beslutning:** MainActivity kalder `updateAll<...>()` for alle widget-providers når
tema-valget ændres. Nova-quirken ("update ignoreres") gælder kun under PLACEMENT —
eksisterende widgets opdaterer fint.
**Begrundelse:** Undgår at flytte en app-indstilling ind i Room kun for reaktivitet.

## Ordliste (suppleret)

| Term | Betydning |
|---|---|
| **Slot** | Én række i multi-widgetten (hoved-entitet + op til 3 chips). Maks 5 pr. widget. |
| **Chip** | Sekundær info/handlings-element på en slot-række. Egen visning/handling. |
| **Visning / Handling** | De to uafhængige halvdele af en slot/chip-konfiguration: hvad der vises vs. hvad et tryk gør (kan pege på forskellige entiteter). |
| **Handlings-mål** | Den entitet en handling rammer (kan ≠ vist entitet). Bekræft-dialogen navngiver altid denne. |
| **Bekræft ved tryk** | Per-handling-option (TOGGLE/TRIGGER): tryk åbner ConfirmActionActivity i stedet for at udløse direkte. |
| **Refresh-strip/bar** | 24dp klikbar bjælke der synker alle entiteter; fremover halvtransparent overlay i bunden af listen. |
| **Auto-formatering** | Default værdi-visning: maks 1 decimal for tal, lokalt kort format for datetime. Overstyres pr. slot/chip. |
| **Tema-mode** | Global indstilling `light|dark|system` i SecureStore; styrer app-UI + widget-farver (ikke WebView, jf. ADR-4). |
