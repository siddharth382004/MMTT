package com.example.sih.maps.mapsforge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sih.ui.PathTracker
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Marker
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import java.io.File
import java.io.FileOutputStream

@Composable
fun MapsforgeMapComposable(
    modifier: Modifier = Modifier,
    mapFileName: String = "india.map",
    currentLat: Double,
    currentLon: Double,
    centerTrigger: Long,
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current

    // Keep single MapView across recompositions
    val mapView = remember {
        MapView(context).apply {
            isClickable = true
            setBuiltInZoomControls(false)
            model.displayModel.setFixedTileSize(256)
        }
    }

    val isInitialized = remember { mutableStateOf(false) }

    if (!isInitialized.value) {
        try {
            val mapFile = File(context.filesDir, mapFileName)
            if (!mapFile.exists()) {
                // copy map from assets if present
                try {
                    context.assets.open(mapFileName).use { input ->
                        FileOutputStream(mapFile).use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {
                    // ignore if asset not present
                }
            }

            if (mapFile.exists()) {
                val tileCache = AndroidUtil.createTileCache(
                    context,
                    "mapcache",
                    mapView.model.displayModel.tileSize,
                    1f,
                    mapView.model.frameBufferModel.overdrawFactor,
                    true
                )

                val store = MapFile(mapFile)
                val tileLayer = TileRendererLayer(
                    tileCache,
                    store,
                    mapView.model.mapViewPosition,
                    AndroidGraphicFactory.INSTANCE
                )
                tileLayer.setXmlRenderTheme(InternalRenderTheme.DEFAULT)
                mapView.layerManager.layers.add(tileLayer)

                // initial camera position with a higher zoom level (e.g., 15)
                mapView.model.mapViewPosition.mapPosition = MapPosition(LatLong(currentLat, currentLon), 15.toByte())

                isInitialized.value = true
                try { onMapReady(mapView) } catch (_: Throwable) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ------------------------
    // Create polyline (single instance)
    // ------------------------
    val polyline = remember {
        val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            color = android.graphics.Color.BLUE
            strokeWidth = 3f // Made it thinner (was 5f, started as 6f Red)
            setStrokeCap(org.mapsforge.core.graphics.Cap.ROUND)
        }
        Polyline(paint, AndroidGraphicFactory.INSTANCE)
    }

    // Add polyline layer once when initialized
    LaunchedEffect(isInitialized.value) {
        if (isInitialized.value) {
            try {
                if (!mapView.layerManager.layers.contains(polyline)) {
                    mapView.layerManager.layers.add(polyline)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ------------------------
    // Update polyline whenever active sender or its path changes or current coordinate changes
    // ------------------------
    LaunchedEffect(isInitialized.value, PathTracker.activeSender, currentLat, currentLon) {
        if (!isInitialized.value) return@LaunchedEffect
        try {
            polyline.latLongs.clear()
            val sender = PathTracker.activeSender
            if (sender != null) {
                val list = PathTracker.senderPaths[sender] ?: emptyList()
                list.forEach { p -> polyline.latLongs.add(LatLong(p.latitude, p.longitude)) }
            }

            // ensure current coordinate is part of line (avoid duplicates)
            val cur = LatLong(currentLat, currentLon)
            if (polyline.latLongs.isEmpty() || polyline.latLongs.last() != cur) {
                polyline.latLongs.add(cur)
            }

            mapView.layerManager.redrawLayers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ------------------------
    // Marker management (single marker)
    // ------------------------
    val globalMarkerRef = remember { mutableStateOf<Marker?>(null) }

    fun updateOrCreateMarker(lat: Double, lon: Double) {
        try {
            val pos = LatLong(lat, lon)
            val existing = globalMarkerRef.value
            if (existing == null) {
                val bmp = createMarkerBitmap(mapView.context)
                val marker = Marker(pos, bmp, 0, -bmp.height / 2)
                mapView.layerManager.layers.add(marker)
                globalMarkerRef.value = marker
            } else {
                existing.latLong = pos
            }
            mapView.layerManager.redrawLayers()
            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ------------------------
    // Center map if requested by parent (centerTrigger)
    // ------------------------
    LaunchedEffect(isInitialized.value, centerTrigger) {
        if (!isInitialized.value) return@LaunchedEffect

        if (centerTrigger > 0) {
            try {
                // Use current zoom or enforce minimum 15
                val currentZoom = mapView.model.mapViewPosition.zoomLevel
                val targetZoom = if (currentZoom < 15) 15.toByte() else currentZoom

                // We use assignment to force the position, ensuring we land on the marker
                mapView.model.mapViewPosition.mapPosition = MapPosition(
                    LatLong(currentLat, currentLon),
                    targetZoom
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Keep marker updated with latest location (and create if missing)
    LaunchedEffect(isInitialized.value, currentLat, currentLon) {
        if (!isInitialized.value) return@LaunchedEffect
        updateOrCreateMarker(currentLat, currentLon)
    }

    // AndroidView host
    AndroidView(
        modifier = modifier,
        factory = { mapView }
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                mapView.destroyAll()
                AndroidGraphicFactory.clearResourceMemoryCache()
            } catch (_: Exception) {}
        }
    }
}

// ------------------------
// Attractive custom marker: red pin with white center
// ------------------------
private fun createMarkerBitmap(context: android.content.Context): org.mapsforge.core.graphics.Bitmap {
    val bmp = Bitmap.createBitmap(56, 72, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // red circular head
    paint.color = android.graphics.Color.RED
    canvas.drawCircle(28f, 24f, 24f, paint)

    // tail / triangle
    val path = Path().apply {
        moveTo(8f, 28f)
        lineTo(28f, 72f)
        lineTo(48f, 28f)
        close()
    }
    canvas.drawPath(path, paint)

    // white center circle
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(28f, 24f, 10f, paint)

    return AndroidGraphicFactory.convertToBitmap(BitmapDrawable(context.resources, bmp))
}
