package com.wgo.navigator.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Width of the left-edge strip that starts a swipe-back (reference dp; Wear's own edge default is 30 dp). */
private val EdgeWidth = 30.dp

/** Rightward travel that commits the swipe-back. */
private val CommitDistance = 56.dp

/**
 * In-app swipe-to-dismiss, LEFT EDGE ONLY (the window's own swipe-to-dismiss is off, see themes.xml), so horizontal
 * drags anywhere else (the map picker dial) stay inside the app. A rightward drag that starts within [EdgeWidth] of
 * the left edge is claimed in the Initial pass (children see it as consumed and cancel their own drags) and calls
 * [onBack] once it has travelled [CommitDistance]. Taps at the edge are not consumed.
 */
fun Modifier.wgoEdgeSwipeBack(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    val edge = EdgeWidth.toPx()
    val commit = CommitDistance.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x > edge) return@awaitEachGesture
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) return@awaitEachGesture
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            if (!claimed) {
                if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture // vertical: not ours
                if (dx > slop && dx > abs(dy)) claimed = true
            }
            if (claimed) {
                change.consume()
                if (dx >= commit) {
                    onBack()
                    // Swallow the rest of this gesture.
                    while (true) {
                        val rest = awaitPointerEvent(PointerEventPass.Initial)
                        rest.changes.forEach { it.consume() }
                        if (rest.changes.none { it.pressed }) return@awaitEachGesture
                    }
                }
            }
        }
    }
}
