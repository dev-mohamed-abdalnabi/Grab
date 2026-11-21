@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.grab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ═══════════════════════════ الثيم ═══════════════════════════
// ألوان ديناميكية من خلفية الموبايل (أندرويد 12+)، وألوان احتياطية للأقدم

private val LightFallback = lightColorScheme(
    primary = Color(0xFF0B57D0), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD), onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF565F71), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF131C2B),
    tertiary = Color(0xFF146C2E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC4EED0), onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val DarkFallback = darkColorScheme(
    primary = Color(0xFFA8C7FA), onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0), onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBDC7DC), onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF6DD58C), onTertiary = Color(0xFF003915),
    tertiaryContainer = Color(0xFF0F5223), onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF111318), onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E), onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

// خط Tajawal (بيتحمّل في الـBuild من جوجل فونتس ويتحط في res/font)
private val AppFont = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold)
)
private fun TextStyle.f(w: FontWeight? = null) = copy(fontFamily = AppFont, fontWeight = w ?: fontWeight)

private val T0 = Typography()
private val AppType = Typography(
    displayLarge = T0.displayLarge.f(FontWeight.ExtraBold),
    displayMedium = T0.displayMedium.f(FontWeight.ExtraBold),
    displaySmall = T0.displaySmall.copy(fontSize = 36.sp, lineHeight = 52.sp).f(FontWeight.ExtraBold),
    headlineLarge = T0.headlineLarge.f(FontWeight.ExtraBold),
    headlineMedium = T0.headlineMedium.f(FontWeight.ExtraBold),
    headlineSmall = T0.headlineSmall.f(FontWeight.Bold),
    titleLarge = T0.titleLarge.f(FontWeight.Bold),
    titleMedium = T0.titleMedium.f(FontWeight.Bold),
    titleSmall = T0.titleSmall.f(FontWeight.Bold),
    bodyLarge = T0.bodyLarge.f(FontWeight.Normal),
    bodyMedium = T0.bodyMedium.f(FontWeight.Normal),
    bodySmall = T0.bodySmall.f(FontWeight.Normal),
    labelLarge = T0.labelLarge.f(FontWeight.Bold),
    labelMedium = T0.labelMedium.f(FontWeight.Medium),
    labelSmall = T0.labelSmall.f(FontWeight.Medium)
)

// ألوان رئيسية بديلة (لون التمييز): أزرق، أخضر، بنفسجي، برتقالي، وردي
private val AccentDots = listOf(Color(0xFF0B57D0), Color(0xFF1B6C3A), Color(0xFF6750A4), Color(0xFF9A4600), Color(0xFFA8305F))

private fun accentScheme(a: Int, dark: Boolean): ColorScheme {
    val base = if (dark) DarkFallback else LightFallback
    return when (a) {
        2 -> if (dark) base.copy(primary = Color(0xFF8FD8A0), onPrimary = Color(0xFF00391A), primaryContainer = Color(0xFF005226), onPrimaryContainer = Color(0xFFB5F2C4))
             else base.copy(primary = Color(0xFF1B6C3A), primaryContainer = Color(0xFFB5F2C4), onPrimaryContainer = Color(0xFF00210D))
        3 -> if (dark) base.copy(primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72), primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF))
             else base.copy(primary = Color(0xFF6750A4), primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D))
        4 -> if (dark) base.copy(primary = Color(0xFFFFB68A), onPrimary = Color(0xFF512300), primaryContainer = Color(0xFF743400), onPrimaryContainer = Color(0xFFFFDBC8))
             else base.copy(primary = Color(0xFF9A4600), primaryContainer = Color(0xFFFFDBC8), onPrimaryContainer = Color(0xFF331200))
        5 -> if (dark) base.copy(primary = Color(0xFFFFB0CB), onPrimary = Color(0xFF640032), primaryContainer = Color(0xFF86144A), onPrimaryContainer = Color(0xFFFFD9E3))
             else base.copy(primary = Color(0xFFA8305F), primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E001D))
        else -> base
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val cs = when {
        Prefs.accent == 0 && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> accentScheme(Prefs.accent, dark)
    }
    MaterialTheme(colorScheme = cs, typography = AppType, content = content)
}

// ═══════════════════════════ الأيقونات ═══════════════════════════
// المجموعة كلها في Icons.kt (object Ico)

private val IcDownload get() = Ico.Download
private val IcLink get() = Ico.Link
private val IcWifi get() = Ico.Wifi
private val IcSubs get() = Ico.Subs
private val IcMusic get() = Ico.Music
private val IcLayers get() = Ico.Layers
private val IcPause get() = Ico.Pause
private val IcEngine get() = Ico.Terminal

// ═══════════════════════════ أشكال وحركة ═══════════════════════════

// شكل "الكوكي" المتموّج: دايرة حوافها فصوص ناعمة
private class CookieShape(private val lobes: Int = 9, private val amp: Float = 0.06f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f; val cy = size.height / 2f
        val p = Path(); val steps = 180
        for (i in 0..steps) {
            val a = (2.0 * PI * i / steps).toFloat()
            val rad = r * (1f - amp + amp * cos(lobes * a))
            val x = cx + rad * cos(a); val y = cy + rad * sin(a)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

@Composable
private fun CookieIcon(icon: ImageVector, size: Dp, container: Color, tint: Color, modifier: Modifier = Modifier, spin: Boolean = true) {
    val shape = remember { CookieShape() }
    val rot by rememberInfiniteTransition(label = "cookie").animateFloat(0f, 360f,
        infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "rot")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (spin) rot else 0f }.clip(shape).background(container))
        Icon(icon, null, Modifier.size(size * 0.44f), tint = tint)
    }
}

// شريط تقدم متموّج (زي Material Expressive). p = null يعني "جاري التجهيز"
@Composable
private fun WavyBar(p: Float?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val active = cs.primary; val track = cs.secondaryContainer
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(0f, (2.0 * PI).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ph")
    val slide by inf.animateFloat(-0.35f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "sl")
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val sw = 4.dp.toPx(); val amp = 3.dp.toPx(); val wl = 24.dp.toPx()
        val cy = size.height / 2f; val w = size.width; val gap = sw * 1.3f
        val from: Float; val to: Float
        if (p != null) { from = 0f; to = w * p.coerceIn(0f, 1f) }
        else { from = (slide * w).coerceAtLeast(0f); to = (slide * w + 0.35f * w).coerceAtMost(w) }
        scale(if (rtl) -1f else 1f, 1f, pivot = center) {
            if (to + gap < w) drawLine(track, Offset(to + gap, cy), Offset(w, cy), sw, StrokeCap.Round)
            if (p == null && from - gap > 0f) drawLine(track, Offset(0f, cy), Offset(from - gap, cy), sw, StrokeCap.Round)
            if (to - from > 1f) {
                val path = Path()
                var x = from
                path.moveTo(x, cy + amp * sin(x / wl * 2f * PI.toFloat() - phase))
                while (x < to) {
                    x = min(x + 3f, to)
                    path.lineTo(x, cy + amp * sin(x / wl * 2f * PI.toFloat() - phase))
                }
                drawPath(path, active, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ═══════════════════════════ صور ═══════════════════════════

object Img { val cache = android.util.LruCache<String, Bitmap>(40) }

@Composable
fun Thumb(url: String?, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(url?.let { Img.cache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it, null, o) }?.also { Img.cache.put(url, it) }
            } catch (e: Throwable) { null }
        }
    }
    Box(modifier) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}

fun fmt(s: Int): String {
    if (s <= 0) return ""
    val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

fun qTag(h: Int) = when {
    h >= 4320 -> "8K"; h >= 2160 -> "4K"; h >= 1440 -> "2K"; h >= 1080 -> "Full HD"; h >= 720 -> "HD"
    h >= 480 -> "متوسطة"; h >= 360 -> "خفيفة"; else -> "ضعيفة"
}


// حجم بالميجا/الجيجا. «≈» لو الرقم تقدير مش حجم دقيق من الموقع
fun sz(bytes: Long, approx: Boolean): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1048576.0
    val t = when {
        mb >= 1024 -> String.format(java.util.Locale.US, "%.2f GB", mb / 1024)
        mb >= 100 -> String.format(java.util.Locale.US, "%.0f MB", mb)
        else -> String.format(java.util.Locale.US, "%.1f MB", mb)
    }
    return if (approx) "≈ $t" else t
}

// ═══════════════════════════ عناصر مشتركة ═══════════════════════════

@Composable
private fun fieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme.surfaceContainerHigh
    return TextFieldDefaults.colors(
        focusedContainerColor = c, unfocusedContainerColor = c, disabledContainerColor = c,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent
    )
}

@Composable
private fun Header(title: String, sub: String? = null) {
    Column(Modifier.statusBarsPadding().padding(top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall)
        if (sub != null) Text(sub, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 28.dp, bottom = 6.dp))

@Composable
private fun Footnote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

@Composable
private fun SettingRow(icon: ImageVector?, title: String, sub: String? = null, subColor: Color = Color.Unspecified,
                       onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = cs.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = if (subColor == Color.Unspecified) cs.onSurfaceVariant else subColor)
        }
        trailing?.invoke()
    }
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) =
    Switch(checked, onChange, thumbContent = if (checked) ({ Icon(Ico.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) else null)

// صف خيارات (شرائح) بيتحرّك أفقيًا
@Composable
private fun Choices(opts: List<String>, sel: Int, on: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEachIndexed { i, o ->
            FilterChip(selected = sel == i, onClick = { on(i) }, label = { Text(o) },
                leadingIcon = if (sel == i) ({ Icon(Ico.Check, null, Modifier.size(18.dp)) }) else null)
        }
    }
}

// مبدّل بحبّة بتنزلق بين الخيارات
@Composable
private fun Switcher(opts: List<String>, sel: Int, modifier: Modifier = Modifier, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp)) {
        val w = maxWidth / opts.size
        val x by animateDpAsState(w * sel.coerceAtLeast(0), spring(0.8f, 420f), label = "sw")
        if (sel >= 0) Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(cs.primary))
        Row(Modifier.fillMaxSize()) {
            opts.forEachIndexed { i, o ->
                val c by animateColorAsState(if (i == sel) cs.onPrimary else cs.onSurface, label = "swc")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { on(i) },
                    contentAlignment = Alignment.Center) {
                    Text(o, color = c, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

// زر كبير: بيتحوّل شكله ويصغر لما تضغط عليه
@Composable
private fun BigButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
                      loading: Boolean = false, tonal: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val r by animateDpAsState(if (pressed) 18.dp else 32.dp, spring(0.55f, 600f), label = "r")
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, spring(0.55f, 600f), label = "s")
    val bg = if (tonal) cs.secondaryContainer else cs.primary
    val fg = if (tonal) cs.onSecondaryContainer else cs.onPrimary
    Row(modifier.fillMaxWidth().height(64.dp)
        .graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.38f }
        .clip(RoundedCornerShape(r)).background(bg)
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = fg)
        else if (icon != null) Icon(icon, null, Modifier.size(24.dp), tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CookieIcon(icon, 128.dp, cs.primaryContainer, cs.onPrimaryContainer)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PageList(bottom: Dp, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) =
    LazyColumn(Modifier.fillMaxSize().imePadding(), state = state,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom), content = content)

// ═══════════════════════════ الهيكل + شريط التنقل العايم ═══════════════════════════

private class NavTab(val label: String, val on: ImageVector, val off: ImageVector)

private val LocalSnack = staticCompositionLocalOf<SnackbarHostState> { error("no snackbar host") }

@Composable
fun App() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val inc by Incoming.v.collectAsState()
    LaunchedEffect(inc) { if (inc != null) tab = 0 }
    BackHandler(enabled = tab != 0) { tab = 0 }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottom = if (imeOpen) 16.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 120.dp

    // لما تحميل يخلص: رسالة "تم التحميل" مع زر فتح، ونقطة على تبويب المكتبة لو مش فاتحها
    var unseen by remember { mutableIntStateOf(0) }
    var lastSize by remember { mutableIntStateOf(Repo.library.size) }
    val libSize = Repo.library.size
    LaunchedEffect(libSize) {
        if (libSize > lastSize) {
            val it = Repo.library.firstOrNull()
            if (tab != 1) unseen += libSize - lastSize
            lastSize = libSize
            if (it != null) {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar("تم التحميل: ${it.title.take(40)}", "فتح", duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) openItem(ctx, it)
            }
        } else lastSize = libSize
    }
    LaunchedEffect(tab) { if (tab == 1) unseen = 0 }

    CompositionLocalProvider(LocalSnack provides snack) {
        Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
            Box(Modifier.fillMaxSize()) {
                Crossfade(tab, Modifier.fillMaxSize(), label = "tab") {
                    when (it) { 0 -> HomeScreen(bottom); 1 -> LibraryScreen(bottom); else -> SettingsScreen(bottom) }
                }
                AnimatedVisibility(!imeOpen, Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    Box(Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, cs.surface)))
                        .navigationBarsPadding().padding(top = 36.dp, bottom = 14.dp),
                        contentAlignment = Alignment.Center) { FloatingNav(tab, unseen) { tab = it } }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (imeOpen) 12.dp else 100.dp))
            }
        }
    }
}

@Composable
private fun FloatingNav(sel: Int, badge: Int, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hap = LocalHapticFeedback.current
    val items = remember {
        listOf(
            NavTab("الرئيسية", Ico.Home, Ico.Home),
            NavTab("المكتبة", Ico.Library, Ico.Library),
            NavTab("الإعدادات", Ico.Settings, Ico.Settings)
        )
    }
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, shadowElevation = 10.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, t ->
                val a = i == sel
                val bg by animateColorAsState(if (a) cs.primaryContainer else Color.Transparent, label = "nb")
                Row(Modifier.height(54.dp).clip(CircleShape).background(bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        hap.performHapticFeedback(HapticFeedbackType.TextHandleMove); on(i)
                    }
                    .padding(horizontal = 20.dp).animateContentSize(spring(0.8f, 400f)),
                    verticalAlignment = Alignment.CenterVertically) {
                    val ic: @Composable () -> Unit = {
                        Icon(if (a) t.on else t.off, t.label, tint = if (a) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    }
                    if (i == 1 && badge > 0 && !a) BadgedBox(badge = { Badge { Text("$badge") } }) { ic() } else ic()
                    AnimatedVisibility(a, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(t.label, Modifier.padding(start = 10.dp), color = cs.onPrimaryContainer,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════ الرئيسية ═══════════════════════════

@Composable
private fun HomeHero(compact: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(-5f, 5f,
        infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    if (compact) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Grab", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                Text("التحميلات", style = MaterialTheme.typography.headlineMedium)
            }
            CookieIcon(IcDownload, 60.dp, cs.primary, cs.onPrimary)
        }
    } else {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Grab", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                Text("الصق الرابط،\nوخلّيها علينا", style = MaterialTheme.typography.displaySmall)
            }
            CookieIcon(IcDownload, 104.dp, cs.primary, cs.onPrimary, Modifier.offset(y = bob.dp))
        }
    }
}

@Composable
private fun HowTo(top: Dp = 36.dp) {
    val cs = MaterialTheme.colorScheme
    val steps = listOf(
        "افتح الفيديو في يوتيوب أو إنستجرام أو فيسبوك أو تيك توك",
        "اضغط مشاركة واختار Grab، أو انسخ الرابط والصقه فوق",
        "اختار الجودة أو الصوت واضغط تحميل"
    )
    Column(Modifier.fillMaxWidth().padding(top = top), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("إزاي تستخدمه؟", style = MaterialTheme.typography.titleLarge)
        steps.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = cs.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
                }
                Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun HomeScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val hap = LocalHapticFeedback.current
    val clip = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var url by remember { mutableStateOf(Prefs.draft) }
    LaunchedEffect(url) { Prefs.draft = url }
    var info by remember { mutableStateOf<Info?>(null) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(0) }
    var subs by remember { mutableStateOf(Prefs.subs) }
    var playlist by remember { mutableStateOf(false) }
    var dup by remember { mutableStateOf<Dup?>(null) }
    var pl by remember { mutableStateOf<PlInfo?>(null) }
    var plSel by remember { mutableStateOf(setOf<Int>()) }
    var plFrom by remember { mutableStateOf("") }
    var plTo by remember { mutableStateOf("") }
    var skipDup by remember { mutableStateOf(false) }
    var clipOn by remember { mutableStateOf(false) }
    var clipFrom by remember { mutableStateOf("") }
    var clipTo by remember { mutableStateOf("") }
    val clipLink by Incoming.clip.collectAsState()
    // شرح الاستخدام بيظهر أول مرة تفتح فيها التطبيق بس (وتقدر تشوفه تاني من الإعدادات)
    val showHow = remember { Prefs.firstRun }
    LaunchedEffect(Unit) { if (Prefs.firstRun) { Prefs.firstRun = false; Prefs.save() } }
    val inc by Incoming.v.collectAsState()
    val ready by Repo.ready
    val engineSt by Repo.status

    BackHandler(enabled = info != null || pl != null) { if (pl != null) pl = null else info = null }

    fun newTask(title: String, thumb: String?, pl: Boolean) =
        Task(System.nanoTime().toString(), url.trim(), title, thumb, kind == 1, h, subs, pl)

    fun added() {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            launch { listState.animateScrollToItem(0) }
            snack.showSnackbar("بدأ التحميل", duration = SnackbarDuration.Short)
        }
    }

    fun direct() {
        val u = url.trim(); if (u.isEmpty()) return
        Repo.enqueue(ctx, newTask(u, null, playlist)); url = ""; err = ""; info = null; added()
    }

    fun go() {
        val u = url.trim()
        if (u.isEmpty()) return
        Incoming.clip.value?.let { if (u.contains(it)) { Prefs.lastClip = it; Incoming.clip.value = null } }
        val all = urlsOf(u)
        if (all.size > 1) {   // دفعة روابط: كلها تتضاف للتحميل مباشرة (بنتخطى اللي اتحمّل قبل كده)
            val fresh = all.filter { Repo.dupOf(it, kind == 1) == null }
            fresh.forEachIndexed { n, l -> Repo.enqueue(ctx, Task("${System.nanoTime()}_$n", l, l, null, kind == 1, 0, Prefs.subs, false)) }
            url = ""; err = ""; info = null
            val skipped = all.size - fresh.size
            val m = when {
                fresh.isEmpty() -> "كل الروابط اتحمّلت قبل كده"
                skipped == 0 -> "اتضاف ${all.size} روابط للتحميل"
                else -> "اتضاف ${fresh.size}، واتخطّى $skipped اتحمّلوا قبل كده"
            }
            scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) }
            return
        }
        if (!playlist && !skipDup) { Repo.dupOf(u, kind == 1)?.let { dup = it; return } }
        skipDup = false
        focus.clearFocus()
        err = ""; info = null; pl = null
        if (playlist) {   // نعرض عناوين القائمة عشان المستخدم يختار منها
            loading = true
            scope.launch {
                try { val p = Repo.fetchPlaylist(u); pl = p; plSel = p.entries.indices.toSet(); plFrom = ""; plTo = "" }
                catch (e: Throwable) { err = Repo.clean(e) }
                loading = false
            }
            return
        }
        loading = true
        scope.launch {
            try { val i = Repo.fetch(u); info = i; h = 0; Repo.addHistory(u, i.title ?: u, i.thumbnail) } catch (e: Throwable) { err = Repo.clean(e) }
            loading = false
        }
    }
    LaunchedEffect(inc) { inc?.let { url = it; Incoming.v.value = null; go() } }

    val compact = Repo.tasks.isNotEmpty() || info != null || pl != null || loading

    // توهّج لوني ناعم ثابت في أعلى الشاشة
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.radialGradient(listOf(cs.primaryContainer.copy(alpha = 0.85f), Color.Transparent),
            center = Offset(size.width * (if (rtl) 0.12f else 0.88f), size.height * 0.04f), radius = size.width * 0.95f))
    }) {
        PageList(bottom, listState) {
            item { Box(Modifier.animateContentSize()) { HomeHero(compact) } }
            clipLink?.let { cl ->
                if (url.isBlank() && info == null) item {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(28.dp))
                        .background(cs.secondaryContainer).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("رابط منسوخ", style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer.copy(alpha = 0.7f))
                            Text(cl, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSecondaryContainer)
                        }
                        TextButton({ url = cl; go() }) { Text("جلب") }
                        IconButton({ Prefs.lastClip = cl; Incoming.clip.value = null }) { Icon(Ico.Close, "تجاهل") }
                    }
                }
            }
            item {
                TextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    shape = RoundedCornerShape(34.dp), colors = fieldColors(),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = { Text("رابط الفيديو") },
                    leadingIcon = { Icon(IcLink, null, tint = cs.onSurfaceVariant) },
                    trailingIcon = {
                        if (url.isEmpty()) Box(Modifier.padding(end = 8.dp).clip(CircleShape).background(cs.primaryContainer)
                            .clickable { clip.getText()?.text?.let { txt -> url = urlsOf(txt).joinToString(" ").ifEmpty { txt.trim() }; go() } }
                            .padding(horizontal = 16.dp, vertical = 9.dp)) {
                            Text("لصق وجلب", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                        } else IconButton({ url = ""; err = ""; info = null }) { Icon(Ico.Close, "مسح") }
                    },
                    supportingText = if (url.isNotBlank() && !url.trim().startsWith("http", true))
                        ({ Text("ده مش شكل رابط — هحاول معاه برضه") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() })
                )
            }
            item {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = playlist, onClick = { playlist = !playlist }, label = { Text("قائمة تشغيل كاملة") },
                        leadingIcon = if (playlist) ({ Icon(Ico.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
            item {
                BigButton(if (playlist) "تحميل القائمة" else if (loading) "جاري الجلب…" else "جلب",
                    Modifier.padding(top = 16.dp), icon = IcDownload,
                    enabled = url.isNotBlank() && !loading, loading = loading) {
                    hap.performHapticFeedback(HapticFeedbackType.LongPress); go()
                }
            }
            if (!ready) item {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (engineSt.isNotEmpty()) engineSt else "جاري تجهيز المحرك…",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (err.isNotEmpty()) item {
                Column(Modifier.padding(top = 16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Ico.Warning, null, Modifier.size(22.dp), tint = cs.error)
                        Text(err, style = MaterialTheme.typography.bodyMedium, color = cs.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ go() }) { Text("حاول تاني") }
                        TextButton({ direct() }) { Text("حمّل مباشرة") }
                    }
                }
            }

            info?.let { i ->
                item {
                    val dur = fmt(i.duration)
                    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box {
                            Thumb(i.thumbnail, Modifier.fillMaxWidth().aspectRatio(16 / 9f)
                                .clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerHigh))
                            if (dur.isNotEmpty()) Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                                Text(dur, color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(i.title ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!i.uploader.isNullOrEmpty()) Text(i.uploader ?: "", style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switcher(listOf("فيديو", "صوت"), kind) { kind = it }
                        if (kind == 0) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QChip("الأفضل", "تلقائي", i.qs.firstOrNull()?.let { sz(it.bytes, it.approx) } ?: "", h == 0) { h = 0 }
                                i.qs.forEach { q -> QChip("${q.h}p", qTag(q.h), sz(q.bytes, q.approx), h == q.h) { h = q.h } }
                            }
                            if (i.qs.any { it.bytes > 0 }) Footnote(
                                if (clipOn) "الأحجام للفيديو كامل (فيديو + صوت)، والجزء المحدد هيبقى أصغر."
                                else "الحجم الحقيقي للفيديو + الصوت لكل جودة. «≈» يعني تقدير.")
                            SettingRow(IcSubs, "تضمين الترجمة", onClick = { subs = !subs }) { Toggle(subs) { subs = it } }
                        } else {
                            val a = i.audioEst(Prefs.audioFmt)
                            SettingRow(IcMusic, "الصيغة: ${Prefs.audioFmt.uppercase()}",
                                (if (a.first > 0) "الحجم ${sz(a.first, a.second)} • " else "") + "الصيغة تتغير من الإعدادات")
                        }
                        SettingRow(Ico.Scissors, "تحميل جزء فقط", "حدّد بداية ونهاية المقطع", onClick = { clipOn = !clipOn }) { Toggle(clipOn) { clipOn = it } }
                        if (clipOn) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(clipFrom, { clipFrom = it.filter { c -> c.isDigit() || c == ':' } }, Modifier.weight(1f),
                                label = { Text("من (0:30)") }, singleLine = true)
                            OutlinedTextField(clipTo, { clipTo = it.filter { c -> c.isDigit() || c == ':' } }, Modifier.weight(1f),
                                label = { Text("إلى (1:45)") }, singleLine = true)
                        }
                        BigButton("تحميل", icon = IcDownload) {
                            hap.performHapticFeedback(HapticFeedbackType.LongPress)
                            Repo.enqueue(ctx, newTask(i.title ?: url, i.thumbnail, false).also { tk ->
                                tk.uploader = i.uploader
                                if (clipOn && (clipFrom.isNotBlank() || clipTo.isNotBlank())) { tk.clipStart = clipFrom; tk.clipEnd = clipTo }
                            })
                            info = null; url = ""; clipOn = false; clipFrom = ""; clipTo = ""; added()
                        }
                    }
                }
            }

            pl?.let { p ->
                item {
                    Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(p.title.ifEmpty { "قائمة تشغيل" }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${plSel.size} من ${p.entries.size} محدد", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(plSel.size == p.entries.size, { plSel = if (plSel.size == p.entries.size) emptySet() else p.entries.indices.toSet() },
                                { Text("تحديد الكل") })
                            OutlinedTextField(plFrom, { plFrom = it.filter { c -> c.isDigit() }.take(4) }, Modifier.weight(1f),
                                label = { Text("من رقم") }, singleLine = true)
                            OutlinedTextField(plTo, { plTo = it.filter { c -> c.isDigit() }.take(4) }, Modifier.weight(1f),
                                label = { Text("إلى رقم") }, singleLine = true)
                            FilledTonalButton({
                                val a = ((plFrom.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                                val b = (plTo.toIntOrNull() ?: p.entries.size).coerceAtMost(p.entries.size)
                                plSel = (a until b).toSet()
                            }) { Text("حدّد") }
                        }
                        Switcher(listOf("فيديو", "صوت"), kind) { kind = it }
                        BigButton("حمّل المحدد (${plSel.size})", icon = IcDownload, enabled = plSel.isNotEmpty()) {
                            hap.performHapticFeedback(HapticFeedbackType.LongPress)
                            val chosen = p.entries.filterIndexed { n, _ -> n in plSel }
                            val fresh = chosen.filter { Repo.dupOf(it.url, kind == 1) == null }
                            fresh.forEachIndexed { n, e ->
                                Repo.enqueue(ctx, Task("${System.nanoTime()}_$n", e.url, e.title, e.thumb, kind == 1, 0, Prefs.subs, false))
                            }
                            val skipped = chosen.size - fresh.size
                            pl = null; url = ""; err = ""
                            scope.launch {
                                snack.currentSnackbarData?.dismiss()
                                snack.showSnackbar(if (skipped == 0) "اتضاف ${fresh.size} للتحميل" else "اتضاف ${fresh.size}، واتخطّى $skipped اتحمّلوا قبل كده")
                            }
                        }
                    }
                }
                itemsIndexed(p.entries) { n, e ->
                    val on = n in plSel
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .clickable { plSel = if (on) plSel - n else plSel + n }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Checkbox(on, null)
                        Thumb(e.thumb, Modifier.size(width = 84.dp, height = 52.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh))
                        Column(Modifier.weight(1f)) {
                            Text("${n + 1}. ${e.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            if (e.dur > 0) Text(fmt(e.dur), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            // آخر الروابط اللي جلبتها (لما مفيش حاجة مكتوبة)
            if (info == null && pl == null && url.isBlank() && Repo.history.isNotEmpty()) {
                item {
                    Row(Modifier.padding(top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("آخر الروابط", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton({ Repo.clearHistory() }) { Text("مسح", color = cs.error) }
                    }
                }
                items(Repo.history.toList(), key = { "h" + it.url }) { h0 ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { url = h0.url; go() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Thumb(h0.thumb, Modifier.size(width = 84.dp, height = 52.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh))
                        Text(h0.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            if (Repo.tasks.isNotEmpty()) {
                item {
                    Row(Modifier.padding(top = 32.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("قيد التنفيذ", style = MaterialTheme.typography.titleLarge)
                        Box(Modifier.clip(CircleShape).background(cs.primaryContainer).padding(horizontal = 10.dp, vertical = 2.dp)) {
                            Text("${Repo.tasks.size}", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                        }
                        if (Repo.tasks.any { it.st == St.Failed || it.st == St.Canceled }) {
                            Spacer(Modifier.weight(1f))
                            TextButton({ Repo.retryAll(ctx) }) { Text("إعادة الكل") }
                            TextButton({ Repo.clearFailed() }) { Text("مسح الفاشل", color = cs.error) }
                        }
                    }
                }
                items(Repo.tasks, key = { it.id }) { TaskRow(it, Modifier.animateItem()) }
            } else if (showHow && info == null && err.isEmpty() && !loading) item { HowTo() }
        }
    }

    dup?.let { d ->
        AlertDialog(
            onDismissRequest = { dup = null },
            title = { Text(if (d.item != null) "اتحمّل قبل كده" else "ده بيتحمّل دلوقتي") },
            text = { Text("\"${d.title}\"", maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                Row {
                    if (d.item != null) TextButton({ openItem(ctx, d.item); dup = null }) { Text("فتح الملف") }
                    TextButton({ dup = null; skipDup = true; go() }) { Text("حمّل تاني") }
                }
            },
            dismissButton = { TextButton({ dup = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun QChip(top: String, sub: String, size: String, sel: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (sel) cs.primary else cs.surfaceContainerHigh, label = "qc")
    val fg = if (sel) cs.onPrimary else cs.onSurface
    Column(Modifier.clip(RoundedCornerShape(24.dp)).background(bg)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(top, color = fg, style = MaterialTheme.typography.titleMedium)
        Text(sub, color = fg.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        if (size.isNotEmpty()) Text(size, Modifier.padding(top = 4.dp), color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun TaskRow(t: Task, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val p by animateFloatAsState(t.progress, label = "prog")
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Thumb(t.thumb, Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            when (t.st) {
                St.Running -> WavyBar(if (t.progress > 0f && t.progress < 0.995f) p else null)
                St.Queued -> WavyBar(0f)
                else -> {}
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (t.st == St.Done) Icon(Ico.CheckCircle, null, Modifier.size(18.dp), tint = cs.tertiary)
                Text(when (t.st) {
                    St.Queued -> "في الانتظار"
                    St.Running -> if (t.msg.isNotEmpty() && (t.progress == 0f || t.progress >= 0.995f)) t.msg else "${(t.progress * 100).toInt()}%" + if (t.eta > 0) " • باقي ${t.eta}ث" else ""
                    St.Failed -> t.msg
                    St.Canceled -> t.msg.ifEmpty { "أُلغي" }
                    St.Done -> "تم التحميل"
                }, style = MaterialTheme.typography.labelLarge,
                    color = when (t.st) { St.Failed -> cs.error; St.Done -> cs.tertiary; else -> cs.onSurfaceVariant },
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when (t.st) {
            St.Running, St.Queued -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (t.st == St.Queued) IconButton({ Repo.startNow(ctx, t) }) { Icon(Ico.Play, "ابدأ الآن") }
                IconButton({ Repo.pause(t) }) { Icon(IcPause, "إيقاف مؤقت") }
                FilledTonalIconButton({ Repo.cancel(t) }) { Icon(Ico.Close, "إلغاء") }
            }
            St.Failed, St.Canceled -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FilledTonalIconButton({ if (t.waitNet) Repo.startNow(ctx, t) else Repo.enqueue(ctx, t) }) { Icon(Ico.Refresh, "إعادة") }
                IconButton({ Repo.drop(t) }) { Icon(Ico.Delete, "حذف") }
            }
            else -> {}
        }
    }
}

// ═══════════════════════════ المكتبة (شبكة صور) ═══════════════════════════

// بيفتح المشغّل. للصوت: queue = قائمة التشغيل كلها وبتبدأ من الملف المختار
private fun openItem(c: Context, i: Item, queue: List<Item> = listOf(i)) = try {
    val q = if (queue.any { it.uri == i.uri }) queue else listOf(i)
    c.startActivity(Intent(c, PlayerActivity::class.java)
        .putStringArrayListExtra("u", ArrayList(q.map { it.uri }))
        .putStringArrayListExtra("t", ArrayList(q.map { it.title }))
        .putExtra("i", q.indexOfFirst { it.uri == i.uri }).putExtra("a", i.audio))
} catch (_: Throwable) {}

private fun shareItems(c: Context, l: List<Item>) = try {
    if (l.size == 1) shareItem(c, l[0]) else {
        val s = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(l.map { Uri.parse(it.uri) }))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(s, null))
    }
} catch (_: Throwable) {}

private fun shareItem(c: Context, i: Item) = try {
    val s = Intent(Intent.ACTION_SEND).setType(if (i.audio) "audio/*" else "video/*")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(i.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(s, null))
} catch (_: Throwable) {}

@Composable
fun LibraryScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var f by remember { mutableIntStateOf(0) }
    var del by remember { mutableStateOf<Item?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }       // التحديد المتعدد (بالضغط المطوّل)
    var delMany by remember { mutableStateOf(false) }
    val selecting = sel.isNotEmpty()
    BackHandler(enabled = selecting) { sel = emptySet() }
    LaunchedEffect(Unit) { Repo.scope.launch { Repo.pruneLibrary() } }
    val list = Repo.library.filter { (f == 0 || (f == 1 && !it.audio) || (f == 2 && it.audio)) && it.title.contains(q, true) }
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(span = full) { Header("المكتبة", if (Repo.library.isEmpty()) null else "${Repo.library.size} ملف") }
        if (selecting) item(span = full) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.secondaryContainer)
                .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${sel.size} محدد", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer)
                TextButton({ sel = list.map { it.uri }.toSet() }) { Text("الكل") }
                IconButton({ shareItems(ctx, Repo.library.filter { it.uri in sel }) }) { Icon(Ico.Share, "مشاركة") }
                IconButton({ delMany = true }) { Icon(Ico.Delete, "حذف") }
                IconButton({ sel = emptySet() }) { Icon(Ico.Close, "إلغاء التحديد") }
            }
        }
        item(span = full) {
            TextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                shape = RoundedCornerShape(30.dp), colors = fieldColors(),
                placeholder = { Text("ابحث في تنزيلاتك") },
                leadingIcon = { Icon(Ico.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Ico.Close, "مسح") } }
            )
        }
        item(span = full) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("الكل", "فيديو", "صوت").forEachIndexed { i, l ->
                    FilterChip(selected = f == i, onClick = { f = i }, label = { Text(l) },
                        leadingIcon = if (f == i) ({ Icon(Ico.Check, null, Modifier.size(18.dp)) }) else null)
                }
            }
        }
        val audios = list.filter { it.audio }
        if (audios.isNotEmpty() && f != 1 && !selecting) item(span = full) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton({ openItem(ctx, audios[0], audios) }) {
                    Icon(Ico.Play, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("تشغيل الصوت (${audios.size})")
                }
                OutlinedButton({ val sh = audios.shuffled(); openItem(ctx, sh[0], sh) }) { Text("عشوائي") }
            }
        }
        if (list.isEmpty()) item(span = full) {
            EmptyState(Ico.Play, if (Repo.library.isEmpty()) "المكتبة فاضية" else "مفيش نتائج",
                if (Repo.library.isEmpty()) "اللي تحمّله هيظهر هنا." else "جرّب كلمة تانية أو فلتر تاني.")
        }
        items(list, key = { it.uri }) { i ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(28.dp))
                    .background(cs.surfaceContainerHigh).combinedClickable(
                        onClick = {
                            if (selecting) sel = if (i.uri in sel) sel - i.uri else sel + i.uri
                            else openItem(ctx, i, if (i.audio) list.filter { it.audio } else listOf(i))
                        },
                        onLongClick = { sel = if (i.uri in sel) sel - i.uri else sel + i.uri }), contentAlignment = Alignment.Center) {
                    if (i.thumb != null) Thumb(i.thumb, Modifier.fillMaxSize())
                    else CookieIcon(if (i.audio) IcMusic else Ico.Play, 72.dp, cs.primaryContainer, cs.onPrimaryContainer, spin = false)
                    Box(Modifier.align(Alignment.TopStart).padding(10.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)).padding(7.dp)) {
                        Icon(if (i.audio) IcMusic else Ico.Play, null, Modifier.size(16.dp), tint = Color.White)
                    }
                    if (i.uri in sel) {
                        Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = 0.35f)))
                        Box(Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(cs.primary).padding(6.dp)) {
                            Icon(Ico.Check, null, Modifier.size(16.dp), tint = cs.onPrimary)
                        }
                    }
                }
                Text(i.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (i.audio) "صوت" else "فيديو", Modifier.weight(1f).padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    IconButton({ shareItem(ctx, i) }, Modifier.size(36.dp)) { Icon(Ico.Share, "مشاركة", Modifier.size(20.dp)) }
                    IconButton({ del = i }, Modifier.size(36.dp)) { Icon(Ico.Delete, "حذف", Modifier.size(20.dp)) }
                }
            }
        }
    }

    if (delMany) AlertDialog(
        onDismissRequest = { delMany = false },
        title = { Text("حذف ${sel.size} ملف؟") },
        text = { Text("هيتمسحوا من جهازك نهائيًا.") },
        confirmButton = {
            TextButton({
                Repo.library.filter { it.uri in sel }.forEach { Repo.remove(it) }
                sel = emptySet(); delMany = false
            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ delMany = false }) { Text("إلغاء") } }
    )

    del?.let { d ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("حذف الملف؟") },
            text = { Text("\"${d.title}\"\nهيتمسح من جهازك نهائيًا.", maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton({ Repo.remove(d); del = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ del = null }) { Text("إلغاء") } }
        )
    }
}

// ═══════════════════════════ الإعدادات ═══════════════════════════

@Composable
private fun HourStepper(label: String, h: Int, modifier: Modifier = Modifier, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton({ on((h + 23) % 24) }, Modifier.size(44.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text("%02d:00".format(h), style = MaterialTheme.typography.titleMedium)
        }
        IconButton({ on((h + 1) % 24) }, Modifier.size(44.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
fun SettingsScreen(bottom: Dp) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val ready by Repo.ready
    val st by Repo.status
    val hasCk by Repo.hasCookies
    var ver by remember { mutableStateOf("") }
    var ckMsg by remember { mutableStateOf("") }
    var howDialog by remember { mutableStateOf(false) }
    val sites = Repo.cookieSites
    LaunchedEffect(ready, st) { if (ready) ver = withContext(Dispatchers.IO) { Repo.version() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) ckMsg = if (Repo.importCookies(u)) "تم استيراد الكوكيز ✓" else "الملف مش بصيغة cookies.txt صحيحة"
    }
    val fmts = listOf("mp3", "m4a", "opus")
    val subOpts = listOf("ar.*,en.*", "ar.*", "en.*", "all,-live_chat")
    val treePick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                Prefs.treeUri = u.toString(); Prefs.save()
            } catch (_: Throwable) {}
        }
    }

    PageList(bottom) {
        item { Header("الإعدادات") }

        item { SectionTitle("التحميل", Modifier.padding(top = 0.dp)) }
        item {
            SettingRow(IcWifi, "الواي فاي فقط", "ما تستهلكش باقة الموبايل", onClick = { Prefs.wifiOnly = !Prefs.wifiOnly; Prefs.save() }) {
                Toggle(Prefs.wifiOnly) { Prefs.wifiOnly = it; Prefs.save() }
            }
        }
        item {
            SettingRow(IcSubs, "الترجمة افتراضيًا", "تضمين الترجمة في كل تحميل", onClick = { Prefs.subs = !Prefs.subs; Prefs.save() }) {
                Toggle(Prefs.subs) { Prefs.subs = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ico.Share, "تحميل سريع من المشاركة", "لما تشارك رابط يبدأ التحميل فورًا بدون نافذة اختيار", onClick = { Prefs.shareQuick = !Prefs.shareQuick; Prefs.save() }) {
                Toggle(Prefs.shareQuick) { Prefs.shareQuick = it; Prefs.save() }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(IcMusic, "صيغة الصوت")
                Switcher(fmts.map { it.uppercase() }, fmts.indexOf(Prefs.audioFmt)) { Prefs.audioFmt = fmts[it]; Prefs.save() }
            }
        }
        item {
            val lims = listOf(0, 1, 2, 5)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(IcDownload, "حد السرعة", "عشان التحميل ما ياكلش النت كله")
                Switcher(listOf("بدون", "1MB/s", "2MB/s", "5MB/s"), lims.indexOf(Prefs.limit).coerceAtLeast(0)) { Prefs.limit = lims[it]; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingRow(IcSubs, "طريقة الترجمة")
                Choices(listOf("مدمجة في الفيديو", "ملف SRT منفصل", "مدمجة + منفصلة", "ترجمة فقط"), Prefs.subsMode) { Prefs.subsMode = it; Prefs.save() }
                Footnote(when (Prefs.subsMode) {
                    1 -> "الملف المنفصل بيتحمّل بعد الفيديو، فلو يوتيوب رفضه الفيديو بيتحمّل عادي."
                    2 -> "الترجمة بتتدمج في الفيديو وبتتحفظ كمان كملف SRT."
                    3 -> "بينزّل ملف الترجمة بس من غير الفيديو (لما تفعّل «تضمين الترجمة»)."
                    else -> "الترجمة بتتدمج جوه ملف الفيديو."
                })
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(IcSubs, "لغة الترجمة")
                Switcher(listOf("عربي + EN", "عربي", "English", "الكل"), subOpts.indexOf(Prefs.subLangs)) { Prefs.subLangs = subOpts[it]; Prefs.save() }
                Footnote("«الكل» ممكن يحمّل ملفات كتير.")
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(IcDownload, "شكل اسم الملف")
                Switcher(listOf("العنوان", "القناة", "التاريخ", "المعرّف"), Prefs.nameTpl) { Prefs.nameTpl = it; Prefs.save() }
                Footnote(when (Prefs.nameTpl) {
                    1 -> "القناة - العنوان"; 2 -> "2025-01-31 - العنوان"; 3 -> "العنوان [معرّف الفيديو]"; else -> "العنوان فقط"
                })
            }
        }
        item {
            SettingRow(IcMusic, "غلاف وبيانات الصوت", "تضمين الصورة واسم القناة داخل الملف (MP3 وM4A)", onClick = { Prefs.embedMeta = !Prefs.embedMeta; Prefs.save() }) {
                Toggle(Prefs.embedMeta) { Prefs.embedMeta = it; Prefs.save() }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingRow(Ico.Library, "صورة الفيديو")
                Choices(listOf("بدون", "الصورة فقط", "مدمجة في الفيديو", "ملف منفصل", "مدمجة + منفصلة"), Prefs.thumbMode) { Prefs.thumbMode = it; Prefs.save() }
                Footnote(when (Prefs.thumbMode) {
                    1 -> "بينزّل الصورة بس (في Pictures) من غير الفيديو."
                    2 -> "الصورة بتتدمج كغلاف جوه الفيديو (ممكن تزوّد وقت المعالجة شوية)."
                    3 -> "الفيديو + صورته كملف منفصل في Pictures."
                    4 -> "الصورة مدمجة في الفيديو وكمان ملف منفصل في Pictures."
                    else -> "من غير صور."
                })
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingRow(IcMusic, "الدبلجة ولغة الصوت")
                Choices(listOf("الصوت الأصلي", "لغة مفضلة", "كل الأصوات"), Prefs.dubMode) { Prefs.dubMode = it; Prefs.save() }
                if (Prefs.dubMode == 1) {
                    val codes = listOf("ar", "en", "fr", "es", "de", "tr", "ru", "hi")
                    Choices(listOf("عربي", "English", "Français", "Español", "Deutsch", "Türkçe", "Русский", "हिन्दी"),
                        codes.indexOf(Prefs.dubLang)) { Prefs.dubLang = codes[it]; Prefs.save() }
                }
                Footnote(when (Prefs.dubMode) {
                    1 -> "لو الفيديو فيه صوت بلغتك المفضلة بيتحمّل بيه، وإلا بيتحمّل الأصلي."
                    2 -> "كل مسارات الصوت (الدبلجات) بتتدمج في ملف MKV وتختار منها وقت التشغيل."
                    else -> "الصوت الأصلي للفيديو."
                })
            }
        }
        item {
            SettingRow(IcSubs, "حفظ وصف الفيديو", "ملف نصي في Download", onClick = { Prefs.saveDesc = !Prefs.saveDesc; Prefs.save() }) {
                Toggle(Prefs.saveDesc) { Prefs.saveDesc = it; Prefs.save() }
            }
        }

        item { SectionTitle("مكان الحفظ") }
        item {
            SettingRow(Ico.Folder, "مجلد مخصص", if (Prefs.treeUri.isEmpty()) "الافتراضي: Movies / Music"
                else Uri.decode(Uri.parse(Prefs.treeUri).lastPathSegment ?: Prefs.treeUri)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilledTonalButton({ treePick.launch(null) }) { Text("اختيار") }
                    if (Prefs.treeUri.isNotEmpty()) TextButton({ Prefs.treeUri = ""; Prefs.save() }) { Text("إلغاء", color = cs.error) }
                }
            }
        }
        if (Prefs.treeUri.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(Prefs.folder, { Prefs.folder = it.filter { c -> c != '/' && c != '\\' }.take(30); Prefs.save() },
                    Modifier.fillMaxWidth(), label = { Text("اسم المجلد (داخل Movies / Music)") }, singleLine = true)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Switcher(listOf("عادي", "حسب الموقع", "حسب القناة"), Prefs.organize) { Prefs.organize = it; Prefs.save() }
                Footnote("حسب الموقع: YouTube / Instagram… وحسب القناة: مجلد باسم صاحب الفيديو.")
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingRow(IcLayers, "تحميلات متزامنة")
                Switcher(listOf("1", "2", "3"), Prefs.concurrent - 1) { Prefs.concurrent = it + 1; Prefs.save() }
            }
        }

        item { SectionTitle("الجدولة") }
        item {
            SettingRow(Ico.Clock, "تحميل في ساعات معينة", "التحميلات بتستنى لحد الموعد وبتبدأ لوحدها", onClick = { Prefs.schedOn = !Prefs.schedOn; Prefs.save() }) {
                Toggle(Prefs.schedOn) { Prefs.schedOn = it; Prefs.save() }
            }
        }
        if (Prefs.schedOn) item {
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HourStepper("من", Prefs.schedFrom, Modifier.weight(1f)) { Prefs.schedFrom = it; Prefs.save() }
                HourStepper("إلى", Prefs.schedTo, Modifier.weight(1f)) { Prefs.schedTo = it; Prefs.save() }
            }
        }
        item {
            SettingRow(Ico.Zap, "وقت الشحن فقط", "ما يحمّلش إلا والموبايل على الشاحن", onClick = { Prefs.chargingOnly = !Prefs.chargingOnly; Prefs.save() }) {
                Toggle(Prefs.chargingOnly) { Prefs.chargingOnly = it; Prefs.save() }
            }
        }
        item { Footnote("لبدء ملف فورًا اضغط ▶ جنبه. لو النظام قفل الخدمة، التحميلات المنتظرة بتكمّل لما تفتح التطبيق.") }

        item { SectionTitle("المظهر") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Switcher(listOf("تلقائي", "فاتح", "داكن"), Prefs.theme) { Prefs.theme = it; Prefs.save() }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    for (a in 0..5) {
                        val c = if (a == 0) cs.surfaceContainerHighest else AccentDots[a - 1]
                        Box(Modifier.size(46.dp).clip(CircleShape).background(c)
                            .then(if (Prefs.accent == a) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                            .clickable { Prefs.accent = a; Prefs.save() }, contentAlignment = Alignment.Center) {
                            if (a == 0) Text("A", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            else if (Prefs.accent == a) Icon(Ico.Check, null, tint = Color.White)
                        }
                    }
                }
                Footnote("«A» = ألوان خلفية الموبايل (أندرويد 12 وأحدث).")
            }
        }

        item { SectionTitle("المحرك") }
        item {
            SettingRow(IcEngine, "yt-dlp", if (st.isNotEmpty()) st else if (ver.isNotEmpty()) "الإصدار $ver" else "—") {
                Box(Modifier.clip(CircleShape).background(cs.primaryContainer).clickable { Repo.update() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Ico.Refresh, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                        Text("تحديث", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item { Footnote("بيتحدّث تلقائيًا كل 24 ساعة، وكمان لو التحميل فشل.") }

        item { SectionTitle("المتصفح الصغير والكوكيز") }
        item {
            SettingRow(Ico.Globe, "المتصفح الصغير", "سجّل دخول في أي موقع (يوتيوب، فيسبوك، إنستجرام…) والكوكيز بتتحفظ لـ Grab",
                onClick = { ctx.startActivity(Intent(ctx, LoginActivity::class.java)) }) {
                FilledTonalButton({ ctx.startActivity(Intent(ctx, LoginActivity::class.java)) }) { Text("فتح") }
            }
        }
        if (sites.isEmpty()) item {
            SettingRow(Ico.Cookie, "مفيش مواقع مسجّلة", "افتح المتصفح الصغير وسجّل دخول في الموقع اللي عايزه")
        } else {
            item { SettingRow(Ico.Lock, "مواقع مسجّل دخولها", "${sites.size} موقع", subColor = cs.tertiary) }
            items(sites.toList(), key = { "ck" + it.domain }) { site ->
                SettingRow(Ico.Cookie, site.domain, "${site.count} كوكي") {
                    IconButton({ Repo.deleteCookies(site.domain); ckMsg = "" }) { Icon(Ico.Delete, "حذف", tint = cs.error) }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ckMsg.isNotEmpty()) Text(ckMsg, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton({ picker.launch(arrayOf("*/*")) }) { Text("استيراد cookies.txt") }
                    if (hasCk) TextButton({ Repo.clearCookies(); ckMsg = "" }) { Text("حذف الكل", color = cs.error) }
                }
                Footnote("الكوكيز بتتطبّق تلقائيًا على كل موقع بتحمّل منه. لو جوجل منعت تسجيل الدخول جوه المتصفح الصغير، صدّر cookies.txt من متصفحك (إضافة Get cookies.txt LOCALLY) واستورده هنا. يفضّل تستخدم حساب ثانوي.")
            }
        }

        item { SectionTitle("عن التطبيق") }
        item {
            SettingRow(Ico.Info, "طريقة الاستخدام", "اعرض شرح الخطوات تاني",
                onClick = { howDialog = true }) {
                FilledTonalButton({ howDialog = true }) { Text("عرض") }
            }
        }
    }

    if (howDialog) AlertDialog(
        onDismissRequest = { howDialog = false },
        text = { HowTo(top = 0.dp) },
        confirmButton = { TextButton({ howDialog = false }) { Text("تمام") } }
    )
}

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// مجموعة أيقونات خطّية حديثة (24×24، حواف دايرية) مرسومة من مسارات SVG، فمفيش مكتبة أيقونات إضافية
private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

object Ico {
    val Home by lazy { icon("home",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z") }
    val Library by lazy { icon("library", "M4 4v16", "M8 8v12", "M12 6v14", "M16 6l4 14") }
    val Settings by lazy { icon("settings",
        "M10 5H3", "M12 19H3", "M14 3v4", "M16 17v4", "M21 12h-9", "M21 19h-5", "M21 5h-7", "M8 10v4", "M8 12H3") }
    val Download by lazy { icon("download",
        "M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5") }
    val Link by lazy { icon("link",
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71") }
    val Wifi by lazy { icon("wifi",
        "M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0") }
    val Subs by lazy { icon("captions",
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M7 15h4", "M15 15h2", "M7 11h2", "M13 11h4") }
    val Music by lazy { icon("music",
        "M9 18V5l12-2v13", "M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z") }
    val Layers by lazy { icon("layers",
        "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
        "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
        "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17") }
    val Pause by lazy { icon("pause",
        "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z") }
    val Play by lazy { icon("play", "M6 4.5v15a1 1 0 0 0 1.5.86l13-7.5a1 1 0 0 0 0-1.72l-13-7.5A1 1 0 0 0 6 4.5z") }
    val Terminal by lazy { icon("terminal", "M4 17l6-6-6-6", "M12 19h8") }
    val Cookie by lazy { icon("cookie",
        "M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5",
        "M8.5 8.5v.01", "M16 15.5v.01", "M12 12v.01", "M11 17v.01", "M7 14v.01") }
    val Lock by lazy { icon("lock",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 11V7a5 5 0 0 1 10 0v4") }
    val Check by lazy { icon("check", "M20 6L9 17l-5-5") }
    val CheckCircle by lazy { icon("check-circle", "M21.801 10A10 10 0 1 1 17 3.335", "M9 11l3 3L22 4") }
    val Close by lazy { icon("close", "M18 6L6 18", "M6 6l12 12") }
    val Delete by lazy { icon("trash",
        "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11v6", "M14 11v6") }
    val Refresh by lazy { icon("refresh", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5") }
    val Search by lazy { icon("search", "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16z", "M21 21l-4.3-4.3") }
    val Share by lazy { icon("share",
        "M21 5a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M9 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M21 19a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
        "M8.59 13.51l6.83 3.98", "M15.41 6.51l-6.82 3.98") }
    val Warning by lazy { icon("warning",
        "M21.73 18l-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01") }
    val Globe by lazy { icon("globe",
        "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20") }
    val ArrowRight by lazy { icon("arrow-right", "M5 12h14", "M12 5l7 7-7 7") }
    val Scissors by lazy { icon("scissors",
        "M9 6a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
        "M20 4L8.12 15.88", "M14.47 14.48L20 20", "M8.12 8.12L12 12") }
    val Folder by lazy { icon("folder",
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z") }
    val Clock by lazy { icon("clock", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 6v6l4 2") }
    val Zap by lazy { icon("zap",
        "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z") }
    val Info by lazy { icon("info", "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z", "M12 16v-4", "M12 8h.01") }
}
