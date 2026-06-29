# UX-proces — ha-widgets

Gælder for alle visuelle og interaktionsrelaterede ændringer i ha-widgets.

## Hvornår kræves en UX-spec?

En spec er påkrævet ved:
- Ny widget-type (config-flow + display)
- Ændring af eksisterende config-flow (felter, flow-rækkefølge, validering)
- Ændring af widget-display (compact/wide layout, farver, tekst)
- Ny interaktion (tap-action, swipe, long-press)
- Widget-picker metadata (previewImage, beskrivelse)

Ingen spec kræves for: fejlrettelser der ikke ændrer synlig adfærd, interne refaktoreringer, SyncWorker-ændringer.

## Workflow

```
UX-oplæg → Impl → UX-review-loop → QA-loop → Commit
```

### 1. UX-oplæg
- Skriv spec i `docs/widget-settings-spec.md` (eller tilhørende domæne-spec)
- Inkluder: problem, brugerflows, skærmbeskrivelser, feltdefinitioner, edge cases
- Inkluder: præcise værdier (dp, sp, maxLength, farvetokens fra GlanceTheme)
- Godkend spec med bruger inden implementation

### 2. Implementation
- Developer agent læser spec + relevante kildefiler
- Implementerer baseret på spec — ingen "skønsmæssige" UX-beslutninger
- Bygger: `./gradlew assembleDebug`
- Installer: `adb install -r`

### 3. UX-review-loop
- Tag screenshot af hvert skærmbillede/widget-tilstand
- Sammenlign mod spec punkt for punkt
- Afvigelser → ret kode eller opdater spec (med begrundelse) → gentag
- Loop slutter når alle spec-punkter er opfyldt

### 4. QA-loop
- Test på emulator (`pixel_test`): alle config-flows, widget-tilstande, tap-interaktioner
- Test på rigtig enhed (`adb install -r`): samme flows
- Ingen "fikset" erklæring før begge er grønne
- Se `CLAUDE.md#workflow-rettelser-og-release` for detaljer

### 5. Commit
- Commit message: `feat: <beskrivelse> (v<version>)`
- Opdater `CLAUDE.md` status-sektion

## Agentroller

| Agent | Ansvar |
|---|---|
| UX-agent | Skriver spec, UX-review |
| Developer agent | Impl baseret på spec, QA |
| Bruger | Godkender spec, godkender review-output |

## Spec-format

Se `docs/widget-settings-spec.md` for eksempel på korrekt spec-format.
Ny widget-type: kopiér template-sektionen og udfyld alle felter.
