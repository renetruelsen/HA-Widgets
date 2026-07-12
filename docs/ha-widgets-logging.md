# HA-Widgets → rtr.dk Log Collector — integrationsguide

## Formål
Send fejllogs (med kontekst) fra HA-Widgets-appen til rtr.dk's Log Collector, hvor de gemmes i
MySQL og kan ses i admin-modulet. Fejl udløser desuden en e-mail-notifikation.

## Endpoint
```
POST https://rtr.dk/api/logs
```

## Headers
| Header | Værdi | Note |
|---|---|---|
| `Authorization` | `Bearer hV3nRk7dQmZ2sPxL` | HA-Widgets upload-token |
| `X-App-Id` | `ha-widgets` | Skal matche token'ets app |
| `X-App-Version` | fx `0.9.1` | Appens version (`package_info_plus`) |
| `X-App-Platform` | `android` / `ios` | `Platform.operatingSystem` |
| `Content-Type` | `text/plain; charset=utf-8` | Body er ren tekst |

> **User-Agent:** UnoEuros WAF blokerer generiske/manglende UA (curl, python) med HTTP 455.
> Dart's `http`-klient sender automatisk en reel UA — **Flutter er upåvirket**, gør intet særligt.

## Body-format — vigtigt
Body er **ren tekst**: én log-linje pr. linje. Serveren markerer en upload som **fejl** hvis body
indeholder delstrengen `" E ["` (mellemrum, `E`, mellemrum, `[`). Brug derfor dette linjeformat:

```
<ISO-8601 UTC>  <NIVEAU> [<TAG>] <besked>
```
Niveau er ét bogstav: `I` (info), `W` (advarsel), `E` (fejl). Eksempel:

```
2026-07-12T10:00:00.000Z I [BOOT] Widget host ready
2026-07-12T10:00:04.512Z W [HA] Retry 2/3 GET /api/states
2026-07-12T10:00:04.590Z E [HA] SocketException: Connection reset (os error 104)
    #0 _HttpClient.send (package:ha_widgets/net/http.dart:88)
```
Kun linjer med `" E ["` gør, at loggen får status **Fejl** i admin + trigger mail. Stacktrace-linjer
må gerne stå som fri tekst under fejl-linjen.

## Svarkoder
| Kode | Betydning |
|---|---|
| `202` | Modtaget og gemt ✓ |
| `400` | Tom body |
| `403` | Forkert/manglende token eller `X-App-Id` |
| `413` | Body over 512 KB (`524288` bytes) |
| `429` | Rate limit: max **10 requests/minut pr. IP** |

## Begrænsninger, appen skal respektere
- **Max 512 KB pr. upload** → hold body lille (ring-buffer af seneste linjer, ikke hele historikken).
- **Max 10 uploads/min** → send kun ved fejl + throttle; buffer/coalesce hvis flere fejl kommer tæt.
- Upload er **fire-and-forget**: fejl i afsendelsen må aldrig crashe appen — swallow exceptions, evt. én retry.

---

## Anbefalet klient-design (Dart)
1. Hold en **ring-buffer** af de seneste ~300 formaterede log-linjer (giver kontekst før fejlen).
2. Fang fejl globalt: `FlutterError.onError`, `PlatformDispatcher.instance.onError`, og kør `main`
   i `runZonedGuarded`.
3. Ved fejl: skriv en `E`-linje + stacktrace til bufferen, og **flush** bufferen som ét POST.
4. **Throttle** flush (fx max én upload / 30 sek) så en fejl-storm ikke rammer rate limit.

```dart
import 'dart:collection';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class RemoteLogger {
  RemoteLogger._();
  static final RemoteLogger instance = RemoteLogger._();

  static const _endpoint = 'https://rtr.dk/api/logs';
  static const _token = String.fromEnvironment('LOG_TOKEN',
      defaultValue: 'hV3nRk7dQmZ2sPxL'); // sæt via --dart-define i release
  static const _appId = 'ha-widgets';

  final Queue<String> _buffer = Queue<String>();
  static const _maxLines = 300;
  String _appVersion = '0.0.0';
  DateTime _lastFlush = DateTime.fromMillisecondsSinceEpoch(0);
  static const _throttle = Duration(seconds: 30);

  void init({required String appVersion}) => _appVersion = appVersion;

  void i(String tag, String msg) => _add('I', tag, msg);
  void w(String tag, String msg) => _add('W', tag, msg);
  void e(String tag, String msg) => _add('E', tag, msg);

  void _add(String level, String tag, String msg) {
    final ts = DateTime.now().toUtc().toIso8601String(); // ...Z
    _buffer.addLast('$ts $level [$tag] $msg');
    while (_buffer.length > _maxLines) _buffer.removeFirst();
  }

  /// Kald denne fra dine globale fejl-handlere.
  Future<void> reportError(Object error, StackTrace? stack, {String tag = 'CRASH'}) async {
    _add('E', tag, error.toString());
    if (stack != null) _buffer.addLast(stack.toString());
    await flush();
  }

  Future<void> flush() async {
    if (_buffer.isEmpty) return;
    if (DateTime.now().difference(_lastFlush) < _throttle) return; // throttle
    _lastFlush = DateTime.now();

    final body = _buffer.join('\n');
    try {
      await http.post(
        Uri.parse(_endpoint),
        headers: {
          'Authorization': 'Bearer $_token',
          'X-App-Id': _appId,
          'X-App-Version': _appVersion,
          'X-App-Platform': Platform.operatingSystem, // android / ios
          'Content-Type': 'text/plain; charset=utf-8',
        },
        body: body,
      ).timeout(const Duration(seconds: 10));
    } catch (_) {
      // fire-and-forget: aldrig kaste videre
    }
  }
}
```

Opsætning i `main.dart`:
```dart
void main() {
  runZonedGuarded(() async {
    WidgetsFlutterBinding.ensureInitialized();
    final info = await PackageInfo.fromPlatform();
    RemoteLogger.instance.init(appVersion: info.version);

    FlutterError.onError = (details) {
      FlutterError.presentError(details);
      RemoteLogger.instance.reportError(details.exception, details.stack);
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      RemoteLogger.instance.reportError(error, stack);
      return true;
    };

    runApp(const MyApp());
  }, (error, stack) => RemoteLogger.instance.reportError(error, stack));
}
```

> **Token i release:** byg med `--dart-define=LOG_TOKEN=hV3nRk7dQmZ2sPxL` fremfor at hardcode.
> (Bemærk: en klient-token kan aldrig gøres 100% hemmelig i en mobil-app — den kan roteres i
> `appsettings` hvis den misbruges.)

## Hurtig manuel test
```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST https://rtr.dk/api/logs \
  -H "Content-Type: text/plain; charset=utf-8" \
  -H "Authorization: Bearer hV3nRk7dQmZ2sPxL" \
  -H "X-App-Id: ha-widgets" -H "X-App-Version: 0.9.1" -H "X-App-Platform: android" \
  --data-binary $'2026-07-12T10:00:00Z I [BOOT] test\n2026-07-12T10:00:01Z E [HA] test-fejl'
```
Forventet: `HTTP 202`, og loggen dukker op i `/admin/logs` med status **Fejl**.
