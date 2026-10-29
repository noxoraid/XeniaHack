package com.uplink.app

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex


enum class NxPalette(val key: String, private val label: String) {
    IBank("ibank", "iBank");

    val title: String get() = tr(label)

    fun skinFor(dark: Boolean): NxSkin = if (dark) SkinIBankDark else SkinIBankLight
}

class NxSkin(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val live: Color,
    val danger: Color,
    val glow: Color,
    val head: Color,
    val soft: Color,
)

private val SkinIBankLight = NxSkin(
    Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFF2F1F9), Color(0xFFE0E0E0), Color(0xFF343434), Color(0xFF898989),
    Color(0xFF281C9D), Color(0xFFFFFFFF), Color(0xFF52D5BA), Color(0xFFFF4267), Color(0xFF5655B9),
    Color(0xFF281C9D), Color(0xFFA8A3D7),
)

private val SkinIBankDark = NxSkin(
    Color(0xFF17142F), Color(0xFF1E1A3C), Color(0xFF272250), Color(0xFF332E5E), Color(0xFFF2F1F9), Color(0xFFA19CC8),
    Color(0xFF8F8AF2), Color(0xFF0F0D23), Color(0xFF52D5BA), Color(0xFFFF6B88), Color(0xFF5655B9),
    Color(0xFF0F0D23), Color(0xFFA8A3D7),
)

object NxMode {
    var palette by mutableStateOf(NxPalette.IBank)
        private set
    var dark by mutableStateOf(false)
        private set

    val skin: NxSkin get() = palette.skinFor(dark)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("noxora_ui", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val store = prefs(ctx)
        dark = store.getBoolean("dark", false)
        palette = NxPalette.values().firstOrNull { it.key == store.getString("palette", "ibank") } ?: NxPalette.IBank
    }

    fun pick(ctx: Context, value: NxPalette) {
        palette = value
        prefs(ctx).edit().putString("palette", value.key).apply()
    }

    fun set(ctx: Context, value: Boolean) {
        dark = value
        prefs(ctx).edit().putBoolean("dark", value).apply()
    }
}

val NxBg: Color get() = NxMode.skin.bg
val NxSurface: Color get() = NxMode.skin.surface
val NxRaised: Color get() = NxMode.skin.raised
val NxLine: Color get() = NxMode.skin.line
val NxText: Color get() = NxMode.skin.text
val NxDim: Color get() = NxMode.skin.dim
val NxAccent: Color get() = NxMode.skin.accent
val NxOnAccent: Color get() = NxMode.skin.onAccent
val NxLive: Color get() = NxMode.skin.live
val NxDanger: Color get() = NxMode.skin.danger
val NxGlow: Color get() = NxMode.skin.glow
val NxHead: Color get() = NxMode.skin.head
val NxSoft: Color get() = NxMode.skin.soft

val NxOnHead = Color(0xFFFFFFFF)
val NxPrimaryVariant = Color(0xFF5655B9)
val NxRed = Color(0xFFFF4267)
val NxBlue = Color(0xFF0890FE)
val NxYellow = Color(0xFFFFAF2A)
val NxMint = Color(0xFF52D5BA)
val NxOrange = Color(0xFFFB6B18)
val NxHero = Color(0xFF1E1470)

private val NxShade = Color(0xFF281C9D)
val LocalOnHead = compositionLocalOf { false }

enum class Tone { Accent, Quiet, Danger }

@Composable
fun Modifier.nxSafe(): Modifier = this.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))

fun toast(ctx: Context, msg: String) {
    Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}

private fun typographyOf(face: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = face),
        displayMedium = base.displayMedium.copy(fontFamily = face),
        displaySmall = base.displaySmall.copy(fontFamily = face),
        headlineLarge = base.headlineLarge.copy(fontFamily = face),
        headlineMedium = base.headlineMedium.copy(fontFamily = face),
        headlineSmall = base.headlineSmall.copy(fontFamily = face),
        titleLarge = base.titleLarge.copy(fontFamily = face),
        titleMedium = base.titleMedium.copy(fontFamily = face),
        titleSmall = base.titleSmall.copy(fontFamily = face),
        bodyLarge = base.bodyLarge.copy(fontFamily = face),
        bodyMedium = base.bodyMedium.copy(fontFamily = face),
        bodySmall = base.bodySmall.copy(fontFamily = face),
        labelLarge = base.labelLarge.copy(fontFamily = face),
        labelMedium = base.labelMedium.copy(fontFamily = face),
        labelSmall = base.labelSmall.copy(fontFamily = face),
    )
}

@Composable
fun NxTheme(content: @Composable () -> Unit) {
    val face = FontPrefs.ui.family
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !NxMode.dark
        }
    }
    val scheme = if (NxMode.dark) {
        darkColorScheme(
            primary = NxAccent, onPrimary = NxOnAccent, background = NxBg, onBackground = NxText,
            surface = NxRaised, onSurface = NxText, surfaceTint = Color.Transparent,
        )
    } else {
        lightColorScheme(
            primary = NxAccent, onPrimary = NxOnAccent, background = NxBg, onBackground = NxText,
            surface = NxRaised, onSurface = NxText, surfaceTint = Color.Transparent,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = typographyOf(face),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = face, color = NxText, fontSize = 14.sp),
            LocalTextSelectionColors provides TextSelectionColors(NxAccent, NxAccent.copy(alpha = 0.28f)),
        ) {
            Surface(color = NxBg, modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

fun Modifier.nxShadow(shape: Shape): Modifier =
    if (NxMode.dark) this else this.shadow(6.dp, shape, clip = false, ambientColor = NxShade.copy(alpha = 0.16f), spotColor = NxShade.copy(alpha = 0.16f))

fun Modifier.nxPress(onTap: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.96f else 1f, spring(0.55f, 600f), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onTap)
}

fun Modifier.nxTap(radius: Dp = 12.dp, onTap: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down && radius > 0.dp) 0.96f else 1f, spring(0.55f, 600f), label = "tap")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(radius))
        .background(if (down) NxRaised else Color.Transparent)
        .clickable(interactionSource = source, indication = null, onClick = onTap)
}

private fun DrawScope.sheetCorners(color: Color, radius: Float) {
    val top = size.height
    val left = Path().apply {
        moveTo(0f, top)
        lineTo(radius, top)
        arcTo(Rect(0f, top, radius * 2f, top + radius * 2f), 270f, -90f, false)
        close()
    }
    val right = Path().apply {
        moveTo(size.width, top)
        lineTo(size.width - radius, top)
        arcTo(Rect(size.width - radius * 2f, top, size.width, top + radius * 2f), 270f, 90f, false)
        close()
    }
    drawPath(left, color)
    drawPath(right, color)
}

@Composable
fun NxBar(
    title: String,
    subtitle: String? = null,
    nav: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val head = NxHead
    val radius = with(LocalDensity.current) { 32.dp.toPx() }
    Column(
        Modifier.fillMaxWidth().zIndex(1f)
            .drawWithContent { drawContent(); sheetCorners(head, radius) }
            .background(head)
            .statusBarsPadding(),
    ) {
        CompositionLocalProvider(LocalOnHead provides true) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (nav != null) nav() else Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = NxOnHead, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(subtitle, fontSize = 12.sp, color = NxSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }
    }
}

fun Modifier.nxGlass(radius: Dp = 16.dp, hot: Boolean = false): Modifier = composed {
    val shape = RoundedCornerShape(radius)
    val rim by animateColorAsState(
        if (hot) NxAccent else if (NxMode.dark) NxLine else Color.Transparent, tween(200), label = "glassRim",
    )
    this
        .nxShadow(shape)
        .clip(shape)
        .background(NxSurface)
        .border(1.dp, rim, shape)
}

@Composable
fun NxCard(
    modifier: Modifier = Modifier,
    hot: Boolean = false,
    onTap: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val lift = animateFloatAsState(if (down) 1f else 0f, spring(0.6f, 520f), label = "cardLift")
    Column(
        modifier
            .graphicsLayer {
                val k = 1f - 0.04f * lift.value
                scaleX = k
                scaleY = k
            }
            .nxGlass(16.dp, hot)
            .then(if (onTap != null) Modifier.clickable(interactionSource = source, indication = null, onClick = onTap) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun NxSection(text: String, modifier: Modifier = Modifier) {
    val shown = if (text.length > 4 && text == text.uppercase() && text.any { it.isLetter() }) {
        text.lowercase().replaceFirstChar { it.uppercase() }
    } else {
        text
    }
    Text(
        shown, modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = NxText, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun NxIconButton(icon: ImageVector, description: String, tint: Color = NxText, onTap: () -> Unit) {
    if (LocalOnHead.current) {
        Box(Modifier.size(48.dp).nxPress(onTap), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(NxOnHead.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, description, Modifier.size(20.dp), tint = NxOnHead)
            }
        }
    } else {
        Box(Modifier.size(48.dp).nxTap(14.dp, onTap), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(22.dp), tint = tint)
        }
    }
}

@Composable
fun NxHeadAction(icon: ImageVector, description: String, dot: Boolean = false, onTap: () -> Unit) {
    Box(contentAlignment = Alignment.TopEnd) {
        NxIconButton(icon, description, onTap = onTap)
        if (dot) Box(Modifier.padding(top = 8.dp, end = 8.dp).size(9.dp).clip(CircleShape).background(NxRed))
    }
}

@Composable
fun NxButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone = Tone.Quiet,
    compact: Boolean = false,
    wide: Boolean = false,
    onTap: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val down by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (down) 0.96f else 1f, spring(0.5f, 700f), label = "press")
    val shape = RoundedCornerShape(16.dp)
    val fill = when (tone) {
        Tone.Accent -> NxAccent
        Tone.Danger -> NxDanger.copy(alpha = 0.12f)
        Tone.Quiet -> NxRaised
    }
    val ink = when (tone) {
        Tone.Accent -> NxOnAccent
        Tone.Danger -> NxDanger
        Tone.Quiet -> NxText
    }
    Row(
        modifier
            .then(if (wide) Modifier.fillMaxWidth() else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(SolidColor(if (down) fill.copy(alpha = fill.alpha * 0.82f) else fill))
            .clickable(interactionSource = source, indication = null, onClick = onTap)
            .heightIn(min = if (compact) 36.dp else 48.dp)
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 8.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(if (compact) 16.dp else 18.dp), tint = ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 13.sp else 14.sp, color = ink, maxLines = 1)
    }
}

@Composable
fun NxField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    obscure: Boolean = false,
    mono: Boolean = false,
    lines: Int = 1,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val rim by animateColorAsState(if (focused) NxAccent else Color.Transparent, tween(180), label = "fieldRim")
    val tag by animateColorAsState(if (focused) NxAccent else NxDim, tween(180), label = "fieldTag")
    val rise by animateFloatAsState(if (focused) 1f else 0f, spring(0.7f, 500f), label = "fieldRise")
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(
            label, fontSize = 12.sp, color = tag,
            modifier = Modifier.padding(bottom = 6.dp).graphicsLayer { translationX = rise * 4.dp.toPx() },
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = lines == 1,
            minLines = lines,
            maxLines = lines,
            textStyle = TextStyle(
                fontFamily = if (mono) FontPrefs.mono.family else FontPrefs.ui.family,
                fontSize = 14.sp, color = NxText,
            ),
            cursorBrush = SolidColor(NxAccent),
            visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                keyboardType = if (obscure) KeyboardType.Password else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(NxRaised)
                .border(1.dp, rim, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun NxDialog(
    title: String?,
    onDismiss: () -> Unit,
    confirm: String,
    onConfirm: () -> Unit,
    tone: Tone = Tone.Accent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .graphicsLayer {
                    alpha = pop.value.coerceIn(0f, 1f)
                    scaleX = 0.9f + 0.1f * pop.value
                    scaleY = 0.9f + 0.1f * pop.value
                    translationY = (1f - pop.value) * 28.dp.toPx()
                }
                .nxGlass(24.dp)
                .padding(20.dp),
        ) {
            if (title != null) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NxButton(tr("Cancel"), Modifier.weight(1f), wide = true, onTap = onDismiss)
                NxButton(confirm, Modifier.weight(1f), tone = tone, wide = true, onTap = onConfirm)
            }
        }
    }
}

@Composable
fun NxAsk(title: String, initial: String, label: String, lines: Int, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    NxDialog(title, onDismiss, tr("OK"), { onOk(text.trim()) }) {
        NxField(text, { text = it }, label, mono = true, lines = lines)
    }
}

@Composable
fun NxConfirm(message: String, confirm: String = tr("Yes"), onDismiss: () -> Unit, onYes: () -> Unit) {
    NxDialog(null, onDismiss, confirm, onYes, Tone.Danger) {
        Text(message, modifier = Modifier.padding(bottom = 18.dp))
    }
}
