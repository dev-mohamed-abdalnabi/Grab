package app.grab

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.runtime.*
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class St { Queued, Running, Done, Failed, Canceled }

val LADDER = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)
fun lowerQ(h: Int): Int = LADDER.lastOrNull { it < h } ?: 0

class Task(
    val id: String, val url: String, title: String, thumb: String?,
    val audio: Boolean, val height: Int, val subs: Boolean, val playlist: Boolean
) {
    var title by mutableStateOf(title)
    var thumb by mutableStateOf(thumb)
    var uploader: String? = null
    var clipStart = ""
    var clipEnd = ""
    var thumbOnly = false       // تبويب الصورة: ننزّل الصورة بس من غير الفيديو
    var keep = false            // إيقاف مؤقت: نحتفظ بالجزء المحمّل عشان نكمّل منه
    var waitNet = false         // مستني النت/الواي فاي: بيكمّل لوحده لما يرجع
    var waitSince = 0L
    var lastTry = 0L
    var autoTries = 0           // عدد محاولات الاستئناف التلقائي (الحد 5)
    var waitKind = 0            // 0 = مستني النت (بينتهي بعد 30 دقيقة)، 1 = مستني الموعد/الشحن (من غير انتهاء)
    var prio = 0                // أولوية في الطابور (الأعلى الأول)
    var queuedAt = 0L
    var force = false           // «ابدأ الآن»: يتخطّى الطابور والجدولة
    var st by mutableStateOf(St.Queued)
    var progress by mutableFloatStateOf(0f)
    var eta by mutableLongStateOf(0L)
    var speed by mutableLongStateOf(0L)    // بايت في الثانية (متوسط متحرك)
    var done by mutableLongStateOf(0L)     // اللي نزل لحد دلوقتي
    var total by mutableLongStateOf(0L)    // الحجم الكلي (تقديري لو الموقع مادّاش رقم)
    var expect = 0L                        // الحجم المتوقع من المعاينة (فيديو + صوت)
    var msg by mutableStateOf("")
    var job: Job? = null
}

// وقت متبقي بالدقايق (مش بالثواني)
private fun minsAr(m: Long) = when {
    m <= 1L -> "دقيقة"
    m == 2L -> "دقيقتين"
    m in 3L..10L -> "$m دقايق"
    else -> "$m دقيقة"
}

fun etaText(sec: Long): String {
    if (sec <= 0L) return ""
    if (sec < 60L) return "أقل من دقيقة"
    val m = (sec + 30L) / 60L
    if (m < 60L) return minsAr(m)
    val h = m / 60L; val r = m % 60L
    val hs = when { h == 1L -> "ساعة"; h == 2L -> "ساعتين"; h in 3L..10L -> "$h ساعات"; else -> "$h ساعة" }
    return if (r > 0L) "$hs و${minsAr(r)}" else hs
}

// سطر مختصر للإشعار: النسبة • السرعة • الوقت المتبقي
fun briefOf(t: Task): String {
    val parts = mutableListOf("${(t.progress * 100).toInt()}%")
    if (t.speed > 0L) parts.add(sz(t.speed, false) + "/s")
    etaText(t.eta).takeIf { it.isNotEmpty() }?.let { parts.add("باقي $it") }
    return parts.joinToString(" • ")
}

class Item(val title: String, val thumb: String?, val uri: String, val audio: Boolean, val src: String = "")

// فيديو اتحمّل قبل كده (item) أو لسه في قائمة التحميل (item == null)
class Dup(val title: String, val item: Item?)

class Hist(val url: String, val title: String, val thumb: String?)
class PlEntry(val url: String, val title: String, val thumb: String?, val dur: Int)
class PlInfo(val title: String, val entries: List<PlEntry>)

// حجم جودة معيّنة (فيديو + صوت). approx = تقدير مش رقم دقيق من الموقع
class QOpt(val h: Int, val bytes: Long, val approx: Boolean)
class Info(
    val title: String?, val thumbnail: String?, val uploader: String?, val duration: Int,
    val qs: List<QOpt>, val audioBytes: Long, val audioApprox: Boolean
) {
    // حجم الصوت المتوقع حسب الصيغة (mp3 بيتحوّل فبنقدّره من المدة)
    fun audioEst(fmt: String): Pair<Long, Boolean> =
        if (fmt == "mp3" && duration > 0) (duration * 23_750L) to true else audioBytes to audioApprox
}

class CookieSite(val domain: String, val count: Int)

// الدومين الأساسي (m.facebook.com → facebook.com ، x.co.uk → x.co.uk)
fun baseDomain(host: String): String {
    val p = host.lowercase().removePrefix("www.").split(".").filter { it.isNotEmpty() }
    if (p.size <= 2) return p.joinToString(".")
    val second = setOf("co", "com", "org", "net", "gov", "edu", "ac")
    return if (p[p.size - 2] in second && p.last().length == 2) p.takeLast(3).joinToString(".") else p.takeLast(2).joinToString(".")
}

object Incoming {
    val v = MutableStateFlow<String?>(null)
    val clip = MutableStateFlow<String?>(null)   // رابط منسوخ في الكليبورد
}

object Prefs {
    lateinit var sp: SharedPreferences
    var wifiOnly by mutableStateOf(false)
    var subs by mutableStateOf(false)
    var concurrent by mutableIntStateOf(2)
    var audioFmt by mutableStateOf("m4a")
    var audioQ by mutableIntStateOf(1)         // جودة MP3: 0 = 320، 1 = 192، 2 = 128
    var audioCover by mutableStateOf(true)     // غلاف الألبوم جوه ملف الصوت
    var limit by mutableIntStateOf(0)          // حد السرعة MB/s (0 = بدون)
    var subsMode by mutableIntStateOf(0)       // 0 تضمين، 1 ملف منفصل، 2 الاتنين، 3 ترجمة فقط (من غير فيديو)
    var organize by mutableIntStateOf(0)       // 0 عادي، 1 حسب الموقع، 2 حسب القناة
    var folder by mutableStateOf("Grab")
    var saveThumb by mutableStateOf(false)
    var saveDesc by mutableStateOf(false)
    var shareQuick by mutableStateOf(false)   // من نافذة المشاركة: ابدأ التحميل فورًا بالإعدادات الافتراضية
    var nameTpl by mutableIntStateOf(0)       // 0 العنوان، 1 القناة - العنوان، 2 التاريخ - العنوان، 3 العنوان [المعرّف]
    var embedMeta by mutableStateOf(true)     // بيانات الملف (العنوان والقناة) جوه الفيديو والصوت
    var subLangs by mutableStateOf("ar.*,en.*")
    var treeUri by mutableStateOf("")         // مجلد اختاره المستخدم (SAF). فاضي = Movies/Music
    var schedOn by mutableStateOf(false)
    var schedFrom by mutableIntStateOf(1)
    var schedTo by mutableIntStateOf(7)
    var chargingOnly by mutableStateOf(false)
    var theme by mutableIntStateOf(0)         // 0 النظام، 1 فاتح، 2 داكن
    var thumbMode by mutableIntStateOf(0)     // للفيديو: 0 بدون، 2 مدمجة في الفيديو، 3 ملف منفصل، 4 مدمجة + منفصلة
    var thumbFmt by mutableStateOf("jpg")     // صيغة الصورة: jpg / png / webp
    var vFmt by mutableIntStateOf(0)          // حاوية الفيديو: 0 MP4، 1 MKV، 2 WEBM
    var vCodec by mutableIntStateOf(0)        // 0 تلقائي، 1 H.264، 2 VP9، 3 AV1
    var fpsMax by mutableIntStateOf(0)        // أقصى إطارات (0 = بدون حد)
    var chapters by mutableStateOf(false)     // تضمين فصول الفيديو
    var dubMode by mutableIntStateOf(0)       // 0 الصوت الأصلي، 1 لغة مفضلة، 2 كل الأصوات في ملف MKV
    var dubLang by mutableStateOf("ar")
    var homeTab by mutableIntStateOf(0)       // آخر تبويب في الرئيسية: 0 فيديو، 1 صوت، 2 صورة
    var firstRun by mutableStateOf(true)      // شرح الاستخدام بيظهر أول فتح بس
    var accent by mutableIntStateOf(0)        // 0 لون الخلفية، 1 أزرق، 2 أخضر، 3 بنفسجي، 4 برتقالي، 5 وردي

    fun load(c: Context) {
        sp = c.getSharedPreferences("p", 0)
        wifiOnly = sp.getBoolean("w", false); subs = sp.getBoolean("s", false)
        concurrent = sp.getInt("c", 2); audioFmt = sp.getString("a", "m4a") ?: "m4a"
        audioQ = sp.getInt("aq", 1); audioCover = sp.getBoolean("acv", true)
        shareQuick = sp.getBoolean("q", false)
        nameTpl = sp.getInt("nt", 0); embedMeta = sp.getBoolean("em", true); subLangs = sp.getString("sl", "ar.*,en.*") ?: "ar.*,en.*"
        treeUri = sp.getString("tu", "") ?: ""
        schedOn = sp.getBoolean("so", false); schedFrom = sp.getInt("sfr", 1); schedTo = sp.getInt("sto", 7)
        chargingOnly = sp.getBoolean("co", false); theme = sp.getInt("tm", 0); accent = sp.getInt("ac", 0)
        firstRun = sp.getBoolean("fr", true)
        thumbMode = sp.getInt("tmo", if (sp.getBoolean("th", false)) 3 else 0)
        if (thumbMode == 1) thumbMode = 0   // «صورة فقط» بقت تبويب لوحدها في الرئيسية
        thumbFmt = sp.getString("tf", "jpg") ?: "jpg"
        vFmt = sp.getInt("vf", 0); vCodec = sp.getInt("vc", 0); fpsMax = sp.getInt("fp", 0); chapters = sp.getBoolean("ch", false)
        homeTab = sp.getInt("ht", 0).coerceIn(0, 2)
        dubMode = sp.getInt("dm", 0); dubLang = sp.getString("dl", "ar") ?: "ar"
        limit = sp.getInt("l", 0); subsMode = sp.getInt("sm", 0); organize = sp.getInt("o", 0)
        folder = sp.getString("f", "Grab") ?: "Grab"; saveThumb = sp.getBoolean("th", false); saveDesc = sp.getBoolean("de", false)
    }
    // آخر رابط كتبته/لصقته — بيفضل محفوظ حتى لو التطبيق اتقفل
    var draft: String
        get() = sp.getString("d", "") ?: ""
        set(v) { sp.edit().putString("d", v).apply() }

    var lastClip: String
        get() = sp.getString("lc", "") ?: ""
        set(v) { sp.edit().putString("lc", v).apply() }

    fun save() {
        sp.edit().putBoolean("w", wifiOnly).putBoolean("s", subs).putInt("c", concurrent).putString("a", audioFmt).putBoolean("q", shareQuick)
            .putInt("aq", audioQ).putBoolean("acv", audioCover)
            .putInt("l", limit).putInt("sm", subsMode).putInt("o", organize).putString("f", folder)
            .putBoolean("th", saveThumb).putBoolean("de", saveDesc)
            .putInt("nt", nameTpl).putBoolean("em", embedMeta).putString("sl", subLangs).putString("tu", treeUri)
            .putBoolean("so", schedOn).putInt("sfr", schedFrom).putInt("sto", schedTo).putBoolean("co", chargingOnly)
            .putInt("tm", theme).putInt("ac", accent).putBoolean("fr", firstRun).putInt("tmo", thumbMode).putString("tf", thumbFmt)
            .putInt("vf", vFmt).putInt("vc", vCodec).putInt("fp", fpsMax).putBoolean("ch", chapters).putInt("ht", homeTab)
            .putInt("dm", dubMode).putString("dl", dubLang).apply()
    }
}

object Repo {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, ex -> status.value = clean(ex) })
    val tasks = mutableStateListOf<Task>()
    val library = mutableStateListOf<Item>()
    val ready = mutableStateOf(false)
    val status = mutableStateOf("")
    val hasCookies = mutableStateOf(false)
    val cookieSites = mutableStateListOf<CookieSite>()
    val history = mutableStateListOf<Hist>()
    // لما تحميل صورة/ترجمة بس يخلص (مش بيدخل المكتبة) الواجهة بتعرض رسالة
    val extrasTick = mutableIntStateOf(0)
    @Volatile var extrasMsg = ""
    lateinit var app: Context
    // عدد التحميلات المتزامنة بيتقرأ من الإعدادات في كل مرة، فالتغيير بيتطبّق فورًا
    private val slotLock = Any()
    private var slots = 0
    private val initLock = Mutex()
    private var updatedThisSession = false
    private val media = setOf("mp4", "mkv", "webm", "mov", "mp3", "m4a", "opus", "ogg", "aac", "flac", "wav")

    private val cookieFile get() = File(app.filesDir, "cookies.txt")

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        try { Prefs.load(app) } catch (_: Throwable) {}
        loadLib()
        loadTasks()
        loadHist()
        refreshCookies()
        try {
            val nm = app.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("dl", "التحميل", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel("done", "اكتمل التحميل", NotificationManager.IMPORTANCE_DEFAULT))
        } catch (_: Throwable) {}
        scope.launch { pruneLibrary() }
        scope.launch {
            try {
                ensureEngine()
                if (System.currentTimeMillis() - Prefs.sp.getLong("u", 0) > 43_200_000) { delay(30_000); updateBlocking() }
            } catch (e: Throwable) { status.value = clean(e) }
        }
    }

    // يتجهّز المحرك عند أول استخدام، ولو فشل مرة بيحاول تاني في المرة الجاية
    suspend fun ensureEngine() {
        if (ready.value) return
        initLock.withLock {
            if (ready.value) return
            YoutubeDL.getInstance().init(app)
            try { FFmpeg.getInstance().init(app) } catch (_: Throwable) {}
            ready.value = true
            status.value = ""
        }
    }

    // ───── أخطاء مفهومة بالعربي ─────
    fun clean(e: Throwable): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            m.contains("not a bot", true) || m.contains("Sign in to confirm", true) ->
                "يوتيوب طلب تأكيد إنك مش بوت. قفّل الـ VPN أو سجّل دخول من «المتصفح الصغير» في الإعدادات."
            m.contains("Unable to resolve host", true) || m.contains("timed out", true) || m.contains("Network is unreachable", true) ->
                "مشكلة في الاتصال بالنت"
            m.contains("403") || m.contains("Forbidden", true) ->
                "يوتيوب رفض التحميل (403). من الإعدادات ← المحرك اضغط «تحديث» وجرّب تاني."
            m.contains("Private video", true) -> "الفيديو خاص"
            m.contains("Video unavailable", true) -> "الفيديو مش متاح"
            m.contains("age", true) && m.contains("confirm", true) -> "الفيديو مقيّد بالعمر، سجّل دخول من «المتصفح الصغير» في الإعدادات"
            m.contains("--cookies", true) || m.contains("need to log in", true) || m.contains("login required", true) ||
                m.contains("requires authentication", true) ->
                "الموقع ده محتاج تسجيل دخول — افتح «المتصفح الصغير» من الإعدادات وسجّل دخول وبعدين جرّب تاني"
            else -> m.lines().lastOrNull { it.isNotBlank() }?.take(160) ?: "error"
        }
    }

    fun version(): String = try { YoutubeDL.version(app) ?: "" } catch (e: Throwable) { "" }

    // ───── تحديث المحرك ─────
    fun update() = scope.launch { updateBlocking() }

    private suspend fun updateBlocking() {
        status.value = "جاري تحديث المحرك…"
        try {
            ensureEngine()
            val r = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.NIGHTLY)
            Prefs.sp.edit().putLong("u", System.currentTimeMillis()).apply()
            status.value = if (r?.name == "DONE") "تم التحديث ✓" else "المحرك محدّث ✓"
        } catch (e: Throwable) { status.value = "فشل التحديث: " + clean(e) }
    }

    // ───── الكوكيز (أي موقع) ─────
    // ملف واحد cookies.txt بصيغة Netscape فيه كوكيز كل المواقع، وyt-dlp بيختار منه اللي يناسب كل رابط
    private fun isCookieLine(l: String) =
        l.isNotBlank() && (!l.startsWith("#") || l.startsWith("#HttpOnly_")) && l.count { it == '\t' } >= 6
    private fun lineDomain(l: String) = l.removePrefix("#HttpOnly_").substringBefore('\t').trimStart('.').lowercase()
    private fun cookieLines(): List<String> = try {
        if (cookieFile.exists()) cookieFile.readLines().map { it.trimEnd('\r') }.filter { isCookieLine(it) } else emptyList()
    } catch (_: Throwable) { emptyList() }

    fun refreshCookies() {
        val groups = cookieLines().groupingBy { baseDomain(lineDomain(it)) }.eachCount()
        cookieSites.clear()
        groups.entries.sortedBy { it.key }.forEach { cookieSites.add(CookieSite(it.key, it.value)) }
        hasCookies.value = groups.isNotEmpty()
    }

    // بيضيف كوكيز جديدة، وبيستبدل القديمة لنفس الموقع (من غير ما يمسّ باقي المواقع)
    private fun mergeCookies(add: List<String>) {
        val doms = add.map { baseDomain(lineDomain(it)) }.toSet()
        val keep = cookieLines().filter { baseDomain(lineDomain(it)) !in doms }
        cookieFile.writeText("# Netscape HTTP Cookie File\n" + (keep + add).joinToString("\n") + "\n")
        refreshCookies()
    }

    fun importCookies(uri: Uri): Boolean = try {
        val txt = app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val lines = txt.lines().map { it.trimEnd('\r') }.filter { isCookieLine(it) }
        if (lines.isEmpty()) false else { mergeCookies(lines); true }
    } catch (_: Throwable) { false }

    // مواقع بتسجّل دخول عن طريق دومين تاني (يوتيوب ← جوجل…)
    private fun related(base: String): List<Pair<String, String>> = when (base) {
        "youtube.com" -> listOf("https://accounts.google.com/" to "google.com", "https://www.google.com/" to "google.com",
            "https://accounts.youtube.com/" to "youtube.com")
        "google.com" -> listOf("https://www.youtube.com/" to "youtube.com", "https://accounts.google.com/" to "google.com")
        "x.com", "twitter.com" -> listOf("https://x.com/" to "x.com", "https://twitter.com/" to "twitter.com")
        "facebook.com" -> listOf("https://m.facebook.com/" to "facebook.com", "https://www.facebook.com/" to "facebook.com")
        else -> emptyList()
    }

    // بناخد كوكيز الموقع اللي مفتوح في المتصفح الصغير ونكتبها بصيغة Netscape.
    // بترجّع (الدومين، عدد الكوكيز) أو null لو مفيش كوكيز
    fun saveWebCookies(pageUrl: String): Pair<String, Int>? = try {
        val host = Uri.parse(pageUrl).host ?: throw Exception("no host")
        val base = baseDomain(host)
        val cm = android.webkit.CookieManager.getInstance()
        cm.flush()
        val jar = linkedMapOf<String, LinkedHashMap<String, String>>()   // دومين ← (اسم الكوكي ← القيمة)
        fun grab(u: String, dom: String) {
            cm.getCookie(u)?.split(";")?.forEach { c ->
                val kv = c.trim(); val i = kv.indexOf('=')
                if (i > 0) { jar.getOrPut(dom) { linkedMapOf() }[kv.substring(0, i)] = kv.substring(i + 1) }
            }
        }
        grab(pageUrl, base); grab("https://$base/", base); grab("https://www.$base/", base)
        related(base).forEach { (u, d) -> grab(u, d) }
        val lines = jar.flatMap { (d, m) -> m.map { (k, v) -> ".$d\tTRUE\t/\tTRUE\t2147483647\t$k\t$v" } }
        if (lines.isEmpty()) null else { mergeCookies(lines); base to lines.size }
    } catch (_: Throwable) { null }

    fun deleteCookies(domain: String) {
        try {
            val keep = cookieLines().filter { baseDomain(lineDomain(it)) != domain }
            if (keep.isEmpty()) cookieFile.delete()
            else cookieFile.writeText("# Netscape HTTP Cookie File\n" + keep.joinToString("\n") + "\n")
        } catch (_: Throwable) {}
        refreshCookies()
    }

    fun clearCookies() { try { cookieFile.delete() } catch (_: Throwable) {}; refreshCookies() }

    // ───── استراتيجيات يوتيوب: بنجرّب واحدة ورا التانية لحد ما واحدة تنجح ─────
    private val ytArgs = listOf<String?>(
        "youtube:player_client=android_vr",     // 0: من غير JS، بس أحيانًا بيرجّع 403 أو 360p بس
        "youtube:player_client=android",        // 1
        "youtube:player_client=tv",             // 2
        null,                                   // 3: الافتراضي
        "youtube:player_client=mweb",           // 4
        "youtube:player_client=web_safari"      // 5
    )
    // ترتيب المحاولات: لو فيه كوكيز نبدأ بالعملاء اللي بيدعموها
    private fun order(url: String): List<Int> =
        if (!isYt(url)) listOf(0, 1)
        else if (cookieSites.any { it.domain == "youtube.com" || it.domain == "google.com" }) listOf(2, 3, 4, 1, 0, 5)
        else listOf(0, 1, 2, 3, 4, 5)

    // أخطاء مفيش فايدة من تكرارها
    private fun fatal(e: Throwable): Boolean {
        val m = e.message ?: return false
        return listOf("Private video", "Video unavailable", "Unsupported URL", "has been removed", "members-only",
            "not available in your country", "copyright", "This video is not available").any { m.contains(it, true) }
    }
    private fun netErr(e: Throwable): Boolean {
        val m = e.message ?: return false
        return listOf("Unable to resolve host", "timed out", "Network is unreachable", "Connection reset").any { m.contains(it, true) }
    }
    private fun subErr(e: Throwable): Boolean {
        val m = e.message ?: return false
        return m.contains("subtitle", true) || m.contains("timedtext", true)
    }
    private fun isYt(u: String) = u.contains("youtube.com", true) || u.contains("youtu.be", true) || u.contains("youtube-nocookie.com", true)

    private fun common(r: YoutubeDLRequest, strat: Int, url: String) {
        r.addOption("--socket-timeout", "15")
        r.addOption("--retries", "5")
        r.addOption("--fragment-retries", "10")
        r.addOption("--extractor-retries", "2")
        r.addOption("--retry-sleep", "linear=1::2")
        r.addOption("--force-ipv4")                 // IPv6 على الموبايل سبب رئيسي لـ 403/429 المتقطع
        r.addOption("--http-chunk-size", "10M")     // تحميل على أجزاء بيتفادى تقييد السرعة/القطع من يوتيوب
        if (isYt(url)) ytArgs.getOrNull(strat)?.let { r.addOption("--extractor-args", it) }
        if (hasCookies.value && cookieFile.exists()) r.addOption("--cookies", cookieFile.absolutePath)
    }

    private val infoDir get() = File(app.cacheDir, "info").apply { mkdirs() }
    private fun infoFile(url: String) = File(infoDir, key(url).hashCode().toString() + ".json")
    private fun cacheInfo(url: String, json: String) {
        try {
            infoDir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(5)?.forEach { it.delete() }
            infoFile(url).writeText(json)
        } catch (_: Throwable) {}
    }

    // المعاينة: بنجرّب طريقتين في نفس الوقت (أول واحدة تنجح تكسب) وبنحفظ النتيجة عشان التحميل يبدأ فورًا
    suspend fun fetch(url: String): Info = withContext(Dispatchers.IO) {
        ensureEngine()
        val tries = if (isYt(url)) order(url).take(2) else listOf(0)
        val tag = "pv${System.nanoTime()}"
        coroutineScope {
            val res = CompletableDeferred<Info>()
            var last: Throwable? = null
            val jobs = tries.map { i ->
                launch {
                    try {
                        val r = YoutubeDLRequest(url).apply {
                            addOption("--no-playlist"); addOption("--no-warnings"); addOption("--dump-single-json")
                            common(this, i, url); addOption("--retries", "1"); addOption("--extractor-retries", "1")
                        }
                        val out = YoutubeDL.getInstance().execute(r, "${tag}_$i").out
                        val info = parseInfo(JSONObject(out))
                        cacheInfo(url, out); res.complete(info)
                    } catch (e: CancellationException) { throw e
                    } catch (e: Throwable) { last = e }
                }
            }
            launch { jobs.joinAll(); if (!res.isCompleted) res.completeExceptionally(last ?: Exception("error")) }
            try { res.await() } finally {
                tries.forEach { try { YoutubeDL.getInstance().destroyProcessById("${tag}_$it") } catch (_: Throwable) {} }
            }
        }
    }

    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k)
    private fun JSONObject.n(k: String): Double = if (isNull(k)) 0.0 else optDouble(k, 0.0)
    private fun codecRank(v: String) = when {
        v.startsWith("av01") -> 4; v.startsWith("vp9") || v.startsWith("vp09") -> 3
        v.startsWith("hev") || v.startsWith("hvc") -> 2; v.startsWith("avc") -> 1; else -> 0
    }

    // الحجم الحقيقي لكل جودة = أحسن فيديو بالجودة دي (اللي yt-dlp هيختاره فعلًا) + أحسن صوت.
    // لو الموقع مادّيش الحجم بنقدّره من معدل البت × المدة
    private fun parseInfo(j: JSONObject): Info {
        val dur = j.n("duration")
        fun size(f: JSONObject): Pair<Long, Boolean> {
            val exact = f.n("filesize"); if (exact > 0) return exact.toLong() to false
            val ap = f.n("filesize_approx"); if (ap > 0) return ap.toLong() to true
            val tbr = f.n("tbr"); if (tbr > 0 && dur > 0) return (tbr * 125.0 * dur).toLong() to true
            return 0L to true
        }
        val arr = j.optJSONArray("formats")
        val all = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it) }.filter { it.s("ext") != "mhtml" }
        val vids = all.filter { it.s("vcodec") != "none" && it.n("height") > 0 }
        val audios = all.filter { it.s("vcodec") == "none" && it.s("acodec").let { a -> a.isNotEmpty() && a != "none" } }
        val aud = audios.sortedWith(compareBy<JSONObject>(
            { if (it.s("ext") == "m4a") 1 else 0 },
            { val a = it.n("abr"); if (a > 0) a else it.n("tbr") }
        )).lastOrNull()
        val audSize = aud?.let { size(it) } ?: (0L to false)

        val qs = vids.groupBy { f ->
            val h = f.n("height").toInt(); val w = f.n("width").toInt()
            val short = if (w > 0) minOf(w, h) else h
            LADDER.lastOrNull { it <= short * 1.08 } ?: LADDER.first()
        }.map { (q, fl) ->
            val best = fl.sortedWith(compareBy<JSONObject>(
                { if (it.s("ext") == "mp4") 1 else 0 }, { it.n("fps") }, { codecRank(it.s("vcodec")) }, { it.n("tbr") }
            )).last()
            val v = size(best)
            var bytes = v.first; var ap = v.second
            if (bytes > 0 && best.s("acodec") == "none" && aud != null) { bytes += audSize.first; ap = ap || audSize.second }
            QOpt(q, bytes, ap)
        }.sortedByDescending { it.h }.take(7)

        val uploader = j.s("uploader").ifEmpty { j.s("channel") }
        return Info(j.s("title").ifEmpty { null }, j.s("thumbnail").ifEmpty { null }, uploader.ifEmpty { null },
            dur.toInt(), qs, audSize.first, audSize.second)
    }

    private val slotWait = mutableListOf<Task>()

    // الطابور: الأعلى أولوية الأول، وبعدين الأقدم. «ابدأ الآن» (force) بيتخطّى الحد
    private suspend fun <T> withSlot(t: Task, block: suspend () -> T): T {
        synchronized(slotLock) { slotWait.add(t) }
        try {
            while (true) {
                val got = synchronized(slotLock) {
                    val top = slotWait.maxWithOrNull(compareBy<Task>({ it.prio }, { -it.queuedAt }))
                    if (t.force || (slots < Prefs.concurrent.coerceIn(1, 3) && top === t)) { slotWait.remove(t); slots++; true } else false
                }
                if (got) break
                delay(300)
            }
        } catch (e: Throwable) { synchronized(slotLock) { slotWait.remove(t) }; throw e }
        try { return block() } finally { synchronized(slotLock) { slots-- } }
    }

    fun startNow(c: Context, t: Task) {
        t.force = true; t.prio = (tasks.maxOfOrNull { it.prio } ?: 0) + 1
        if (t.job?.isActive == true) return    // في الطابور أو شغّال: اللفّة الجاية هتشوف force
        enqueue(c, t)
    }

    // auto = استئناف تلقائي (من الخدمة أو عند فتح التطبيق) مش ضغطة من المستخدم
    fun enqueue(c: Context, t: Task, auto: Boolean = false) {
        if (t.job?.isActive == true) return          // شغّال أصلًا، ما نكرّرش
        val resumeStats = t.keep && (t.done > 0L || t.total > 0L)
        if (!auto) t.autoTries = 0
        if (t !in tasks) tasks.add(0, t)
        t.waitNet = false; t.lastTry = System.currentTimeMillis(); t.queuedAt = t.lastTry
        t.st = St.Queued; t.msg = ""
        // الاستئناف الحقيقي يحتفظ بما تم تنزيله، أما المهمة الجديدة فتبدأ بإحصاءات نظيفة.
        if (!resumeStats) {
            t.progress = 0f; t.speed = 0L; t.done = 0L; t.total = 0L; t.eta = 0L
        }
        saveTasks()
        if (!auto || !DownloadService.looping)
            try { c.startForegroundService(Intent(c, DownloadService::class.java)) } catch (_: Throwable) {}
        t.job = scope.launch { try { withSlot(t) { run(t) } } finally { saveTasks() } }
    }

    // ───── مراقبة النت والاستئناف التلقائي ─────
    private fun caps(cm: ConnectivityManager) = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    private fun metered(cm: ConnectivityManager) = caps(cm)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
    private fun netOk(cm: ConnectivityManager): Boolean {
        val c = caps(cm) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            !(Prefs.wifiOnly && !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    // إيقاف بسبب النت: بنسيب الجزء المحمّل وبنكمّل لوحدنا لما النت/الواي فاي يرجع
    fun pauseForNet(t: Task, msg: String, kind: Int = 0) {
        t.waitNet = true; t.waitKind = kind; t.waitSince = System.currentTimeMillis()
        t.keep = true; t.msg = msg; t.st = St.Canceled
        kill(t); t.job?.cancel()
        saveTasks()
    }

    // الجدولة (ساعات معينة) أو الشحن فقط. بترجّع سبب المنع أو null لو مسموح نبدأ
    fun blockReason(): String? {
        if (Prefs.chargingOnly) {
            val plugged = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            if (plugged == 0) return "في انتظار الشحن…"
        }
        if (Prefs.schedOn) {
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val f = Prefs.schedFrom; val to = Prefs.schedTo
            val inside = when { f == to -> true; f < to -> h in f until to; else -> h >= f || h < to }
            if (!inside) return "في انتظار الموعد (${f}:00)"
        }
        return null
    }

    // بتتنادى كل ثانية من خدمة التحميل. بترجّع عدد التحميلات المستنية (نت/موعد/شحن)
    fun tick(): Int {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val now = System.currentTimeMillis()
        val block = blockReason()
        val running = tasks.filter { it.st == St.Running || it.st == St.Queued }
        if (Prefs.wifiOnly && metered(cm))   // خرجنا من الواي فاي في نص التحميل
            running.forEach { pauseForNet(it, "اتوقف: الواي فاي مقطوع — هكمّل لما يرجع") }
        else if (block != null)              // خرجنا من نافذة الجدولة أو الشاحن اتفصل
            running.filter { !it.force }.forEach { pauseForNet(it, block, kind = 1) }
        val ok = netOk(cm)
        var waiting = 0
        for (t in tasks.toList()) {
            if (!t.waitNet || (t.st != St.Failed && t.st != St.Canceled)) continue
            if (t.waitKind == 0 && (now - t.waitSince > 30 * 60_000L || t.autoTries >= 5)) {
                t.waitNet = false; t.st = St.Failed; t.msg = "انتهى وقت الانتظار — اضغط إعادة"; saveTasks()
            } else {
                waiting++
                if (ok && (block == null || t.force) && now - t.lastTry > 20_000L) {
                    if (t.waitKind == 0) t.autoTries++
                    enqueue(app, t, auto = true)
                }
            }
        }
        return waiting
    }

    // عند فتح التطبيق: كمّل أي تحميل اتقطع (قفل التطبيق، انتهاء مهلة الخدمة، قطع النت)
    fun resumePending() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (!netOk(cm)) return
        if (blockReason() != null) return
        tasks.filter { it.waitNet && (it.st == St.Failed || it.st == St.Canceled) }.forEach { enqueue(app, it, auto = true) }
    }

    // ───── منع التكرار ─────
    fun key(raw: String): String {
        val s = URL_FIND.find(raw)?.value ?: raw.trim()
        val u = try { Uri.parse(s) } catch (_: Throwable) { return s.lowercase() }
        val host = (u.host ?: "").lowercase().removePrefix("www.").removePrefix("m.").removePrefix("music.")
        val segs = u.pathSegments
        if (host == "youtu.be") return "yt:" + (segs.firstOrNull() ?: "")
        if (host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")) {
            u.getQueryParameter("v")?.let { return "yt:$it" }
            if (segs.size >= 2 && segs[0] in setOf("shorts", "embed", "live", "v")) return "yt:${segs[1]}"
        }
        val drop = setOf("fbclid", "si", "igsh", "igshid", "feature", "gclid")
        val q = u.queryParameterNames.filter { !it.startsWith("utm_") && it !in drop }.sorted()
            .joinToString("&") { n -> "$n=${u.getQueryParameter(n)}" }
        return host + "/" + segs.joinToString("/") + if (q.isEmpty()) "" else "?$q"
    }
    private val URL_FIND = Regex("https?://\\S+")

    // الرابط ده اتحمّل قبل كده بنفس النوع (فيديو/صوت)، أو لسه في التحميل دلوقتي؟
    fun dupOf(url: String, audio: Boolean): Dup? {
        val k = key(url)
        tasks.firstOrNull { !it.playlist && !it.thumbOnly && it.audio == audio && key(it.url) == k &&
            (it.st == St.Running || it.st == St.Queued || it.waitNet) }?.let { return Dup(it.title, null) }
        library.firstOrNull { it.src.isNotEmpty() && it.audio == audio && key(it.src) == k }?.let { return Dup(it.title, it) }
        return null
    }

    // الملفات اللي اتمسحت من برّه التطبيق (جاليري/ملفات) بتتشال من المكتبة
    fun pruneLibrary() {
        val gone = library.toList().filter { i ->
            try {
                app.contentResolver.query(Uri.parse(i.uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { it.count == 0 } ?: false
            } catch (_: Throwable) { false }
        }
        if (gone.isNotEmpty()) { library.removeAll(gone); saveLib() }
    }

    private fun kill(t: Task) {
        try { YoutubeDL.getInstance().destroyProcessById(t.id) } catch (_: Throwable) {}
        try { YoutubeDL.getInstance().destroyProcessById(t.id + "_s") } catch (_: Throwable) {}
    }

    fun cancel(t: Task) {
        t.keep = false; t.waitNet = false; t.msg = ""; t.st = St.Canceled
        kill(t); t.job?.cancel()
        saveTasks()
    }

    // إيقاف مؤقت: بيوقف العملية لكن بيسيب الجزء المحمّل، والاستئناف بيكمّل منه
    fun pause(t: Task) {
        t.keep = true; t.waitNet = false; t.msg = "متوقف مؤقتًا"; t.st = St.Canceled
        kill(t); t.job?.cancel()
        saveTasks()
    }

    fun drop(t: Task) { tasks.remove(t); try { partialDir(t).deleteRecursively() } catch (_: Throwable) {}; saveTasks() }

    fun retryAll(c: Context) { tasks.filter { it.st == St.Failed || it.st == St.Canceled }.forEach { enqueue(c, it) } }
    fun clearFailed() {
        tasks.filter { it.st == St.Failed || it.st == St.Canceled }.forEach { try { partialDir(it).deleteRecursively() } catch (_: Throwable) {} }
        tasks.removeAll { it.st == St.Failed || it.st == St.Canceled }; saveTasks()
    }

    private fun partialDir(t: Task) = File(app.filesDir, "partial/${t.id}")

    private suspend fun run(t: Task) {
        if (t.st == St.Canceled) return
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (Prefs.wifiOnly && metered(cm)) {   // مش فاشل: بيستنى الواي فاي ويكمّل لوحده
            t.waitNet = true; t.waitSince = System.currentTimeMillis()
            t.waitKind = 0
            t.keep = true; t.st = St.Canceled; t.msg = "في انتظار الواي فاي…"; return
        }
        if (!t.force) blockReason()?.let {   // برّه الموعد أو مش على الشاحن
            t.waitNet = true; t.waitKind = 1; t.waitSince = System.currentTimeMillis()
            t.keep = true; t.st = St.Canceled; t.msg = it; return
        }
        t.st = St.Running; t.keep = false
        val dir = partialDir(t).apply { mkdirs() }
        try {
            ensureEngine()
            // الروابط اللي اتضافت بدون معاينة (دفعات/مشاركة سريعة): نجيب العنوان والصورة في الخلفية
            if (t.title == t.url && !t.playlist) scope.launch {
                try {
                    val i = fetch(t.url)
                    i.title?.let { t.title = it }
                    if (t.thumb == null) t.thumb = i.thumbnail
                    t.uploader = i.uploader
                    saveTasks()
                } catch (_: Throwable) {}
            }
            val plan = order(t.url).toMutableList()
            var idx = 0; var updated = updatedThisSession; var last: Throwable? = null; var ok = false; var netTries = 0
            val sm = Prefs.subsMode
            val skipVideo = t.thumbOnly || (!t.audio && t.subs && sm == 3)   // صورة فقط / ترجمة فقط: من غير تحميل الفيديو
            var useSubs = t.subs && !t.audio && !skipVideo && (sm == 0 || sm == 2)
            var useInfo = !t.playlist && !skipVideo
            var curH = t.height; var round2 = false; var okS = plan.first()
            while (idx < plan.size) {
                val s = plan[idx++]
                if (t.st == St.Canceled) return
                t.msg = if (idx > 1) "بجرّب طريقة تانية ($idx/${plan.size})…" else ""
                try { if (skipVideo) extrasOnly(t, dir, s) else attempt(t, dir, s, useSubs, curH, useInfo); ok = true; okS = s; break }
                catch (e: CancellationException) { throw e }
                catch (e: Throwable) {
                    if (t.st == St.Canceled) return
                    last = e
                    // أخطاء النت: بنسيب الجزء المحمّل عشان نكمّل منه. غير كده بنبدأ نضيف
                    if (!netErr(e)) { dir.deleteRecursively(); dir.mkdirs(); t.progress = 0f }
                    when {
                        fatal(e) -> break
                        // فشل الترجمة (429 غالبًا) ما يوقّفش التحميل: نكمّل من غيرها
                        useInfo && !netErr(e) -> { useInfo = false; t.msg = ""; plan.add(idx, s) }   // المعلومات المحفوظة مشتغلتش: نستخرج من جديد
                        useSubs && subErr(e) -> { useSubs = false; t.msg = "الترجمة مش متاحة، بحمّل من غيرها…"; plan.add(idx, s); delay(500) }
                        curH > 0 && (e.message ?: "").contains("format is not available", true) ->
                            { curH = lowerQ(curH); t.msg = "الجودة مش متاحة، بجرّب أقل…"; plan.add(idx, s) }
                        netErr(e) -> {
                            if (++netTries > 3) break
                            t.msg = "مشكلة في النت… بحاول تاني"; plan.add(idx, s); delay(2000)
                        }
                        idx == plan.size && !updated -> {
                            updated = true; updatedThisSession = true
                            t.msg = "بيحدّث المحرك ويحاول تاني…"
                            updateBlocking(); plan.addAll(order(t.url).take(2))
                        }
                        idx == plan.size && !round2 -> {   // لفّة تانية، وبجودة أقل لو كانت محددة
                            round2 = true
                            if (curH > 0) { curH = lowerQ(curH); t.msg = "بجرّب جودة أقل…" } else t.msg = "بحاول تاني بعد ثواني…"
                            delay(4000); plan.addAll(order(t.url))
                        }
                        else -> delay(500)
                    }
                }
            }
            if (!ok) throw last ?: Exception("فشل التحميل")
            // الترجمة كملف منفصل: خطوة بعد الفيديو، وفشلها ما يأثرش على التحميل
            if (!skipVideo && t.subs && !t.audio && (sm == 1 || sm == 2)) {
                t.msg = "بجيب الترجمة…"
                try { subsOnly(t, dir, okS) } catch (e: CancellationException) { throw e } catch (_: Throwable) {}
            }
            if (skipVideo) finishExtras(t, dir) else finish(t, dir)
        } catch (e: CancellationException) {
            if (t.st != St.Canceled) { t.st = St.Failed; t.msg = "اتوقف" }
        } catch (e: Throwable) {
            if (t.st != St.Canceled) {
                t.st = St.Failed; t.msg = clean(e)
                if (netErr(e)) {   // النت قطع: الجزء المحمّل متحفوظ وهنكمّل لما يرجع
                    t.waitNet = true; t.waitKind = 0; t.waitSince = System.currentTimeMillis(); t.msg = "مفيش نت — هكمّل لما يرجع"
                }
            }
        } finally {
            t.force = false
            if (t.st == St.Done || (t.st == St.Canceled && !t.keep)) dir.deleteRecursively()
        }
    }

    // سطر التقدّم بتاع yt-dlp: [download]  45.3% of ~ 10.00MiB at 2.00MiB/s ETA 00:03
    private val PROG = Regex("""([\d.]+)%\s+of\s+~?\s*([\d.,]+)\s*([KMGT]?i?B)(?:\s+at\s+([\d.,]+)\s*([KMGT]?i?B)/s)?""")
    private fun bytesOf(n: String, u: String): Long {
        val v = n.replace(",", "").toDoubleOrNull() ?: return 0L
        val k = when (u.firstOrNull()?.uppercaseChar()) {
            'K' -> 1024.0; 'M' -> 1048576.0; 'G' -> 1073741824.0; 'T' -> 1099511627776.0; else -> 1.0
        }
        return (v * k).toLong()
    }

    private fun attempt(t: Task, dir: File, strat: Int, withSubs: Boolean, height: Int, useInfo: Boolean) {
        val r = YoutubeDLRequest(t.url)
        if (useInfo) infoFile(t.url).takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < 2_700_000L }
            ?.let { r.addOption("--load-info-json", it.absolutePath) }
        r.addOption("-o", "${dir.absolutePath}/${nameTpl()}.%(ext)s")
        r.addOption("--no-mtime"); r.addOption("--continue"); common(r, strat, t.url)
        if (Prefs.embedMeta) r.addOption("--embed-metadata")
        if (Prefs.limit > 0) r.addOption("--limit-rate", "${Prefs.limit}M")
        r.addOption("--concurrent-fragments", "8")
        r.addOption(if (t.playlist) "--yes-playlist" else "--no-playlist")

        // الصورة: مدمجة جوه الملف و/أو ملف منفصل
        val tm = Prefs.thumbMode
        val mkvOut = !t.audio && Prefs.dubMode == 2
        val webmOut = !t.audio && !mkvOut && Prefs.vFmt == 2      // WEBM مبيدعمش غلاف مدمج
        val embedThumb = if (t.audio) Prefs.audioCover && Prefs.audioFmt != "opus" else (tm == 2 || tm == 4) && !webmOut
        val sepThumb = !t.audio && (tm == 3 || tm == 4)
        if (embedThumb) r.addOption("--embed-thumbnail")
        if (sepThumb) r.addOption("--write-thumbnail")
        if (embedThumb || sepThumb)
            r.addOption("--convert-thumbnails", if (t.audio || (embedThumb && Prefs.thumbFmt == "webp")) "jpg" else Prefs.thumbFmt)

        if (Prefs.saveDesc) r.addOption("--write-description")
        if (!t.audio && Prefs.chapters) r.addOption("--embed-chapters")
        if (!t.playlist && (t.clipStart.isNotBlank() || t.clipEnd.isNotBlank()))
            r.addOption("--download-sections", "*${t.clipStart.ifBlank { "0" }}-${t.clipEnd.ifBlank { "inf" }}")
        if (t.audio) {
            // m4a: بيتحمّل جاهز من غير إعادة ترميز (أسرع بكتير من mp3 على الموبايل)
            r.addOption("-f", if (Prefs.audioFmt == "m4a") "ba[ext=m4a]/ba/b" else "ba/b")
            if (Prefs.dubMode == 1) r.addOption("-S", "lang:${Prefs.dubLang}")
            r.addOption("-x"); r.addOption("--audio-format", Prefs.audioFmt)
            if (Prefs.audioFmt == "mp3") r.addOption("--audio-quality", when (Prefs.audioQ) { 0 -> "320K"; 2 -> "128K"; else -> "192K" })
        } else {
            if (mkvOut) { r.addOption("-f", "bv*+mergeall[vcodec=none]/bv*+ba/b"); r.addOption("--audio-multistreams") }
            else r.addOption("-f", "bv*+ba/b")
            // ترتيب التفضيلات: اللغة، الدقة، الكودك، الإطارات، الحاوية
            val sort = mutableListOf<String>()
            if (Prefs.dubMode == 1) sort.add("lang:${Prefs.dubLang}")
            if (height > 0) sort.add("res:$height")
            when (Prefs.vCodec) { 1 -> sort.add("vcodec:h264"); 2 -> sort.add("vcodec:vp9"); 3 -> sort.add("vcodec:av01") }
            if (Prefs.fpsMax > 0) sort.add("fps:${Prefs.fpsMax}")
            when (Prefs.vFmt) { 0 -> sort.add("ext:mp4:m4a"); 2 -> sort.add("ext:webm:webm") }
            if (sort.isNotEmpty()) r.addOption("-S", sort.joinToString(","))
            r.addOption("--merge-output-format", if (mkvOut) "mkv" else when (Prefs.vFmt) { 1 -> "mkv"; 2 -> "webm"; else -> "mp4" })
            if (withSubs) {
                r.addOption("--write-subs"); r.addOption("--write-auto-subs")
                r.addOption("--sub-langs", Prefs.subLangs); r.addOption("--embed-subs")
            }
        }

        // التقدّم: بنحسب اللي نزل والباقي والسرعة. الفيديو والصوت بيتحمّلوا كملفين، فلما النسبة ترجع للصفر نعدّ الملف اللي فات
        var base = 0L; var prevP = 0f; var prevTot = 0L; var lastPersist = 0L
        YoutubeDL.getInstance().execute(r, t.id, callback = { p, eta, line ->
            if (p >= 0f) {
                var cur = prevTot; var sp = 0L
                val m = PROG.find(line)
                if (m != null) {
                    cur = bytesOf(m.groupValues[2], m.groupValues[3])
                    if (m.groupValues[4].isNotEmpty()) sp = bytesOf(m.groupValues[4], m.groupValues[5])
                }
                if (p + 30f < prevP && prevTot > 0L) base += prevTot
                prevP = p; if (cur > 0L) prevTot = cur
                val got = base + (cur * (p / 100.0)).toLong()
                val tot = maxOf(if (t.expect > 0L) t.expect else base + cur, got)
                t.done = got; t.total = tot
                if (sp > 0L) t.speed = if (t.speed == 0L) sp else (t.speed * 0.7 + sp * 0.3).toLong()
                val overall = t.expect > 0L && cur > 0L
                t.progress = if (overall) (got.toDouble() / tot).toFloat().coerceIn(0f, 1f) else (p / 100f).coerceIn(0f, 1f)
                val left = tot - got
                t.eta = if (t.speed > 0L && left > 0L) left / t.speed else eta
                if (p >= 99.5f && (!overall || t.progress >= 0.97f)) t.msg = "جاري الدمج والحفظ…"
                // لا ننتظر نهاية المهمة لحفظ الإحصاءات؛ هذا يحافظ على آخر حالة قابلة للاستئناف.
                val now = System.currentTimeMillis()
                if (now - lastPersist >= 1_000L) { lastPersist = now; saveTasks() }
            }
        })
    }

    // قالب اسم الملف حسب اختيار المستخدم
    private fun nameTpl() = when (Prefs.nameTpl) {
        1 -> "%(uploader&{} - |)s%(title).80s"
        2 -> "%(upload_date>%Y-%m-%d&{} - |)s%(title).80s"
        3 -> "%(title).80s [%(id)s]"
        else -> "%(title).80s"
    }

    // صورة فقط و/أو ترجمة فقط، من غير تحميل الفيديو
    private fun extrasOnly(t: Task, dir: File, strat: Int) {
        val r = YoutubeDLRequest(t.url)
        r.addOption("-o", "${dir.absolutePath}/${nameTpl()}.%(ext)s")
        r.addOption("--skip-download"); common(r, strat, t.url)
        r.addOption(if (t.playlist) "--yes-playlist" else "--no-playlist")
        if (t.thumbOnly || Prefs.thumbMode == 3 || Prefs.thumbMode == 4) {
            r.addOption("--write-thumbnail"); r.addOption("--convert-thumbnails", Prefs.thumbFmt)
        }
        if (t.subs) {
            r.addOption("--write-subs"); r.addOption("--write-auto-subs")
            r.addOption("--sub-langs", Prefs.subLangs); r.addOption("--convert-subs", "srt")
            r.addOption("--sleep-subtitles", "2")
        }
        if (Prefs.saveDesc && !t.thumbOnly) r.addOption("--write-description")
        YoutubeDL.getInstance().execute(r, t.id)
    }

    private fun finishExtras(t: Task, dir: File) {
        val extras = dir.walkTopDown().filter { it.isFile && (it.extension.lowercase() in imgExt || it.extension.lowercase() in txtExt) }.toList()
        if (extras.isEmpty()) throw Exception("ما اتنتجش ملف")
        val sub = subFolder(t)
        extras.forEach { try { save(it, sub) } catch (_: Throwable) {} }
        t.progress = 1f; t.st = St.Done
        notifyDone(t)
        tasks.remove(t)
        extrasMsg = if (t.thumbOnly) "اتحفظت الصورة" else "اتحفظت الترجمة"
        extrasTick.intValue++
    }

    private fun subsOnly(t: Task, dir: File, strat: Int) {
        val r = YoutubeDLRequest(t.url)
        r.addOption("-o", "${dir.absolutePath}/${nameTpl()}.%(ext)s")
        r.addOption("--skip-download"); common(r, strat, t.url)
        r.addOption(if (t.playlist) "--yes-playlist" else "--no-playlist")
        r.addOption("--write-subs"); r.addOption("--write-auto-subs")
        r.addOption("--sub-langs", Prefs.subLangs); r.addOption("--convert-subs", "srt")
        r.addOption("--sleep-subtitles", "2")
        YoutubeDL.getInstance().execute(r, t.id + "_s")
    }

    private val intermediate = Regex("""\.f\d+(-\w+)?\.[A-Za-z0-9]+$""")
    private val imgExt = setOf("jpg", "jpeg", "png", "webp")
    private val txtExt = setOf("srt", "vtt", "description")

    private fun finish(t: Task, dir: File) {
        val all = dir.walkTopDown().filter { it.isFile }.toList()
        val files = all.filter { it.extension.lowercase() in media && !intermediate.containsMatchIn(it.name) }
        if (files.isEmpty()) throw Exception("ما اتنتجش ملف")
        val sub = subFolder(t)
        files.forEach { f ->
            val u = save(f, sub) ?: return@forEach
            val isAudio = (MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "").startsWith("audio")
            library.add(0, Item(f.nameWithoutExtension, t.thumb, u, isAudio, t.url))
        }
        all.filter { it.extension.lowercase().let { e -> e in imgExt || e in txtExt } }.forEach {
            try { save(it, sub) } catch (_: Throwable) {}
        }
        saveLib()
        t.progress = 1f; t.st = St.Done
        notifyDone(t)
        tasks.remove(t)
    }

    private fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|\n\r]"""), "_").trim().take(40)

    fun siteOf(u: String): String {
        val h = try { Uri.parse(u).host ?: "" } catch (_: Throwable) { "" }.lowercase()
        if (h.endsWith("youtu.be") || h.contains("youtube")) return "YouTube"
        val p = h.removePrefix("www.").removePrefix("m.").split(".").filter { it.isNotEmpty() }
        val n = if (p.size >= 2) p[p.size - 2] else p.firstOrNull() ?: "Other"
        return n.replaceFirstChar { it.uppercase() }
    }

    private fun subFolder(t: Task): String {
        val root = safe(Prefs.folder).ifBlank { "Grab" }
        val extra = when (Prefs.organize) {
            1 -> siteOf(t.url)
            2 -> t.uploader?.let { safe(it) }?.ifBlank { null } ?: siteOf(t.url)
            else -> ""
        }
        return if (extra.isEmpty()) root else "$root/$extra"
    }

    private fun save(f: File, sub: String): String? {
        val ext = f.extension.lowercase()
        val mime = when (ext) {
            "srt" -> "application/x-subrip"; "vtt" -> "text/vtt"; "description" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"
        }
        val vol = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val (col, base) = when {
            ext in imgExt -> MediaStore.Images.Media.getContentUri(vol) to Environment.DIRECTORY_PICTURES
            ext in txtExt -> MediaStore.Downloads.getContentUri(vol) to Environment.DIRECTORY_DOWNLOADS
            mime.startsWith("audio") -> MediaStore.Audio.Media.getContentUri(vol) to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Video.Media.getContentUri(vol) to Environment.DIRECTORY_MOVIES
        }
        val name = if (ext == "description") f.nameWithoutExtension + ".txt" else f.name
        // مجلد اختاره المستخدم؟ لو فشل (الصلاحية اتسحبت مثلًا) بنرجع للحفظ العادي
        treeUri()?.let { tree -> try { saveToTree(tree, f, sub, mime, name)?.let { return it } } catch (_: Throwable) {} }
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$base/$sub")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val u = app.contentResolver.insert(col, cv) ?: return null
        app.contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o, 1 shl 20) } }
        cv.clear(); cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
        app.contentResolver.update(u, cv, null, null)
        return u.toString()
    }

    // ───── الحفظ في مجلد يختاره المستخدم (SAF) ─────
    private fun treeUri(): Uri? {
        val s = Prefs.treeUri; if (s.isEmpty()) return null
        val u = Uri.parse(s)
        return if (app.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }) u else null
    }

    private fun childId(tree: Uri, parentId: String, name: String): String? {
        val ch = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        app.contentResolver.query(ch, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == name) return c.getString(0)
        }
        return null
    }

    private fun ensureDir(tree: Uri, parts: List<String>): String {
        var id = DocumentsContract.getTreeDocumentId(tree)
        for (p in parts) {
            id = childId(tree, id, p) ?: run {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val d = DocumentsContract.createDocument(app.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, p)
                    ?: throw Exception("تعذّر إنشاء المجلد")
                DocumentsContract.getDocumentId(d)
            }
        }
        return id
    }

    // المجلد اللي اختاره المستخدم هو الجذر، فبنشيل اسم المجلد الافتراضي (أول جزء) ونكمّل بالمجلدات الفرعية
    private fun saveToTree(tree: Uri, f: File, sub: String, mime: String, name: String): String? {
        val dirId = ensureDir(tree, sub.split("/").drop(1).filter { it.isNotBlank() })
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, dirId)
        val u = DocumentsContract.createDocument(app.contentResolver, parent, mime, name) ?: return null
        app.contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o, 1 shl 20) } }
        return u.toString()
    }

    // ───── سجل الروابط ─────
    fun addHistory(url: String, title: String, thumb: String?) {
        val k = key(url)
        history.removeAll { key(it.url) == k }
        history.add(0, Hist(url, title, thumb))
        while (history.size > 20) history.removeAt(history.size - 1)
        saveHist()
    }
    fun clearHistory() { history.clear(); saveHist() }
    private fun loadHist() {
        try {
            val a = JSONArray(Prefs.sp.getString("hist", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); history.add(Hist(o.getString("u"), o.getString("t"), o.optString("th").ifEmpty { null })) }
        } catch (_: Throwable) {}
    }
    private fun saveHist() {
        try {
            val a = JSONArray(); history.toList().forEach { a.put(JSONObject().put("u", it.url).put("t", it.title).put("th", it.thumb ?: "")) }
            Prefs.sp.edit().putString("hist", a.toString()).apply()
        } catch (_: Throwable) {}
    }

    // ───── قائمة التشغيل: نجيب العناوين بس (من غير تحميل) عشان المستخدم يختار ─────
    suspend fun fetchPlaylist(url: String): PlInfo = withContext(Dispatchers.IO) {
        ensureEngine()
        var last: Throwable? = null
        for (i in order(url).take(2)) {
            try {
                val r = YoutubeDLRequest(url).apply {
                    addOption("--flat-playlist"); addOption("--dump-single-json"); addOption("--yes-playlist")
                    addOption("--no-warnings"); common(this, i, url)
                }
                val j = JSONObject(YoutubeDL.getInstance().execute(r).out)
                val arr = j.optJSONArray("entries")
                if (arr == null) {
                    return@withContext PlInfo(j.optString("title"), listOf(PlEntry(j.optString("webpage_url").ifEmpty { url },
                        j.optString("title").ifEmpty { url }, j.optString("thumbnail").ifEmpty { null }, j.optDouble("duration", 0.0).toInt())))
                }
                val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull { e ->
                    val id = e.optString("id")
                    var u = e.optString("url").ifEmpty { e.optString("webpage_url") }
                    if (!u.startsWith("http")) u = if (isYt(url) && id.isNotEmpty()) "https://www.youtube.com/watch?v=$id" else return@mapNotNull null
                    val th = e.optJSONArray("thumbnails")?.let { ta -> if (ta.length() > 0) ta.optJSONObject(ta.length() - 1)?.optString("url") else null }
                        ?: e.optString("thumbnail")
                    PlEntry(u, e.optString("title").ifEmpty { u }, th?.ifEmpty { null }, e.optDouble("duration", 0.0).toInt())
                }
                if (list.isEmpty()) throw Exception("القائمة فاضية أو مش متاحة")
                return@withContext PlInfo(j.optString("title"), list)
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) { last = e; delay(300) }
        }
        throw last ?: Exception("error")
    }

    private fun notifyDone(t: Task) {
        val pi = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(app, "done").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تم التحميل").setContentText(t.title).setContentIntent(pi).setAutoCancel(true).build()
        app.getSystemService(NotificationManager::class.java).notify(t.id.hashCode(), n)
    }

    private val taskFile get() = File(app.filesDir, "tasks.json")
    private fun loadTasks() {
        try {
            val a = JSONArray(taskFile.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = Task(o.getString("id"), o.getString("url"), o.getString("t"), o.optString("th").ifEmpty { null },
                    o.getBoolean("a"), o.getInt("h"), o.getBoolean("s"), o.getBoolean("p"))
                t.uploader = o.optString("u").ifEmpty { null }; t.clipStart = o.optString("cs"); t.clipEnd = o.optString("ce")
                t.thumbOnly = o.optBoolean("to"); t.expect = o.optLong("ex")
                val saved = try { St.valueOf(o.getString("st")) } catch (_: Throwable) { St.Failed }
                if (saved == St.Canceled || saved == St.Failed) {
                    t.st = saved; t.msg = o.optString("m")
                    t.progress = o.optDouble("pr", 0.0).toFloat().coerceIn(0f, 1f)
                    t.speed = o.optLong("sp"); t.done = o.optLong("dn"); t.total = o.optLong("tt"); t.eta = o.optLong("eta")
                    if (o.optBoolean("wn")) { t.waitNet = true; t.waitKind = o.optInt("wk"); t.waitSince = System.currentTimeMillis(); t.keep = true }
                } else {   // كان شغّال لما التطبيق اتقفل: هيكمّل تلقائيًا أول ما التطبيق يفتح والنت موجود
                    t.st = St.Failed; t.msg = "اتوقف لما التطبيق اتقفل — هكمّل تلقائيًا"
                    t.progress = o.optDouble("pr", 0.0).toFloat().coerceIn(0f, 1f)
                    t.speed = o.optLong("sp"); t.done = o.optLong("dn"); t.total = o.optLong("tt"); t.eta = o.optLong("eta")
                    t.waitNet = true; t.waitSince = System.currentTimeMillis()
                }
                tasks.add(t)
            }
        } catch (_: Throwable) {}
    }
    fun saveTasks() {
        try {
            val a = JSONArray()
            tasks.toList().forEach { t ->
                a.put(JSONObject().put("id", t.id).put("url", t.url).put("t", t.title).put("th", t.thumb ?: "")
                    .put("a", t.audio).put("h", t.height).put("s", t.subs).put("p", t.playlist)
                    .put("st", t.st.name).put("m", t.msg).put("wn", t.waitNet).put("wk", t.waitKind)
                    .put("pr", t.progress).put("sp", t.speed).put("dn", t.done).put("tt", t.total).put("eta", t.eta)
                    .put("u", t.uploader ?: "").put("cs", t.clipStart).put("ce", t.clipEnd)
                    .put("to", t.thumbOnly).put("ex", t.expect))
            }
            taskFile.writeText(a.toString())
        } catch (_: Throwable) {}
    }

    private val libFile get() = File(app.filesDir, "lib.json")
    private fun loadLib() {
        try {
            val a = JSONArray(libFile.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                library.add(Item(o.getString("t"), o.optString("th").ifEmpty { null }, o.getString("u"), o.getBoolean("a"), o.optString("s")))
            }
        } catch (_: Throwable) {}
    }
    fun saveLib() {
        val a = JSONArray()
        library.forEach { a.put(JSONObject().put("t", it.title).put("th", it.thumb ?: "").put("u", it.uri).put("a", it.audio).put("s", it.src)) }
        libFile.writeText(a.toString())
    }
    fun remove(i: Item) {
        try {
            val u = Uri.parse(i.uri)
            if (DocumentsContract.isDocumentUri(app, u)) DocumentsContract.deleteDocument(app.contentResolver, u)
            else app.contentResolver.delete(u, null, null)
        } catch (_: Exception) {}
        library.remove(i); saveLib()
    }
}

class DownloadService : Service() {
    companion object { @Volatile var looping = false; @Volatile var loopJob: Job? = null }
    override fun onBind(i: Intent?): IBinder? = null

    // أندرويد 15 بيحدّد خدمة dataSync بـ 6 ساعات. لازم نوقف في ثواني وإلا التطبيق بيتقفل بكراش.
    // بنسيب الجزء المحمّل، والتحميل بيكمّل لوحده أول ما تفتح التطبيق.
    override fun onTimeout(startId: Int, fgsType: Int) {
        Repo.tasks.filter { it.st == St.Running || it.st == St.Queued }
            .forEach { Repo.pauseForNet(it, "النظام وقّف الخدمة بعد ساعات طويلة — هكمّل لما تفتح التطبيق") }
        loopJob?.cancel(); looping = false
        stopSelf()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        // أزرار الإشعار
        when (i?.action) {
            "grab.CANCEL" -> { Repo.tasks.filter { it.st == St.Running || it.st == St.Queued }.forEach { Repo.cancel(it) }; return START_NOT_STICKY }
            "grab.PAUSE" -> { Repo.tasks.filter { it.st == St.Running || it.st == St.Queued }.forEach { Repo.pause(it) }; return START_NOT_STICKY }
        }
        val nm = getSystemService(NotificationManager::class.java)
        fun act(a: String, label: String, icon: Int): Notification.Action {
            val pi = PendingIntent.getService(this, a.hashCode(), Intent(this, DownloadService::class.java).setAction(a),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), label, pi).build()
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun build(p: Int, txt: String) = Notification.Builder(this, "dl")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Grab").setContentText(txt)
            .setContentIntent(open)
            .addAction(act("grab.PAUSE", "إيقاف مؤقت", android.R.drawable.ic_media_pause))
            .addAction(act("grab.CANCEL", "إلغاء", android.R.drawable.ic_menu_close_clear_cancel))
            .setOnlyAlertOnce(true).setOngoing(true).setProgress(100, p, p == 0).build()
        try { startForeground(1, build(0, "جاري التحميل…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
        catch (_: Throwable) { stopSelf(); return START_NOT_STICKY }
        if (!looping) {
            looping = true
            loopJob = Repo.scope.launch {
                while (true) {
                    val waiting = Repo.tick()   // استئناف تلقائي + مراقبة الواي فاي
                    val act = Repo.tasks.filter { it.st == St.Running || it.st == St.Queued }
                    if (act.isEmpty() && waiting == 0) break
                    if (act.isEmpty()) nm.notify(1, build(0, "$waiting تحميل في الانتظار…"))
                    else {
                        val one = act.singleOrNull()
                        val txt = if (one != null && one.st == St.Running && one.speed > 0L) briefOf(one) else "${act.size} تحميل"
                        nm.notify(1, build((act.map { it.progress }.average() * 100).toInt(), txt))
                    }
                    delay(1000)
                }
                looping = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
}
