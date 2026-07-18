# Keep the external-auth JS bridge methods reachable from WebView JS.
-keepclassmembers class dk.rtr.hawidgets.web.ExternalAuthBridge {
    @android.webkit.JavascriptInterface <methods>;
}
