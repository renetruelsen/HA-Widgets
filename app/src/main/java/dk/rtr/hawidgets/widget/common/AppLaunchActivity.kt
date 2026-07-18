package dk.rtr.hawidgets.widget.common

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import dk.rtr.hawidgets.R

/**
 * Usynlig bro-aktivitet for "Åbn app"-handlingen (MultiEntityWidget). Modtager et pakkenavn,
 * slår app'ens launcher-intent op og starter den — eller viser en toast hvis app'en ikke (længere)
 * er installeret. Ingen UI: finish() kaldes med det samme (translucent tema i manifestet).
 *
 * Launch-opslaget sker her (ikke i clickModifier ved compose-tid), så et afinstalleret pakkenavn
 * giver en synlig fejl frem for et tavst tap.
 */
class AppLaunchActivity : Activity() {

    companion object {
        const val EXTRA_PACKAGE = "package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } else {
            Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
