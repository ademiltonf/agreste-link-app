package br.com.agrestelink.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var puxar: SwipeRefreshLayout
    private val appUrl = BuildConfig.APP_URL
    private val appHost = Uri.parse(BuildConfig.APP_URL).host

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        puxar = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(0xFF0577B8.toInt(), 0xFFFF7300.toInt())
            addView(web)
            setOnRefreshListener { web.reload() }
            // só deixa "puxar para atualizar" quando a página está no topo
            setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        }
        setContentView(puxar)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // guarda o login do cliente
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString AgresteLinkApp/${BuildConfig.VERSION_NAME}"
        }
        web.setBackgroundColor(0xFFF4F7FB.toInt())
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val uri = req.url
                // páginas do próprio app ficam dentro do app
                if (uri.host == appHost && (uri.scheme == "https" || uri.scheme == "http")) return false
                // WhatsApp, boleto, Fast.com, telefone etc. abrem no app certo do celular
                abrirFora(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                puxar.isRefreshing = false
            }

            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) {
                    puxar.isRefreshing = false
                    view.loadDataWithBaseURL(appUrl, paginaSemInternet(), "text/html", "utf-8", null)
                }
            }
        }
        // links com target=_blank (ex.: boleto) também abrem fora
        web.setDownloadListener { url, _, _, _, _ -> abrirFora(Uri.parse(url)) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(appUrl)
    }

    private fun abrirFora(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Não encontrei um app para abrir este link.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun paginaSemInternet() = """
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;font-family:sans-serif;background:#F4F7FB;color:#0A2540;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center">
        <div style="padding:32px"><h2 style="margin:0 0 10px">Sem conexão</h2>
        <p style="color:#4A5A70;line-height:1.5">Não conseguimos carregar o app. Confira sua internet e tente de novo.</p>
        <button onclick="location.href='$appUrl'" style="margin-top:16px;height:52px;padding:0 28px;border:0;border-radius:14px;background:#FF7300;color:#0A2540;font-size:16px;font-weight:bold">Tentar de novo</button></div>
        </body></html>
    """.trimIndent()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); super.onPause() }
}
