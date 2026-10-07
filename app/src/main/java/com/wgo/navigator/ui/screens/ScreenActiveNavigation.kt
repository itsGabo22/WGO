package com.wgo.navigator.ui.screens

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.theme.WgoWhite
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun ScreenActiveNavigation(
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Z-Layer 0: MAP (Real OSM MapView)
        var mapController by remember { mutableStateOf<org.osmdroid.api.IMapController?>(null) }
        
        WgoRealMap(
            onMapReady = { controller ->
                mapController = controller
            }
        )

        // Z-Layer 2: TOP NAVIGATION HUD (Floating card)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colors.surface.copy(alpha = 0.90f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = string(StringKey.GO_STRAIGHT),
                        tint = WgoPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "200m",
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = string(StringKey.NAV_INSTRUCTION),
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Z-Layer 2.5: ZOOM CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { mapController?.zoomIn() },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colors.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = string(StringKey.ZOOM_IN),
                    modifier = Modifier.size(20.dp)
                )
            }

            Button(
                onClick = { mapController?.zoomOut() },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colors.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = string(StringKey.ZOOM_OUT),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Z-Layer 3: BOTTOM ACTION HUD
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            val isLight = MaterialTheme.colors.background == WgoWhite
            Chip(
                onClick = onCancel,
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (isLight) Color(0xFFF2F4F7) else Color(0xFF1E1E1E),
                    contentColor = MaterialTheme.colors.onBackground
                ),
                label = {
                    Text(
                        text = string(StringKey.BTN_CANCEL),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(40.dp)
            )
        }
    }
}

@Composable
fun WgoRealMap(
    onMapReady: (org.osmdroid.api.IMapController) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            // Future extension point: 
            // setTileSource(MyCustomGtaTileSource())
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false) // Disable multi-touch per instruction
            
            // Disable default zoom controls
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            
            val mapController = controller
            mapController.setZoom(16.0)
            
            // Initial position: Pasto, Colombia
            val pastoPoint = GeoPoint(1.2136, -77.2811)
            mapController.setCenter(pastoPoint)

            // Prevent touch intercept issues with Compose/WearOS swipes if necessary
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_DOWN) {
                    performClick()
                }
                false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = {
            onMapReady(mapView.controller)
            mapView
        },
        modifier = Modifier.fillMaxSize()
    )
}
