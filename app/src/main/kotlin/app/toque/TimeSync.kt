package app.toque

import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.RootShell
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/** Madrid time zone, with the clock synchronized by Android. */
object TimeSync {
    const val ZONE = "Europe/Madrid"
    class Result(val ok: Boolean, val message: String)

    fun current(): String = TimeZone.getDefault().id

    fun clock(): String = SimpleDateFormat("HH:mm", I18n.locale).apply {
        timeZone = TimeZone.getTimeZone(ZONE)
    }.format(Date())

    @Synchronized
    fun apply(log: Logbook): Result {
        if (!RootShell.available()) return Result(false, tr("Accès root nécessaire pour régler l'heure du robot."))
        val fixedZone = RootShell.run("settings put global auto_time_zone 0")
        if (!fixedZone.ok) log.write("heure", fixedZone.err.trim())
        val networkTime = RootShell.run("settings put global auto_time 1")
        if (!networkTime.ok) log.write("heure", networkTime.err.trim())
        fun zoneApplied() = RootShell.run("getprop persist.sys.timezone").out.trim() == ZONE
        if (!zoneApplied()) {
            RootShell.run("service call alarm 3 s16 $ZONE")
            if (!zoneApplied()) {
                RootShell.run("setprop persist.sys.timezone $ZONE && am broadcast -a android.intent.action.TIMEZONE_CHANGED --es time-zone $ZONE")
            }
        }
        val ok = zoneApplied()
        if (ok) TimeZone.setDefault(TimeZone.getTimeZone(ZONE))
        return if (ok) Result(true, tr("Il est %s (%s)", clock(), ZONE))
        else Result(false, tr("Le système a refusé le fuseau %s", ZONE))
    }
}
