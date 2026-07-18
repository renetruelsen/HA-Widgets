# Privacy Policy — HA Widgets

**Draft — review before publishing. Effective date: 2026-07-18.**

HA Widgets ("the app") is an Android app that shows Home Assistant entities as home‑screen
widgets and opens your Home Assistant dashboards. This policy explains what data the app handles,
where it is stored, and what — if anything — leaves your device.

The app is developed by René Truelsen (rtr.dk). Contact: **rtr@rtr.dk**.

## Summary

- Your Home Assistant address and access token are stored **encrypted on your device only** and are
  sent **only to your own Home Assistant server** — never to us or any third party.
- The app contains **no ads, no analytics SDKs, and no third‑party trackers**. We do not sell or
  share your data.
- The only data that can leave your device to us is a **diagnostic report that you choose to send**
  (after a crash you are asked whether to send one, or you tap "Report a problem"). Nothing is sent
  automatically, and it never contains your Home Assistant address or token.

## 1. Data stored on your device

The app stores the following locally on your device; it does **not** transmit any of it to us:

- **Home Assistant address (URL) and long‑lived access token** — stored in Android's encrypted
  storage (EncryptedSharedPreferences, backed by the Android KeyStore). The token is used only to
  authenticate to your Home Assistant server. It is never placed in WebView storage.
- **A cache of entity states** and your **widget configuration** (which entities, labels, actions,
  colors, etc.), stored in a local database.
- **App settings** (theme, color theme, language, refresh interval).

## 2. Data sent to your Home Assistant server

When a widget refreshes or you open a dashboard, the app connects **to the Home Assistant address
you entered** and authenticates with your token. This is your own server (self‑hosted or Nabu
Casa). What is sent and stored there is governed by your Home Assistant instance, not by us. If
your server uses plain `http://` on your local network, that traffic is unencrypted on your
network by your own choice; `https://` is recommended.

## 3. Diagnostic / crash reports (to the developer)

To help fix bugs, the app can send a **diagnostic report** to the developer's server at
`https://rtr.dk/api/logs`. Sending is **always your choice** — nothing is transmitted automatically:

- **If the app crashes**, the crash details are stored locally on your device and, the next time you
  open the app, you are **asked whether to send** a report. If you decline, nothing leaves your device.
- **When you tap "Report a problem"** in the app's settings.

A report may contain: your device model, Android version and app version; the app's recent
internal log lines; a summary of your widget setup (entity IDs, domains and action types); and any
note you optionally type. **A report never includes your Home Assistant address or access token.**
Reports are used solely to diagnose and fix problems and are not shared with third parties or used
for advertising.

## 4. Backups (Google)

The app allows Android's built‑in **Auto Backup**, which may copy your widget configuration and
settings to **your own Google Drive** under your Google account (standard Android feature). The
**encrypted address/token store is excluded** from backup and is never backed up. This backup is
handled by Google under Google's terms; we do not receive it.

## 5. What we do not do

- No advertising, no ad identifiers.
- No analytics or usage‑tracking SDKs.
- No selling, renting, or sharing of your data.
- No accounts with us — the app has no sign‑up.

## 6. Permissions

- **Internet / network state** — to reach your Home Assistant server and (for diagnostic reports)
  the developer's log server.
- **Ignore battery optimizations (optional)** — so widgets can refresh reliably in the background;
  you choose whether to grant it.
- **Query installed apps** — only to let you pick an app for the optional "open an app" widget
  action; the app does not transmit your app list anywhere.

## 7. Data retention

Local data remains until you delete it (disconnect in the app, remove widgets, clear app data, or
uninstall). Disconnecting removes the stored address and token from the device. Diagnostic reports
sent to `rtr.dk` are retained only as long as needed to investigate issues.

## 8. Your choices

- **Disconnect** in the app to erase the stored Home Assistant address and token.
- **Uninstall** the app to remove all local data from the device.
- **Disable Android Auto Backup** in your device/Google settings if you do not want settings backed
  up to your Google Drive.

## 9. Children

The app is not directed at children and does not knowingly collect data from children.

## 10. Changes to this policy

We may update this policy as the app changes. The effective date at the top will be updated
accordingly.

## 11. Contact

Questions about this policy or your data: **rtr@rtr.dk**.
