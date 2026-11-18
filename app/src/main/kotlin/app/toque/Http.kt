package app.toque

import app.toque.core.i18n.tr
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import app.toque.core.recipes.McOnline

/** GET for recipe reviews. */
object Http {
    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15_000, maxBytes: Int = 1024 * 1024): String {
        val target = McOnline.trustedUrl(url) ?: throw IOException("Untrusted recipe URL")
        val c = target.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", "Toque (Android)")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            val code = c.responseCode
            if (code != 200) throw IOException(tr("réponse %d du serveur", code))
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException(tr("réponse trop grande"))
                }
                return String(out.toByteArray(), Charsets.UTF_8)
            }
        } finally {
            c.disconnect()
        }
    }
}
