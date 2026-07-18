package dk.rtr.hawidgets.web

/**
 * Plugin-free kiosk mode: hide the HA header and sidebar by injecting CSS into the
 * relevant shadow roots. Instead of walking a fragile element path, we recursively
 * collect every shadow root and inject scoped header-hiding CSS into ALL of them —
 * Lovelace dashboards (`hui-root`) and built-in panels like /history or /logbook
 * (`ha-top-app-bar-fixed`) each keep their toolbar in a different shadow root, so a
 * single hardcoded host isn't enough. The sidebar CSS stays scoped to
 * `home-assistant-main`, which also doubles as the "app booted" readiness signal.
 *
 * Re-applied on an interval because the HA frontend renders late and re-renders on
 * navigation.
 */
object KioskScript {
    val JS = """
        (function () {
          var HEADER_CSS =
            'html,body{overflow-x:hidden!important;max-width:100%!important;}' +
            '.header,.toolbar,ha-app-toolbar,app-header,.mdc-top-app-bar,.top-app-bar{display:none!important;}' +
            '#view,hui-view,.view{padding-top:0!important;margin-top:0!important;min-height:100vh!important;}';
          var SIDEBAR_CSS =
            'ha-sidebar,.mdc-drawer{display:none!important;}' +
            '.mdc-drawer-app-content{margin-left:0!important;}';

          function collectRoots(root, acc) {
            var els = root.querySelectorAll('*');
            for (var i = 0; i < els.length; i++) {
              var sr = els[i].shadowRoot;
              if (sr) { acc.push(sr); collectRoots(sr, acc); }
            }
            return acc;
          }
          function inject(sr, css, id) {
            if (sr.querySelector('#' + id)) return;
            var s = document.createElement('style');
            s.id = id;
            s.textContent = css;
            sr.appendChild(s);
          }
          var notified = false;
          function apply() {
            try {
              var roots = collectRoots(document, []);
              var main = 0;
              roots.forEach(function (sr) {
                // HEADER_CSS injiceres i ALLE shadow roots (ikke kun hui-root) — indbyggede
                // paneler som /history og /logbook renderer deres toolbar
                // (`ha-top-app-bar-fixed` → `<header class="top-app-bar">`) i en anden shadow
                // root end Lovelace-dashboards' hui-root, så den gamle host==='hui-root'-gate
                // skjulte den aldrig. Selectoren er scoped, så injektion i en shadow root uden
                // de elementer er en no-op — helt harmløst.
                inject(sr, HEADER_CSS, 'haw-k-hdr');
                var host = sr.host && sr.host.tagName ? sr.host.tagName.toLowerCase() : '';
                if (host === 'home-assistant-main') { inject(sr, SIDEBAR_CSS, 'haw-k-main'); main++; }
              });
              // Kun home-assistant-main kræves for "klar"-signalet — findes på ALLE paneler.
              if (main > 0 && !notified) {
                notified = true;
                try { window.haWidgetsNative.onDashboardReady(); } catch(e) {}
              }
            } catch (e) {}
          }
          apply();
          var n = 0;
          var iv = setInterval(function () { apply(); if (++n > 40) clearInterval(iv); }, 300);
        })();
    """.trimIndent()
}
