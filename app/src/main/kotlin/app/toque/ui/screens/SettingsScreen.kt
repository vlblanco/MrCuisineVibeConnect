package app.toque.ui.screens

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import app.toque.Prefs
import app.toque.TimeSync
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.RootShell
import app.toque.core.machine.Snapshot
import app.toque.recipes.LidlSync
import app.toque.recipes.OnlineSync
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

class SettingsScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Réglages")
    private lateinit var modeRow: LinearLayout
    private lateinit var twinBox: LinearLayout
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var syncText: TextView
    private lateinit var bowlText: TextView
    private lateinit var linkText: TextView
    private lateinit var timeText: TextView
    private var settingTime = false
    private var mode = a.prefs.mode
    private var syncing = false

    override fun build(): View {
        val col = Ui.vertical(c)
        col.setPadding(Ui.u(24), Ui.u(6), Ui.u(24), Ui.u(24))
        val columns = Ui.horizontal(c, Gravity.TOP)
        val left = Ui.vertical(c)
        val right = Ui.vertical(c)
        columns.addView(left, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(18) })
        columns.addView(right, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        col.addView(columns)

        // ---- Machine
        val machine = section(left, tr("Machine"), Icon.Kind.POT)
        modeRow = Ui.horizontal(c)
        machine.addView(modeRow, Ui.lp(t = 4))
        twinBox = Ui.horizontal(c)
        host = field(a.prefs.twinHost, tr("Hôte"))
        port = field(a.prefs.twinPort.toString(), "Port").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        twinBox.addView(host, LinearLayout.LayoutParams(0, Ui.u(58), 1f).apply { rightMargin = Ui.u(10) })
        twinBox.addView(port, LinearLayout.LayoutParams(Ui.u(120), Ui.u(58)))
        machine.addView(twinBox, Ui.lp(t = 12))
        linkText = Ui.text(c, "", 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }
        machine.addView(linkText, Ui.lp(t = 12))
        machine.addView(Ui.button(c, tr("Appliquer"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 58) { applyMode() }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 14))
        renderModes()

        // ---- Sécurité
        val safety = section(left, tr("Sécurité"), Icon.Kind.WARNING)
        safety.addView(toggle(tr("Exiger la détection du bol"), a.prefs.requireBowl) { on ->
            a.prefs.requireBowl = on
            a.machine.reconfigure { a.refusal(it) }
        })
        bowlText = Ui.text(c, "", 15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }
        safety.addView(bowlText, Ui.lp(t = 6))
        safety.addView(toggle(tr("Montée progressive de la vitesse si le contenu est chaud"), a.prefs.hotRamp) { on ->
            a.prefs.hotRamp = on
            a.machine.reconfigure { a.refusal(it) }
        }, Ui.lp(t = 10))
        safety.addView(Ui.text(c, tr("À partir de 60 °C, la lame atteint sa vitesse en 2 à 3 secondes pour éviter les projections. À froid, elle y va directement."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 6))
        safety.addView(toggle(tr("Enchaîner les étapes machine des recettes"), a.prefs.chainSteps) { a.prefs.chainSteps = it }, Ui.lp(t = 10))
        safety.addView(Ui.text(c, tr("Les réglages qui se suivent sans geste à faire entre eux partent ensemble d'un seul appui sur Lancer : la lame passe directement à la vitesse et à la température suivantes."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 6))
        safety.addView(toggle(tr("Sons"), a.prefs.sound) { a.prefs.sound = it }, Ui.lp(t = 10))

        // ---- Langue (titre bilingue : trouvable quelle que soit la langue affichée)
        val language = section(right, tr("Langue"), Icon.Kind.INFO)
        val options = listOf(I18n.AUTO to tr("Automatique (langue du système)")) + I18n.languages.map { it.code to it.name }
        language.addView(dropdown(options, a.prefs.language) { code ->
            a.prefs.language = code
            a.applyLanguage()
        }, Ui.lp(Ui.MATCH, Ui.u(58), t = 4))

        // ---- Date et heure
        val time = section(right, tr("Date et heure"), Icon.Kind.CLOCK)
        timeText = Ui.text(c, "", 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }
        time.addView(timeText, Ui.lp(t = 10))
        time.addView(Ui.button(c, tr("Appliquer le fuseau horaire"), Ui.Style.SECONDARY, Icon.Kind.SYNC, height = 52) { setClock() },
            Ui.lp(Ui.WRAP, Ui.WRAP, t = 10))
        refreshTime()

        // ---- Recettes Lidl
        val lidl = section(right, tr("Recettes Lidl"), Icon.Kind.SYNC)
        lidl.addView(Ui.text(c, tr("Toque télécharge lui-même les recettes officielles du Monsieur Cuisine connect sur monsieur-cuisine.com (sans compte)."),
            16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) })
        syncText = Ui.text(c, "", 16f, Ui.TEXT)
        lidl.addView(syncText, Ui.lp(t = 10))
        val b1 = Ui.horizontal(c)
        b1.addView(Ui.button(c, tr("Synchroniser"), Ui.Style.PRIMARY, Icon.Kind.SYNC, height = 58) { sync(online = true) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        lidl.addView(b1, Ui.lp(t = 12))
        lidl.addView(Ui.text(c, tr("Autre source : les recettes déjà présentes dans l'appli d'origine du robot (remplacent celles du site)."),
            14f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 12))
        val b2 = Ui.horizontal(c)
        b2.addView(Ui.button(c, tr("Importer de l'appli d'origine"), Ui.Style.SECONDARY, height = 52) { sync(online = false) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        b2.addView(Ui.button(c, tr("Ouvrir l'appli d'origine"), Ui.Style.GHOST, Icon.Kind.LINK, height = 52) { a.openVendorApp() })
        lidl.addView(b2, Ui.lp(t = 6))
        lidl.addView(toggle(tr("Synchro automatique chaque nuit"), a.prefs.autoSync) { a.prefs.autoSync = it }, Ui.lp(t = 10))

        // ---- Outils
        val tools = section(right, tr("Outils"), Icon.Kind.SETTINGS)
        tools.addView(link(tr("Diagnostic de la base moteur")) { a.push(DiagnosticsScreen(a)) })
        tools.addView(link(tr("Bouton rotatif")) { a.push(KnobScreen(a)) })
        tools.addView(link(tr("Journal")) { a.push(LogScreen(a)) })
        tools.addView(link(tr("Paramètres Android")) { a.openAndroidSettings() })
        tools.addView(link(tr("Revenir à l'appli d'origine")) { restoreVendor() })
        val version = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName } catch (_: Exception) { "?" }
        tools.addView(Ui.text(c, tr("Toque %s · protocole d'après le Monsieur Cuisine Developer SDK (licence MIT). " +
            "Projet indépendant, non affilié à Lidl ni à SilverCrest.", version), 14f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 12))

        return Ui.scroll(c, col)
    }

    private fun section(parent: LinearLayout, title: String, icon: Icon.Kind): LinearLayout {
        val card = Ui.card(c, 24f)
        val head = Ui.horizontal(c)
        head.addView(Icon(c, icon, Ui.ACCENT), LinearLayout.LayoutParams(Ui.u(24), Ui.u(24)).apply { rightMargin = Ui.u(10) })
        head.addView(Ui.text(c, title, 21f, Ui.TEXT, Ui.MEDIUM))
        card.addView(head, Ui.lp(b = 10))
        parent.addView(card, Ui.lp(b = 16))
        return card
    }

    private fun field(value: String, hint: String) = EditText(c).apply {
        setText(value)
        this.hint = hint
        setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(19f))
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.TEXT3)
        setSingleLine()
        background = Ui.round(Ui.SURFACE2, 16f, Ui.STROKE, 1f)
        setPadding(Ui.u(14), 0, Ui.u(14), 0)
    }

    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = Ui.horizontal(c)
        row.addView(Ui.text(c, label, 18f, Ui.TEXT), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        var state = initial
        val sw = Ui.text(c, "", 16f, Ui.TEXT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        fun paint() {
            sw.text = if (state) tr("Oui") else tr("Non")
            sw.setTextColor(if (state) 0xFF1A0E05.toInt() else Ui.TEXT2)
            sw.background = if (state) Ui.round(Ui.ACCENT, 24f) else Ui.round(Ui.SURFACE3, 24f)
        }
        paint()
        sw.setOnClickListener { state = !state; paint(); onChange(state) }
        row.addView(sw, LinearLayout.LayoutParams(Ui.u(84), Ui.u(46)))
        return row
    }

    private fun link(label: String, onClick: () -> Unit): LinearLayout {
        val row = Ui.horizontal(c)
        row.setPadding(0, Ui.u(14), 0, Ui.u(14))
        row.addView(Ui.text(c, label, 18f, Ui.TEXT), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(Icon(c, Icon.Kind.CHEVRON, Ui.TEXT3), LinearLayout.LayoutParams(Ui.u(20), Ui.u(20)))
        row.background = Ui.pressable(Ui.round(0, 8f), 8f)
        row.isClickable = true
        row.setOnClickListener { onClick() }
        return row
    }

    /** Menu déroulant : la valeur choisie, et la liste qui s'ouvre en dessous. */
    private fun dropdown(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): View {
        val box = Ui.horizontal(c)
        box.setPadding(Ui.u(16), 0, Ui.u(14), 0)
        box.background = Ui.pressable(Ui.round(Ui.SURFACE2, 16f, Ui.STROKE, 1f), 16f)
        val label = options.firstOrNull { it.first == selected }?.second ?: options.first().second
        box.addView(Ui.text(c, label, 19f, Ui.TEXT), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        box.addView(Icon(c, Icon.Kind.CHEVRON, Ui.TEXT2).apply { rotation = 90f }, LinearLayout.LayoutParams(Ui.u(22), Ui.u(22)))
        box.isClickable = true
        box.setOnClickListener {
            val list = Ui.vertical(c)
            list.setPadding(0, Ui.u(6), 0, Ui.u(6))
            val scroll = ScrollView(c).apply {
                addView(list)
                background = Ui.round(Ui.SURFACE3, 16f, Ui.STROKE, 1f)
            }
            val height = if (options.size > 7) Ui.u(400) else ViewGroup.LayoutParams.WRAP_CONTENT
            val popup = PopupWindow(scroll, box.width, height, false)
            for ((code, name) in options) {
                val row = Ui.text(c, name, 19f, if (code == selected) Ui.ACCENT else Ui.TEXT)
                row.setPadding(Ui.u(16), Ui.u(14), Ui.u(16), Ui.u(14))
                row.background = Ui.pressable(Ui.round(0, 0f), 0f)
                row.isClickable = true
                row.setOnClickListener {
                    popup.dismiss()
                    if (code != selected) onPick(code)
                }
                list.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP))
            }
            popup.setBackgroundDrawable(ColorDrawable(0))
            popup.isOutsideTouchable = true
            popup.elevation = Ui.uf(8f)
            popup.showAsDropDown(box, 0, Ui.u(6))
        }
        return box
    }

    private fun renderModes() {
        modeRow.removeAllViews()
        for (m in Prefs.Mode.values()) {
            modeRow.addView(Ui.toggleChip(c, when (m) {
                Prefs.Mode.DEMO -> tr("Démo")
                Prefs.Mode.TWIN -> tr("Jumeau (PC)")
                Prefs.Mode.HARDWARE -> tr("Machine réelle")
            }, mode == m) { mode = m; renderModes() }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        }
        twinBox.visibility = if (mode == Prefs.Mode.TWIN) View.VISIBLE else View.GONE
        linkText.text = when (mode) {
            Prefs.Mode.DEMO -> tr("Base moteur simulée : pour essayer l'appli sur n'importe quel appareil Android.")
            Prefs.Mode.TWIN -> tr("Jumeau numérique du SDK lancé sur un PC (tools/twin.py). Avec « adb reverse tcp:8765 tcp:8765 », gardez 127.0.0.1.")
            Prefs.Mode.HARDWARE -> tr("Pilote le vrai moteur par le port série. Exige le root. L'appli d'origine est coupée du moteur (réversible).")
        }
    }

    private fun applyMode() {
        if (a.snapshot.busy) {
            a.toast(tr("Arrêtez d'abord le programme en cours"), Ui.WARN)
            return
        }
        if (mode == Prefs.Mode.HARDWARE && a.prefs.mode != Prefs.Mode.HARDWARE) {
            a.confirm(
                tr("Piloter la vraie machine"),
                tr("Toque va prendre le contrôle du moteur et de la chauffe à la place de l'appli d'origine.\n\n" +
                    "Premier essai : bol vide, couvercle fermé, à portée de l'interrupteur principal. " +
                    "Si quoi que ce soit semble anormal, coupez l'interrupteur."),
                tr("Continuer"), danger = true,
            ) { prepareHardware() }
            return
        }
        save()
    }

    private fun save() {
        a.prefs.mode = mode
        a.prefs.bootLaunch = true
        a.prefs.twinHost = host.text.toString().trim().ifEmpty { "127.0.0.1" }
        a.prefs.twinPort = port.text.toString().trim().toIntOrNull() ?: 8765
        a.machine.reconfigure { v -> if (v.ok) a.toast(tr("Mode : %s", mode.label), Ui.OK) else a.refusal(v) }
        a.refreshTitle()
    }

    private fun prepareHardware() {
        a.toast(tr("Vérifications…"))
        Thread {
            val problems = ArrayList<String>()
            if (!RootShell.available()) problems += tr("accès root refusé")
            val sync = LidlSync(a, a.prefs, a.app.recipes, a.app.log)
            if (problems.isEmpty() && sync.installed()) {
                if (sync.components().hardwareService != null && !sync.setVendorHardware(false))
                    problems += tr("impossible de couper l'appli d'origine du moteur")
                sync.forceStop()
            }
            view.post {
                if (problems.isNotEmpty()) {
                    a.confirm(tr("Machine réelle indisponible"), problems.joinToString("\n") { "• $it" }, "OK", tr("Fermer")) {}
                } else save()
            }
        }.start()
    }

    private fun restoreVendor() {
        a.confirm(
            tr("Revenir à l'appli d'origine"),
            tr("Toque libère le moteur, rend l'accès à l'appli Monsieur Cuisine d'origine et passe en mode Démo. " +
                "Toque ne s'ouvrira plus au démarrage du robot."),
            tr("Revenir"),
        ) {
            a.prefs.mode = Prefs.Mode.DEMO
            a.prefs.bootLaunch = false
            a.machine.reconfigure {
                Thread {
                    val sync = LidlSync(a, a.prefs, a.app.recipes, a.app.log)
                    sync.setVendorHardware(true)
                    val launch = sync.components().launch
                    view.post {
                        if (launch != null) a.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        else a.toast(tr("Appli d'origine introuvable"), Ui.WARN)
                    }
                }.start()
            }
        }
    }

    private fun sync(online: Boolean) {
        if (syncing) return
        if (!online && a.prefs.mode != Prefs.Mode.HARDWARE) {
            a.toast(tr("Disponible une fois Toque installé sur le robot (mode Machine réelle)"), Ui.WARN)
            return
        }
        syncing = true
        syncText.text = tr("Synchronisation en cours… (quelques minutes)")
        Thread {
            val r = if (online) OnlineSync(a, a.prefs, a.app.recipes, a.app.log).sync { msg -> view.post { syncText.text = msg } }
            else LidlSync(a, a.prefs, a.app.recipes, a.app.log).importNow { msg -> view.post { syncText.text = msg } }
            view.post {
                syncing = false
                refreshSync()
                a.toast(r.message, if (r.ok) Ui.OK else Ui.WARN)
            }
        }.start()
    }

    private fun refreshTime(message: String? = null) {
        val zone = TimeSync.current()
        val mode = TimeSync.ZONE
        timeText.text = tr("Il est %s · fuseau %s (%s)", TimeSync.clock(), zone.replace('_', ' '), mode) +
            (message?.let { "\n" + it } ?: "")
    }

    private fun setClock() {
        if (settingTime) return
        settingTime = true
        timeText.text = tr("Réglage de l'heure…")
        Thread {
            val r = try {
                TimeSync.apply(a.app.log)
            } catch (e: Exception) {
                TimeSync.Result(false, e.message ?: e.toString())
            }
            a.runOnUiThread {
                settingTime = false
                refreshTime(if (r.ok) null else r.message)
                if (r.ok) a.toast(r.message, Ui.OK)
            }
        }.start()
    }

    private fun refreshSync() {
        val last = a.prefs.lastSyncResult
        syncText.text = (if (last.isEmpty()) tr("Jamais synchronisé") else tr("Dernière synchro le %s", last)) +
            "\n" + tr("%d recettes dans Toque", a.app.recipes.count)
    }

    override fun onShow() {
        mode = a.prefs.mode
        renderModes()
        if (!syncing) refreshSync()
    }

    override fun onSnapshot(s: Snapshot) {
        bowlText.text = if (s.hasSample) tr("Capteur du bol en ce moment : %s. Vérifiez qu'il change quand vous retirez le bol avant d'activer cette option.",
            if (s.bowlDetected) tr("bol détecté") else tr("bol NON détecté")) else tr("Base non connectée")
    }
}
