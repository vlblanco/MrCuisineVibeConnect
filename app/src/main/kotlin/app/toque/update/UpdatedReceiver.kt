package app.toque.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.toque.ToqueApp
import app.toque.ui.MainActivity

/** Après une mise à jour : on note la nouvelle version et on relance l'écran d'accueil. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = ToqueApp.of(c)
        val version = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName } catch (_: Exception) { "?" }
        app.log.write("maj", "Toque mis à jour en version $version")
        try {
            c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
