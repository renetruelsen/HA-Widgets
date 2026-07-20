# Google Play — release-assets (Android)

Alt til Google Play Store for **HA Widgets** samlet ét sted.

- **applicationId:** `dk.rtr.hawidgets` (permanent på Play — kan ikke ændres efter første upload)
- **Aktuel version:** `0.2.100` (versionCode `100`)
- **Privatlivspolitik (live):** https://rtr.dk/projects/ha-widgets/privacy

## Indhold

| Sti | Hvad | I git? |
|-----|------|--------|
| `ha-widgets-<version>-<code>.aab` | Den signerede App Bundle klar til upload | ❌ git-ignoreret (`*.aab`) |
| `play-icon-512.png` | **App-ikon 512×512** til butikssiden (blå hus-af-widgets) | ✅ |
| `feature-graphic-1024x500.png` | **Feature graphic 1024×500** (banner øverst på butikssiden) | ✅ |
| `feature-graphic.src.html` | Kilde til feature-graphic — rediger + gen-render (se nedenfor) | ✅ |
| `signing/upload-keystore.jks` | **Upload-nøgle** (RSA 2048, alias `upload`, gyldig til 2053) | ❌ git-ignoreret (`*.jks`) |
| `../../keystore.properties` (repo-rod) | Nøgle-sti + passwords som Gradle læser ved release-build | ❌ git-ignoreret |
| `store-listing.md` | Butiks-tekster (titel/kort/fuld beskrivelse) på en/da/sv | ✅ |
| `privacy-policy.md` | **Privatlivspolitik** (kilde) — live på https://rtr.dk/projects/ha-widgets/privacy | ✅ |
| `data-safety.md` | Play Console **Data safety**-formular, udfyldningsklar reference | ✅ |
| `screenshots/` | Phone + tablet7 + tablet10 screenshots på en/da/sv | ✅ |
| `<locale>/changelogs/<versionCode>.txt` | Release-noter pr. locale (fastlane-format), indsættes manuelt i Play Console | ✅ |

## ⚠️ Backup af upload-nøglen — VIGTIGT

`signing/upload-keystore.jks` + passwordene i `keystore.properties` signerer alle uploads.
**Tag en sikker backup af begge nu** (uden for maskinen — fx en password-manager + krypteret
cloud-kopi af `.jks`-filen). Uden dem kan du ikke uploade opdateringer med samme nøgle.

Det er en **upload-nøgle**, ikke app-signeringsnøglen: bruger du Google Play App Signing (anbefalet,
default), holder Google den permanente app-signeringsnøgle, og upload-nøglen kan **nulstilles** via
Play Console → hvis den mistes, hvis nødvendigt. Du kan derfor også trygt gen-generere den med dit
eget password før første upload, hvis du foretrækker det (se kommandoen nederst).

Upload-certifikatets fingeraftryk (til dine egne noter / Play):
`SHA256: 9B:1B:69:2A:58:DC:5D:FB:79:16:98:0A:83:42:E3:46:A1:69:D7:19:2B:85:65:39:F1:61:8E:BE:79:45:6E:11`

## Sådan bygger du AAB'en igen

```
JAVA_HOME=<jdk17> ./gradlew bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```
Release-buildet signeres automatisk når `keystore.properties` findes i repo-roden. Findes den ikke
(fx på en anden maskine/CI), bygger release usigneret — så kopiér `keystore.properties` +
`signing/upload-keystore.jks` med over (aldrig via git).

## Gen-render feature graphic

Rediger `feature-graphic.src.html` og render til præcis 1024×500 med headless Chrome:

```
chrome --headless=new --hide-scrollbars --force-device-scale-factor=1 \
  --window-size=1024,500 --screenshot=feature-graphic-1024x500.png feature-graphic.src.html
```

## Publiceringskrav (alle gjort)

- [x] **App-ikon 512×512** — `play-icon-512.png` (matcher det nye adaptive launcher-ikon)
- [x] **Feature graphic 1024×500** — `feature-graphic-1024x500.png` (samme stil som ikonet)
- [x] Mindst **2 telefon-screenshots** pr. sprog (findes — verificér de viser reelle widgets, ikke onboarding)
- [x] **Privatlivspolitik** — live på https://rtr.dk/projects/ha-widgets/privacy; indsæt URL'en i
      Play Console (App content → Privacy policy)
- [x] **Content rating**-spørgeskema (udfyldes i Play Console)
- [x] **Data safety**-formular (token gemmes lokalt i AndroidKeyStore, sendes kun til brugerens egen HA;
      diagnostik-log til rtr.dk ved "Report a problem" — deklarér dette)
- [x] **Kategori:** House & Home (matcher Home Assistants egen officielle companion-app på Play) + kontakt-email (rtr@rtr.dk)
- [x] Beslut om varemærke-navnet "Home Assistant" bruges i titlen (se note i `store-listing.md`)
- [x] **Play-advarsel "native debug-symboler" — ACCEPTERET (ignoreres):** appens eneste native
      kode er en færdig-strippet AndroidX-prebuilt (`libandroidx.graphics.path.so`, transitivt fra
      Compose-grafik). Den indeholder ingen symboler at udtrække (verificeret med clean
      `bundleRelease` + `debugSymbolLevel=FULL` → nul metadata), så advarslen kan ikke ryddes og er
      harmløs. Se forklarende kommentar i `app/build.gradle.kts`.

## Gen-generér upload-nøgle med eget password (valgfrit, kun før første upload)

```
keytool -genkeypair -v -keystore store_assets/android/signing/upload-keystore.jks \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=HA Widgets, O=rtr.dk, C=DK"
# indtast dit eget store/key-password, opdatér derefter keystore.properties
```
