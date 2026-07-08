package dk.akait.hawidgets.web

/**
 * Plugin-free kiosk mode: hide the HA header and sidebar by injecting CSS into the
 * relevant shadow roots. Instead of walking a fragile element path, we recursively
 * collect every shadow root and inject scoped CSS only into the `hui-root` (header)
 * and `home-assistant-main` (sidebar) shadow roots — robust across HA versions.
 *
 * Re-applied on an interval because the HA frontend renders late and re-renders on
 * navigation.
 */
object KioskScript {
    val JS = """
        (function () {
          var HEADER_CSS =
            'html,body{overflow-x:hidden!important;max-width:100%!important;}' +
            '.header,.toolbar,ha-app-toolbar,app-header,.mdc-top-app-bar{display:none!important;}' +
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
              var hui = 0, main = 0;
              roots.forEach(function (sr) {
                var host = sr.host && sr.host.tagName ? sr.host.tagName.toLowerCase() : '';
                if (host === 'hui-root') { inject(sr, HEADER_CSS, 'haw-k-hui'); hui++; }
                if (host === 'home-assistant-main') { inject(sr, SIDEBAR_CSS, 'haw-k-main'); main++; }
              });
              // Kun home-assistant-main kræves for "klar"-signalet — findes på ALLE paneler
              // (inkl. /history, som ikke har hui-root, da det ikke er et Lovelace-dashboard).
              // hui-root-CSS'en injiceres stadig når den findes, den blokerer bare ikke signalet.
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
