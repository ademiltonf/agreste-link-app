package br.com.agrestelink.clube

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Clube de Benefícios Agreste Link — o app abre a página do Clube (worker agreste-clube). */
class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var puxar: SwipeRefreshLayout
    private val appUrl = BuildConfig.APP_URL
    private val appHost = Uri.parse(BuildConfig.APP_URL).host

    /** A página avisa quando a carteirinha está na tela: deixa o brilho no máximo para o caixa ler o QR. */
    inner class Ponte {
        @JavascriptInterface fun brilho(max: Boolean) {
            runOnUiThread {
                val p = window.attributes
                p.screenBrightness = if (max) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = p
                if (max) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        puxar = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(0xFF0577B8.toInt(), 0xFFFF6A00.toInt())
            addView(web)
            setOnRefreshListener { web.reload() }
            setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        }
        setContentView(puxar)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString ClubeAgresteApp/${BuildConfig.VERSION_NAME}"
        }
        web.setBackgroundColor(0xFFF2F5F9.toInt())
        web.addJavascriptInterface(Ponte(), "ClubeAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val uri = req.url
                if (uri.host == appHost && (uri.scheme == "https" || uri.scheme == "http")) return false
                abrirFora(uri); return true   // WhatsApp, Instagram, mapa abrem no app certo
            }
            override fun onPageFinished(view: WebView, url: String?) { puxar.isRefreshing = false }
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) { puxar.isRefreshing = false; view.loadDataWithBaseURL(appUrl, semInternet(), "text/html", "utf-8", null) }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })
        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(appUrl)
    }

    private fun abrirFora(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: ActivityNotFoundException) { Toast.makeText(this, "Não encontrei um app para abrir este link.", Toast.LENGTH_SHORT).show() }
    }

    private fun semInternet() = """
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;font-family:sans-serif;background:#F2F5F9;color:#0A2540;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center">
        <div style="padding:32px"><h2 style="margin:0 0 10px">Sem conexão</h2>
        <p style="color:#4A5A70;line-height:1.5">Não conseguimos abrir o Clube. Confira sua internet e tente de novo.</p>
        <button onclick="location.href='$appUrl'" style="margin-top:16px;height:52px;padding:0 28px;border:0;border-radius:14px;background:#FF6A00;color:#fff;font-size:16px;font-weight:bold">Tentar de novo</button></div>
        </body></html>
    """.trimIndent()

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); web.saveState(outState) }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); super.onPause() }
}
