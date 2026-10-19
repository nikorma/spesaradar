package com.niko.spesaradar.fetch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/** Un'offerta cosi' come l'ha letta l'estrattore JS, prima di ogni calcolo. */
data class RawItem(
    val title: String,
    val store: String?,
    val price: Double,
    val oldPrice: Double?,
    val unitText: String?,
    val validity: String?,
    val url: String?,
    val sponsored: Boolean
)

data class FetchResult(
    val items: List<RawItem>,
    val finalUrl: String?,
    val method: String?,
    val error: String?,
    val blocked: Boolean,
    val html: String?
)

/**
 * Apre una pagina in una WebView invisibile (come un normale browser del telefono),
 * aspetta che il contenuto compaia e lo legge con assets/extract.js.
 */
class WebFetcher(private val ctx: Context) {

    private val script: String by lazy {
        ctx.assets.open("extract.js").bufferedReader().use { it.readText() }
    }

    suspend fun fetch(url: String, cfgJson: String, timeoutMs: Long = 60_000): FetchResult =
        withContext(Dispatchers.Main) {
            val wv = newWebView()
            try {
                withTimeout(timeoutMs) { load(wv, url, cfgJson) }
            } catch (e: TimeoutCancellationException) {
                val html = withTimeoutOrNull(5_000) { grabHtml(wv) }
                FetchResult(emptyList(), wv.url, null, "Tempo scaduto (${timeoutMs / 1000}s)", false, html)
            } catch (e: Exception) {
                FetchResult(emptyList(), wv.url, null, e.message ?: e.javaClass.simpleName, false, null)
            } finally {
                wv.stopLoading()
                wv.destroy()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        val wv = WebView(ctx)
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.blockNetworkImage = true
        s.loadsImagesAutomatically = false
        s.mediaPlaybackRequiresUserGesture = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        // Toglie i segni che rivelano una WebView, cosi' il sito la tratta come Chrome.
        s.userAgentString = s.userAgentString
            .replace("; wv", "")
            .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        // Dimensioni di un telefono vero: alcune pagine caricano le offerte solo se "visibili".
        val w = 1080
        val h = 2400
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, w, h)
        return wv
    }

    private suspend fun load(wv: WebView, startUrl: String, cfgJson: String): FetchResult {
        var pageDone = CompletableDeferred<Unit>()
        var loadError: String? = null
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadError = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pageDone.complete(Unit)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    loadError = "Errore di rete: ${error?.description}"
                    pageDone.complete(Unit)
                }
            }
        }

        wv.loadUrl(startUrl)
        pageDone.await()
        var follows = 0
        var last: ParsedJs? = null

        for (attempt in 0 until 6) {
            delay(if (attempt == 0) 3_000 else 2_000)
            if (loadError != null) break
            val raw = wv.eval(script + "\n;SR_EXTRACT($cfgJson);")
            val parsed = parseJs(raw)
            last = parsed
            if (parsed.follow != null && follows < 1) {
                follows++
                pageDone = CompletableDeferred()
                wv.loadUrl(parsed.follow)
                pageDone.await()
                continue
            }
            if (parsed.items.isNotEmpty() || parsed.blocked) break
            // Scorre in basso per far caricare le offerte "pigre".
            wv.eval("window.scrollTo(0, document.body ? document.body.scrollHeight : 0); 1")
        }

        val html = grabHtml(wv)
        val p = last
        return FetchResult(
            items = p?.items ?: emptyList(),
            finalUrl = wv.url,
            method = p?.method,
            error = loadError ?: p?.error ?: if (p == null) "Nessuna risposta dalla pagina" else null,
            blocked = p?.blocked ?: false,
            html = html
        )
    }

    private suspend fun grabHtml(wv: WebView): String? {
        val raw = wv.eval(
            "(function(){var h=document.documentElement?document.documentElement.outerHTML:'';" +
                "return h.length>3000000?h.substring(0,3000000):h;})()"
        )
        return decodeJsString(raw)
    }

    private class ParsedJs(
        val items: List<RawItem>,
        val follow: String?,
        val method: String?,
        val error: String?,
        val blocked: Boolean
    )

    private fun parseJs(raw: String?): ParsedJs {
        val json = decodeJsString(raw) ?: return ParsedJs(emptyList(), null, null, "Estrattore senza risposta", false)
        return try {
            val o = JSONObject(json)
            val arr = o.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until arr.length()) {
                    val jo = arr.optJSONObject(i) ?: continue
                    val title = jo.optString("title", "").trim()
                    val price = jo.optDouble("price", Double.NaN)
                    if (title.isEmpty() || price.isNaN() || price <= 0) continue
                    add(
                        RawItem(
                            title = title,
                            store = jo.strOrNull("store"),
                            price = price,
                            oldPrice = jo.optDouble("oldPrice", Double.NaN).takeUnless { d -> d.isNaN() },
                            unitText = jo.strOrNull("unitText"),
                            validity = jo.strOrNull("validity"),
                            url = jo.strOrNull("url"),
                            sponsored = jo.optBoolean("sponsored", false)
                        )
                    )
                }
            }
            val info = o.optJSONObject("info")
            ParsedJs(
                items = items,
                follow = o.strOrNull("follow"),
                method = info?.strOrNull("method"),
                error = info?.strOrNull("error"),
                blocked = info?.optBoolean("blocked", false) ?: false
            )
        } catch (e: Exception) {
            ParsedJs(emptyList(), null, null, "Risposta non leggibile: ${e.message}", false)
        }
    }

    private fun JSONObject.strOrNull(k: String): String? =
        if (isNull(k)) null else optString(k, "").takeIf { it.isNotBlank() }

    /** evaluateJavascript restituisce il valore come JSON: una stringa arriva tra virgolette. */
    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        return try {
            JSONArray("[$raw]").getString(0)
        } catch (e: Exception) {
            raw
        }
    }

    private suspend fun WebView.eval(js: String): String? = suspendCancellableCoroutine { cont ->
        evaluateJavascript(js) { result -> if (cont.isActive) cont.resume(result) }
    }
}
