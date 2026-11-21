package app.grab

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private class LoginSite(val name: String, val url: String)

private val LOGIN_SITES = listOf(
    LoginSite("YouTube", "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F"),
    LoginSite("Facebook", "https://m.facebook.com/login"),
    LoginSite("Instagram", "https://www.instagram.com/accounts/login/"),
    LoginSite("TikTok", "https://www.tiktok.com/login"),
    LoginSite("X", "https://x.com/i/flow/login"),
    LoginSite("Reddit", "https://www.reddit.com/login"),
    LoginSite("Twitch", "https://www.twitch.tv/login"),
    LoginSite("Vimeo", "https://vimeo.com/log_in"),
    LoginSite("Dailymotion", "https://www.dailymotion.com/signin")
)

// جوجل بتمنع تسجيل الدخول من WebView العادي، فبنستخدم هوية متصفح كروم
private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

// متصفح صغير: افتح أي موقع، سجّل دخول، واضغط «حفظ». الكوكيز بتتحفظ لـ yt-dlp وبتتطبّق على كل روابط الموقع ده
class LoginActivity : ComponentActivity() {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent { AppTheme { Screen() } }
    }

    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web?.destroy(); web = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun Screen() {
        val cs = MaterialTheme.colorScheme
        val focus = LocalFocusManager.current
        val start = remember { intent?.getStringExtra("url") ?: LOGIN_SITES[0].url }
        var cur by remember { mutableStateOf(start) }
        var addr by remember { mutableStateOf("") }
        var progress by remember { mutableIntStateOf(0) }
        var msg by remember { mutableStateOf("") }
        var ok by remember { mutableStateOf(false) }
        val host = try { Uri.parse(cur).host } catch (_: Throwable) { null }

        fun open(raw: String) {
            val t = raw.trim()
            if (t.isEmpty()) return
            val u = when {
                t.startsWith("http://", true) || t.startsWith("https://", true) -> t
                t.contains(" ") || !t.contains(".") -> "https://www.google.com/search?q=" + Uri.encode(t)
                else -> "https://$t"
            }
            focus.clearFocus()
            web?.loadUrl(u)
        }

        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton({ finish() }) { Icon(Ico.Close, "إغلاق") }
                    TextField(
                        value = addr, onValueChange = { addr = it }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                        placeholder = { Text("اكتب رابط موقع أو ابحث") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { open(addr) })
                    )
                    IconButton({ web?.reload() }) { Icon(Ico.Refresh, "تحديث") }
                }
                if (progress in 1..99) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LOGIN_SITES.forEach { st -> AssistChip(onClick = { open(st.url) }, label = { Text(st.name) }) }
                }
                AndroidView(factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = BROWSER_UA
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : WebViewClient() {
                            // روابط تطبيقات (fb://, intent://…) ما نفتحهاش، نفضل في المتصفح
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val sc = request?.url?.scheme ?: return false
                                return sc != "http" && sc != "https"
                            }
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                if (url != null) { cur = url; addr = url }
                            }
                        }
                        web = this
                        loadUrl(start)
                    }
                }, Modifier.weight(1f).fillMaxWidth())
                Surface(color = cs.surfaceContainer, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (msg.isNotEmpty()) msg else "سجّل دخول في الموقع، وبعدين اضغط حفظ. تقدر تحفظ كذا موقع واحد ورا التاني.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (msg.isEmpty()) cs.onSurfaceVariant else if (ok) cs.tertiary else cs.error
                        )
                        Button(
                            onClick = {
                                val r = Repo.saveWebCookies(cur)
                                if (r != null) {
                                    ok = true; msg = "تم حفظ كوكيز ${r.first} (${r.second} كوكي) ✓"
                                    Toast.makeText(this@LoginActivity, "تم حفظ كوكيز ${r.first} ✓", Toast.LENGTH_SHORT).show()
                                } else {
                                    ok = false; msg = "ما لقيتش كوكيز للموقع ده. سجّل دخول الأول وبعدين جرّب تاني."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp), enabled = host != null
                        ) {
                            Icon(Ico.Cookie, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("حفظ كوكيز " + baseDomain(host ?: ""))
                        }
                    }
                }
            }
        }
    }
}
