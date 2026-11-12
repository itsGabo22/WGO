package com.wgo.navigator.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import androidx.compose.runtime.collectAsState
import com.wgo.navigator.ui.components.MapAttributionArc
import com.wgo.navigator.ui.components.WgoIcons
import com.wgo.navigator.ui.map.MapPickerDisc
import com.wgo.navigator.ui.map.MapTiles
import com.wgo.navigator.ui.map.MapPickerMath
import com.wgo.navigator.ui.map.MapPickerRing
import com.wgo.navigator.ui.map.RadarSchematic
import com.wgo.navigator.ui.map.RotaryAccumulator
import com.wgo.navigator.ui.map.StepThrottle
import com.wgo.navigator.ui.mapStyleGenre
import com.wgo.navigator.ui.mapStyleName
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoMapStyle
import com.wgo.navigator.ui.theme.wgoMinTouch
import com.wgo.navigator.ui.theme.wgoTouchSide
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val REFERENCE_SIZE = 227f
private const val RISE_MS = 360
private const val POP_MS = 400
private val DragPerStyle = 80.dp

/**
 * Screen "map picker": a bezel ring of 8 coloured arcs that rotates under a fixed pointer, a live preview disc of the
 * explored style, two neighbour thumbnails and the USAR button. The explored style is local state until USAR
 * ([onUse]); leaving by Back / swipe-from-the-edge happens outside this composable and changes nothing.
 * It is fully usable by touch (horizontal drag with snap and flick, tap a neighbour); the crown is an optional extra.
 * [onStep] fires once per style change (the ViewModel turns it into a Tick haptic).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreenMapPicker(
    currentStyle: MapStyleId,
    onUse: (MapStyleId) -> Unit,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    clock: WgoClock = rememberWgoClock()
) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val scope = rememberCoroutineScope()
    val k = remember { Animatable(currentStyle.ordinal.toFloat()) }
    val explored by remember { derivedStateOf { MapStyleId.entries[MapPickerMath.activeIndex(k.value)] } }
    // Only changes when the dial crosses to another style, so the rotation does not recompose the screen per frame.
    val active by remember { derivedStateOf { MapPickerMath.activeIndex(k.value) } }
    val throttle = remember { StepThrottle() }
    val rotary = remember { RotaryAccumulator() }
    val pxPerStyle = with(LocalDensity.current) { DragPerStyle.toPx() }
    val focusRequester = remember { FocusRequester() }

    suspend fun settle(target: Float) {
        if (reduceMotion) k.animateTo(target, tween(WgoMotion.SNAP)) else k.animateTo(target, WgoMotion.pop())
    }

    fun stepBy(delta: Int) {
        if (!throttle.accept(System.nanoTime() / 1_000_000L)) return
        scope.launch { settle(k.targetValue.roundToInt() + delta.toFloat()) }
    }

    // One Tick per style change, however the dial moved.
    LaunchedEffect(Unit) {
        snapshotFlow { MapPickerMath.activeIndex(k.value) }.drop(1).collect { onStep() }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val label = string(StringKey.USE)
    val name = mapStyleName(explored)
    val genre = mapStyleGenre(explored)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.canvas)
            .onRotaryScrollEvent { event ->
                val step = rotary.add(event.verticalScrollPixels)
                if (step != 0) stepBy(step)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        k.snapTo(k.value + MapPickerMath.indexDelta(delta, pxPerStyle))
                    }
                },
                onDragStarted = { scope.launch { k.stop() } },
                onDragStopped = { velocity -> scope.launch { settle(MapPickerMath.snapTarget(k.value, velocity)) } }
            )
    ) {
        val scale = maxWidth.value / REFERENCE_SIZE
        fun dial(value: Float): Dp = (value * scale).dp

        MapPickerRing(index = { k.value })

        // Name @22 and genre @41, announced on every change.
        val rise = remember { Animatable(1f) }
        LaunchedEffect(explored) {
            if (!reduceMotion) {
                rise.snapTo(0f)
                rise.animateTo(1f, tween(RISE_MS, easing = WgoMotion.EmphasizedDecel))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = dial(22f))
                .width(dial(150f))
                .graphicsLayer {
                    val p = rise.value
                    alpha = p
                    translationY = (1f - p) * 14.dp.toPx()
                }
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "$name, $genre"
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name.uppercase(),
                color = palette.ink,
                style = WgoType.headlineSm,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = genre,
                color = palette.inkMuted,
                style = WgoType.labelSm,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Preview disc 104 dp at (61.5, 58).
        MapPickerDisc(
            styleId = explored,
            clock = clock,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(58f)).size(dial(104f))
        )

        // Neighbour thumbnails 32 dp at x 14 / x 181, top 94, 60% alpha, tappable (48 real dp hit boxes).
        val hit = wgoTouchSide(32.dp)
        val extra = (hit - 32.dp) / 2
        Neighbour(
            styleId = MapStyleId.entries[MapPickerMath.neighbour(active, -1)],
            onClick = { stepBy(-1) },
            modifier = Modifier.align(Alignment.TopStart).offset(x = dial(14f) - extra, y = dial(94f) - extra)
        )
        Neighbour(
            styleId = MapStyleId.entries[MapPickerMath.neighbour(active, 1)],
            onClick = { stepBy(1) },
            modifier = Modifier.align(Alignment.TopStart).offset(x = dial(181f) - extra, y = dial(94f) - extra)
        )

        // USAR 76 x 28 @170: commits the explored style.
        val useExtra = (wgoTouchSide(28.dp) - 28.dp) / 2
        UseButton(
            label = label,
            onClick = { onUse(explored) },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(170f) - useExtra)
        )

        // The map credit along the bottom arc, below USAR (spec 9.1, P8): the preview stands for the real map.
        val provider by MapTiles.provider.collectAsState()
        MapAttributionArc(visible = true, provider = provider)
    }
}

@Composable
private fun Neighbour(styleId: MapStyleId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(styleId) {
        if (!reduceMotion) {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(POP_MS, easing = WgoMotion.EmphasizedDecel))
        }
    }
    val description = mapStyleName(styleId)
    Box(
        modifier = modifier
            .wgoMinTouch()
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        RadarSchematic(
            style = wgoMapStyle(styleId, palette),
            live = false,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    val s = 0.6f + 0.4f * pop.value
                    scaleX = s
                    scaleY = s
                    alpha = 0.6f
                }
                .clip(CircleShape)
                .border(1.5.dp, palette.ink, CircleShape)
        )
    }
}

@Composable
private fun UseButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (pressed) palette.ink else palette.primary
    val content = if (pressed) palette.inkInverse else palette.onPrimary
    Box(
        modifier = modifier
            .width(76.dp)
            .wgoMinTouch(height = 48.dp)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .size(76.dp, 28.dp)
                .clip(CircleShape)
                .background(background),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = WgoIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Text(text = label.uppercase(), color = content, style = WgoType.labelMd, maxLines = 1)
        }
    }
}
