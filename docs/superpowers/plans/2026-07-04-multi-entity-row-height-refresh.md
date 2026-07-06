# MultiEntityWidget: SizeMode.Responsive fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix a confirmed Nova Launcher bug where `MultiEntityWidget`'s adaptive row-height
logic renders using the wrong (landscape) composed layout while the phone is in portrait,
by switching `sizeMode` from `SizeMode.Exact` to `SizeMode.Responsive` with a fixed set of
height buckets.

**Architecture:** `MultiEntityWidget.kt`'s row-height formula already reads
`LocalSize.current.height` and needs zero changes — only the `sizeMode` declaration and the
widget-info XML's `maxResizeHeight` change. This plan does NOT touch the already-implemented
and already-QA'd row-height math, refresh strip, config toggle, or Room migration — those
are done (see `docs/superpowers/specs/2026-07-04-multi-entity-row-height-refresh-design.md`
§1-2). This plan covers §3 only.

**Tech Stack:** Kotlin, Jetpack Glance (`SizeMode.Responsive`, `DpSize`).

**Spec:** `docs/superpowers/specs/2026-07-04-multi-entity-row-height-refresh-design.md`

## Global Constraints

- Install with `adb install -r` only — **never** `adb uninstall` (wipes `SecureStore` token
  + all widget configs on both the emulator and the Galaxy S23 test device).
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` before building (already at
  35/"0.2.35" from the earlier part of this feature — bump once more to 36/"0.2.36" for
  this fix since it's landing as a separate verified change).
- "Meld aldrig fikset uden bevis" — build, then verify on the emulator, then verify the
  specific portrait-resize scenario on the Galaxy S23 (Nova Launcher) that exposed the bug,
  before considering this done.
- Never run destructive device operations. Prefer read-only `dumpsys`/`uiautomator dump`
  before any tap/drag on the physical S23 to avoid mis-taps on unrelated home-screen icons
  (this happened twice during the investigation that led to this plan).

---

### Task 1: Switch `sizeMode` to `Responsive` with height buckets

**Files:**
- Modify: `app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidget.kt:89-95`
- Modify: `app/src/main/res/xml/multi_entity_widget_info.xml`
- Modify: `app/build.gradle.kts` (version bump)

**Interfaces:**
- Consumes: nothing new — `MultiEntityContent`'s existing row-height formula (already reads
  `LocalSize.current.height`) is unaffected and unchanged.
- Produces: nothing new is exposed to other files; this is a self-contained declaration
  change on the `MultiEntityWidget` class.

- [ ] **Step 1: Add the `DpSize` import and change `sizeMode`**

Current code (`MultiEntityWidget.kt` lines 89-95):

```kotlin
class MultiEntityWidget : GlanceAppWidget() {

    // SizeMode.Exact: indholdet (ramme + LazyColumn af fuld-bredde rækker) bruger almindelige
    // fillMaxSize/fillMaxWidth-modifiers uden custom pixel-matematik — den kontinuerlige
    // størrelse Glance rapporterer bruges derfor direkte, uden diskrete buckets. Se
    // docs/widget-settings-spec.md §9.
    override val sizeMode = SizeMode.Exact
```

Replace with:

```kotlin
class MultiEntityWidget : GlanceAppWidget() {

    // SizeMode.Responsive (ikke Exact): Exact komponerer altid BÅDE en portræt- og en
    // landskabs-udgave (Androids indbyggede RemoteViews(landscape, portrait)-mekanisme),
    // og launcheren vælger selv hvilken der vises ud fra Configuration-orientering ved
    // inflation. På Galaxy S23 + Nova Launcher blev landskabs-udgaven konsekvent vist SELV
    // I PORTRÆT-TILSTAND (bekræftet via midlertidig logging under device-QA — se
    // docs/superpowers/specs/2026-07-04-multi-entity-row-height-refresh-design.md §3),
    // hvilket fik rækkerne til at bruge en langt mindre højde end den faktiske boks.
    // Responsive bruger på API 31+ en faktisk størrelses-baseret vælger blandt de
    // deklarerede buckets i stedet for en orienterings-baseret parring, hvilket omgår
    // fejlvalget. Under API 31 opfører Responsive sig som Exact (samme eksponering som før
    // — ingen regression). Kun ÉN bredde (244dp, matcher minWidth i widget-info-xml'en) —
    // rækkehøjde-formlen bruger udelukkende LocalSize.current.height, bredden håndteres af
    // fillMaxWidth()/defaultWeight() uanset bucket-bredde.
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(244.dp, 56.dp),
            DpSize(244.dp, 130.dp),
            DpSize(244.dp, 200.dp),
            DpSize(244.dp, 270.dp),
        )
    )
```

`DpSize` needs its own import (`Dp` is already imported at line 10 of the file, but
`DpSize` is a separate type). Add it next to the existing `androidx.compose.ui.unit.*`
imports (line 10-12):

```kotlin
import androidx.compose.ui.unit.DpSize
```

- [ ] **Step 2: Tighten `maxResizeHeight` in the widget-info XML**

File: `app/src/main/res/xml/multi_entity_widget_info.xml`. Current line:

```xml
    android:maxResizeHeight="400dp"
```

Change to:

```xml
    android:maxResizeHeight="270dp"
```

(Matches the largest declared `DpSize` bucket height from Step 1 — prevents a user from
ever resizing past what we've bucketed for, which would otherwise recreate a smaller
version of the original gap problem at the extreme end.)

- [ ] **Step 3: Bump the version**

File: `app/build.gradle.kts`. Change:

```kotlin
        versionCode = 35
        versionName = "0.2.35"
```

to:

```kotlin
        versionCode = 36
        versionName = "0.2.36"
```

- [ ] **Step 4: Build**

Run:
```
JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.19.10-hotspot" ./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`. If it fails on the `DpSize`/`Dp` import, double check
`androidx.compose.ui.unit.DpSize` is the correct package (it is, per the Compose UI unit
package that `Dp`/`dp` already come from in this file).

- [ ] **Step 5: Emulator regression check**

Install on the running `pixel_test` emulator (reinstall, not uninstall):
```
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```
Take a screenshot of the existing placed multi-entity widget (appWidgetId 11 from the
earlier session, 3 `device_tracker` slots) and confirm it still renders without crashing
and still shows the reserved refresh strip. This confirms `Responsive` mode didn't break
anything that `Exact` mode was already handling correctly on this launcher.

- [ ] **Step 6: The decisive test — Galaxy S23 in portrait**

This is the scenario that originally exposed the bug. Install (reinstall) on the connected
S23:
```
adb -s R3CWC00JY4M install -r app/build/outputs/apk/debug/app-debug.apk
```
Before any tap/drag: run `adb -s R3CWC00JY4M shell uiautomator dump /sdcard/check.xml` and
pull it to get the current exact bounds of the widget's `ListView` and rows (same technique
used during the investigation) — do not guess coordinates from a screenshot alone.

Long-press the widget (zero-distance `input swipe x y x y 700` on a coordinate confirmed
from the dump to be inside the widget's `rootView` bounds, not on a neighboring icon), tap
"Tilpas størrelse", drag a resize handle to a height near one of the new bucket boundaries
(e.g. near 200dp), tap outside to commit, then re-dump and re-measure the rendered row
heights the same way as before.

Expected: the rows now expand to fill the box (no large empty gap between the last row and
the refresh strip), matching the emulator's already-correct behavior. This is the concrete
pass/fail signal for this entire plan.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/dk/akait/hawidgets/widget/multientity/MultiEntityWidget.kt \
        app/src/main/res/xml/multi_entity_widget_info.xml \
        app/build.gradle.kts
git commit -m "$(cat <<'EOF'
fix: MultiEntityWidget — SizeMode.Responsive to fix Nova portrait/landscape mismatch (v0.2.36)

SizeMode.Exact always composes both a portrait and landscape RemoteViews
pair; Nova Launcher was consistently displaying the landscape one while
the phone was in portrait, causing rows to compute far too short a
height. Responsive mode uses actual-size bucket matching on API 31+
instead of orientation-based pairing, confirmed fixed on a Galaxy S23.

EOF
)"
```
