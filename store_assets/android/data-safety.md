# Google Play — Data safety form (fill-in reference)

This maps the app's **actual** data flows (verified in code, v0.2.98) to the answers you enter in
Play Console → **App content → Data safety**. Keep this in sync with `privacy-policy.md`.

Key facts the whole form rests on:

- The app has **no accounts, no ads, no analytics/tracking SDKs**, and does not sell or share data.
- The only data that reaches **us** (the developer's server `https://rtr.dk/api/logs`) is a
  **diagnostic report the user explicitly chooses to send** — after a crash the app *asks* on next
  launch, or the user taps *Report a problem*. **Nothing is sent automatically** (opt-in since v0.2.98).
- The Home Assistant **address and token never leave the device** except to the user's own HA server;
  they are **never** in a diagnostic report.
- The "open an app" widget action uses a narrow `<queries>` (launcher apps only), **not**
  `QUERY_ALL_PACKAGES`, and the installed-app list is **never transmitted**.

---

## Section 1 — Overview questions

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Diagnostic/crash reports the user sends to us. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | Reports are sent over **HTTPS** to `rtr.dk`. |
| Do you provide a way for users to request that their data be deleted? | **Yes** | By email (rtr@rtr.dk). There are no accounts; reports are minimal and not tied to an identity. On-device data is deleted by disconnecting / clearing app data / uninstalling. |

> Note on "collect vs. share": data the app sends to the **user's own Home Assistant server** at the
> user's direction is **not** "collected" or "shared" by us (we never receive it). Only the
> diagnostic report to `rtr.dk` counts, and it is **collected**, not **shared** (no third parties).

---

## Section 2 — Data types

Declare **only** the types below. For every one: **Collected = Yes**, **Shared = No**,
**Optional** (user chooses to send), **Not processed ephemerally** (a report is stored server-side
to investigate the bug).

### App info and performance → **Crash logs**
- Collected: **Yes** · Shared: **No** · Optional: **Yes (user-initiated)**
- Purpose: **App functionality**, **Analytics** (diagnostics/bug-fixing)
- Contains: the exception type + stack trace from a crash.

### App info and performance → **Diagnostics**
- Collected: **Yes** · Shared: **No** · Optional: **Yes (user-initiated)**
- Purpose: **App functionality**, **Analytics**
- Contains: device model, Android version, app version, launcher package, recent internal log lines,
  and a **summary of the user's widget setup** (Home Assistant *entity IDs*, domains, action types,
  dashboard paths, and any custom chip labels the user typed). No address, no token.

### App activity → **Other user-generated content** *(only because of the optional note field)*
- Collected: **Yes** · Shared: **No** · Optional: **Yes**
- Purpose: **App functionality**
- Contains: the free-text "What happened?" note the user may optionally type into the report.

> If you prefer to keep the form minimal, the optional note can also be described as part of
> *Diagnostics* — but declaring it as user-generated content is the most transparent, since it is
> free text the user provides.

---

## Section 3 — Data types you do **NOT** declare (all "No")

Verified not collected/shared by us:

- **Location** (approximate or precise) — none.
- **Personal info** (name, email, address, phone, IDs, etc.) — none. (rtr@rtr.dk is *ours*, not collected from users.)
- **Financial info** — none.
- **Health & fitness** — none.
- **Messages** (email/SMS/other) — none.
- **Photos / videos / audio / files** — none.
- **Contacts / Calendar** — none.
- **Web browsing history** — none.
- **Device or other IDs** — **none.** We send device *model* and OS *version* (not an advertising ID
  or any persistent unique identifier).
- **Installed apps** — **not collected.** The launcher-app list is only used on-device to populate
  the "open an app" picker and is never transmitted.

---

## Section 4 — Related declarations (not Data safety, but part of the same submission)

- **Home Assistant address & token:** stored on-device in `EncryptedSharedPreferences`
  (Android KeyStore), excluded from Android Auto Backup, never transmitted to us. Mention in the
  privacy policy (already done), not in Data safety.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`:** this is a Google Play **sensitive permission**. Under
  *App content → Permissions*, be ready to justify it: *"Widgets fetch Home Assistant state in the
  background via WorkManager; the optional battery-optimization exemption lets scheduled refreshes
  run reliably. The user chooses whether to grant it."*
- **Privacy policy URL:** required — host `privacy-policy.md` publicly and paste the URL.

---

*Last verified against code: v0.2.98 (crash/diagnostic reporting is opt-in; no automatic uploads).*
