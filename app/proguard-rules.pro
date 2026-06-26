# Keep the external-auth JS bridge methods reachable from WebView JS.
-keepclassmembers class dk.akait.hawidgets.web.ExternalAuthBridge {
    @android.webkit.JavascriptInterface <methods>;
}
