package br.com.agrestelink.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import android.provider.MediaStore
import java.io.File
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var puxar: SwipeRefreshLayout
    private val appUrl = BuildConfig.APP_URL
    private val appHost = Uri.parse(BuildConfig.APP_URL).host

    // seletor de arquivos (foto / PDF) usado pelo chat
    private var arquivosCallback: ValueCallback<Array<Uri>>? = null
    private var fotoCamera: Uri? = null
    private val escolherArquivo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        var uris = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
        // foto tirada pela câmera: vem no arquivo que criamos, não no "data"
        if ((uris == null || uris.isEmpty()) && r.resultCode == RESULT_OK && fotoCamera != null) uris = arrayOf(fotoCamera!!)
        arquivosCallback?.onReceiveValue(uris)
        arquivosCallback = null
        fotoCamera = null
    }

    // GPS: o app pede a permissão e depois libera para a página (Finalizar OS)
    private var gpsOrigem: String? = null
    private var gpsCallback: GeolocationPermissions.Callback? = null
    private val pedirGps = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val ok = r.values.any { it }
        gpsCallback?.invoke(gpsOrigem, ok, false)
        if (!ok) Toast.makeText(this, "Sem permissão de localização não dá para fixar o endereço do cliente.", Toast.LENGTH_LONG).show()
        gpsCallback = null
    }

    // permissão de notificação (Android 13+)
    private val pedirNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Ponte com a página: o app web avisa a sessão e em qual tela o cliente está. */
    inner class Ponte {
        @JavascriptInterface fun sessao(token: String?) {
            Avisos.salvarSessao(applicationContext, (token ?: "").trim())
        }
        @JavascriptInterface fun tela(nome: String?) {
            Avisos.telaAtual = nome ?: ""
            if (Avisos.telaAtual == "chat") Avisos.limpar(applicationContext)
        }
        @JavascriptInterface fun pedirPermissao() {
            if (Build.VERSION.SDK_INT < 33 || Avisos.podeNotificar(applicationContext)) return
            val p = getSharedPreferences("avisos", MODE_PRIVATE)
            if (p.getBoolean("ja_pediu", false)) return
            p.edit().putBoolean("ja_pediu", true).apply()
            runOnUiThread { pedirNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
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
        web.addJavascriptInterface(Ponte(), "AgresteAndroid")
        Avisos.criarCanal(this)
        Avisos.agendar(this)
        web.settings.setGeolocationEnabled(true)
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val temPermissao = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (temPermissao) { callback.invoke(origin, true, false); return }
                gpsOrigem = origin; gpsCallback = callback
                pedirGps.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                arquivosCallback?.onReceiveValue(null)
                arquivosCallback = callback
                val pegar = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
                }
                // câmera: tira a foto direto (Finalizar OS)
                val camera = try {
                    val pasta = File(cacheDir, "fotos").apply { mkdirs() }
                    val arq = File(pasta, "foto_${System.currentTimeMillis()}.jpg")
                    fotoCamera = FileProvider.getUriForFile(this@MainActivity, "${packageName}.arquivos", arq)
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, fotoCamera)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } catch (e: Exception) { fotoCamera = null; null }
                val soCamera = params.isCaptureEnabled && camera != null
                return try {
                    if (soCamera) escolherArquivo.launch(camera!!)
                    else escolherArquivo.launch(Intent.createChooser(pegar, "Enviar foto ou PDF").apply {
                        if (camera != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
                    })
                    true
                } catch (e: ActivityNotFoundException) {
                    arquivosCallback = null
                    Toast.makeText(this@MainActivity, "Não encontrei um app de arquivos.", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
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

        when {
            intent?.getStringExtra(Avisos.EXTRA_ABRIR) == "chat" -> web.loadUrl(appUrl + "#/chat")
            savedInstanceState != null -> web.restoreState(savedInstanceState)
            else -> web.loadUrl(appUrl)
        }
    }

    // tocou na notificação com o app já aberto
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(Avisos.EXTRA_ABRIR) == "chat") {
            Avisos.limpar(this)
            web.evaluateJavascript("location.hash='#/chat'", null)
        }
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

    override fun onResume() {
        super.onResume(); web.onResume()
        Avisos.appVisivel = true
        if (Avisos.telaAtual == "chat") Avisos.limpar(this)
    }
    override fun onPause() { web.onPause(); Avisos.appVisivel = false; super.onPause() }
    // saiu do app: começa a conferir mensagens novas em 1 minuto
    override fun onStop() { super.onStop(); Avisos.conferirEm(this, 1) }
}
