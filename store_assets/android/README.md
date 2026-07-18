# Google Play — release-assets (Android)

Alt til Google Play Store for **HA Widgets** samlet ét sted.

- **applicationId:** `dk.rtr.hawidgets` (permanent på Play — kan ikke ændres efter første upload)
- **Aktuel version:** `0.2.96` (versionCode `96`)

## Indhold

| Sti | Hvad | I git? |
|-----|------|--------|
| `ha-widgets-<version>-<code>.aab` | Den signerede App Bundle klar til upload | ❌ git-ignoreret (`*.aab`) |
| `signing/upload-keystore.jks` | **Upload-nøgle** (RSA 2048, alias `upload`, gyldig til 2053) | ❌ git-ignoreret (`*.jks`) |
| `../../keystore.properties` (repo-rod) | Nøgle-sti + passwords som Gradle læser ved release-build | ❌ git-ignoreret |
| `store-listing.md` | Butiks-tekster (titel/kort/fuld beskrivelse) på en/da/sv | ✅ |
| `screenshots/` | Phone + tablet7 + tablet10 screenshots på en/da/sv | ✅ |

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

## Endnu ikke lavet (kræves/anbefales før publicering)

- [ ] **App-ikon 512×512** (PNG, high-res icon til butikssiden)
- [ ] **Feature graphic 1024×500** (PNG/JPG — vises øverst på butikssiden)
- [ ] Mindst **2 telefon-screenshots** pr. sprog (findes — verificér de viser reelle widgets, ikke onboarding)
- [ ] **Privatlivspolitik-URL** (påkrævet — appen håndterer HA-token/URL; hostes fx på rtr.dk)
- [ ] **Content rating**-spørgeskema (udfyldes i Play Console)
- [ ] **Data safety**-formular (token gemmes lokalt i AndroidKeyStore, sendes kun til brugerens egen HA;
      diagnostik-log til rtr.dk ved "Report a problem" — deklarér dette)
- [ ] Kategori + kontakt-email (rtr@rtr.dk)
- [ ] Beslut om varemærke-navnet "Home Assistant" bruges i titlen (se note i `store-listing.md`)

## Gen-generér upload-nøgle med eget password (valgfrit, kun før første upload)

```
keytool -genkeypair -v -keystore store_assets/android/signing/upload-keystore.jks \
  -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=HA Widgets, O=rtr.dk, C=DK"
# indtast dit eget store/key-password, opdatér derefter keystore.properties
```
