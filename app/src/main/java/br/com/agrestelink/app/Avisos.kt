package br.com.agrestelink.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Avisos do chat na barra de notificação.
 *
 * O app guarda a sessão do cliente e, de tempos em tempos, pergunta ao servidor
 * se a equipe respondeu (rota /chat/resumo). Se tiver mensagem nova, mostra a notificação.
 *  - Com atendimento aberto: confere a cada ~1 minuto.
 *  - Sem atendimento aberto: confere a cada 15 minutos (o mínimo que o Android permite).
 */
object Avisos {
    const val CANAL = "chat"
    const val ID_NOTIF = 1001
    const val EXTRA_ABRIR = "abrir"
    private const val PREFS = "avisos"
    private const val RAPIDO = "chat-rapido"
    private const val PERIODICO = "chat-periodico"

    // estado do app (para não avisar quando o cliente já está olhando o chat)
    @Volatile var appVisivel = false
    @Volatile var telaAtual = ""

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun salvarSessao(ctx: Context, token: String) {
        val p = prefs(ctx)
        val antes = p.getString("sessao", "") ?: ""
        if (token == antes) return
        if (token.isBlank()) {
            p.edit().remove("sessao").remove("ultimo_id").apply()
            WorkManager.getInstance(ctx).cancelUniqueWork(RAPIDO)
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODICO)
            NotificationManagerCompat.from(ctx).cancel(ID_NOTIF)
            return
        }
        p.edit().putString("sessao", token).apply()
        agendar(ctx)
    }

    fun sessao(ctx: Context): String = prefs(ctx).getString("sessao", "") ?: ""

    private val rede = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun agendar(ctx: Context) {
        if (sessao(ctx).isBlank()) return
        val periodico = PeriodicWorkRequestBuilder<ChecarMensagens>(15, TimeUnit.MINUTES).setConstraints(rede).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODICO, ExistingPeriodicWorkPolicy.KEEP, periodico)
    }

    /** Confere de novo daqui a [minutos] (usado enquanto há atendimento aberto). */
    fun conferirEm(ctx: Context, minutos: Long) {
        if (sessao(ctx).isBlank()) return
        val req = OneTimeWorkRequestBuilder<ChecarMensagens>().setInitialDelay(minutos, TimeUnit.MINUTES).setConstraints(rede).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(RAPIDO, ExistingWorkPolicy.REPLACE, req)
    }

    fun criarCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(CANAL, "Mensagens do chat", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso quando a equipe da Agreste Link responder no chat"
            }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    fun podeNotificar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun mostrar(ctx: Context, quem: String, texto: String, quantas: Int) {
        if (!podeNotificar(ctx)) return
        criarCanal(ctx)
        val abrir = Intent(ctx, MainActivity::class.java).apply {
            putExtra(EXTRA_ABRIR, "chat")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(ctx, 1, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val titulo = "$quem · Agreste Link"
        val corpo = if (quantas > 1) "$texto\n($quantas mensagens novas)" else texto
        // na tela bloqueada aparece só um aviso genérico (sem o conteúdo)
        val publica = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("Agreste Link")
            .setContentText("Nova mensagem no chat")
            .build()
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(0xFFFF7300.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(corpo))
            .setNumber(quantas)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publica)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID_NOTIF, n) } catch (_: SecurityException) {}
    }

    /** Aviso "Técnico a caminho" (App Técnicos → app do cliente). */
    fun mostrarTecnico(ctx: Context, titulo: String, texto: String) {
        if (!podeNotificar(ctx)) return
        criarCanal(ctx)
        val abrir = Intent(ctx, MainActivity::class.java).apply {
            putExtra(EXTRA_ABRIR, "tecnico")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(ctx, 2, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(0xFFFF7300.toInt())
            .setContentTitle("$titulo · Agreste Link")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(ID_NOTIF + 1, n) } catch (_: SecurityException) {}
    }
    fun ultimoTecnico(ctx: Context) = prefs(ctx).getLong("ultimo_tec", 0)
    fun marcarTecnico(ctx: Context, id: Long) = prefs(ctx).edit().putLong("ultimo_tec", id).apply()

    fun limpar(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_NOTIF)

    fun guardarAtiva(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("ativa", v).apply()
    fun estavaAtiva(ctx: Context) = prefs(ctx).getBoolean("ativa", false)

    fun ultimoAvisado(ctx: Context) = prefs(ctx).getLong("ultimo_id", 0)
    fun marcarAvisado(ctx: Context, id: Long) = prefs(ctx).edit().putLong("ultimo_id", id).apply()
}

class ChecarMensagens(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val token = Avisos.sessao(ctx)
        if (token.isBlank()) return Result.success()
        var ativa = false
        try {
            val con = (URL(BuildConfig.APP_URL + "chat/resumo").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 15000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", "AgresteLinkApp/${BuildConfig.VERSION_NAME}")
            }
            val codigo = con.responseCode
            if (codigo == 401) { Avisos.salvarSessao(ctx, ""); return Result.success() }
            if (codigo != 200) return Result.success()
            val r = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
            ativa = r.optBoolean("ativa")
            Avisos.guardarAtiva(ctx, ativa)
            val aviso = r.optJSONObject("aviso")
            if (aviso != null) {
                val idA = aviso.optLong("id")
                if (idA > Avisos.ultimoTecnico(ctx)) {
                    Avisos.mostrarTecnico(ctx, aviso.optString("titulo", "Técnico a caminho"), aviso.optString("texto", "O técnico está a caminho."))
                    Avisos.marcarTecnico(ctx, idA)
                }
            }
            val naoLidas = r.optInt("naoLidas")
            val ultima = r.optJSONObject("ultima")
            if (naoLidas > 0 && ultima != null) {
                val id = ultima.optLong("id")
                val olhandoChat = Avisos.appVisivel && Avisos.telaAtual == "chat"
                if (id > Avisos.ultimoAvisado(ctx)) {
                    if (!olhandoChat) Avisos.mostrar(ctx, ultima.optString("nome", "Equipe"), ultima.optString("texto", "Nova mensagem"), naoLidas)
                    Avisos.marcarAvisado(ctx, id)
                }
            }
        } catch (_: Exception) {
            // sem internet ou servidor fora: continua conferindo se havia atendimento aberto
            ativa = Avisos.estavaAtiva(ctx)
        }
        // com atendimento aberto, confere de novo em 1 minuto
        if (ativa) Avisos.conferirEm(ctx, 1)
        return Result.success()
    }
}
