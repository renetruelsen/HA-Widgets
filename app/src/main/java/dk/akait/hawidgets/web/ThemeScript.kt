package dk.akait.hawidgets.web

object ThemeScript {
    /** Sets HA's selectedTheme localStorage key and reloads once if it differs from desired. */
    fun js(dark: Boolean): String {
        val darkValue = if (dark) "true" else "false"
        return """
            (function() {
                try {
                    var desired = JSON.stringify({dark: $darkValue, theme: 'default'});
                    var current = localStorage.getItem('selectedTheme');
                    if (current !== desired) {
                        localStorage.setItem('selectedTheme', desired);
                        location.reload();
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
