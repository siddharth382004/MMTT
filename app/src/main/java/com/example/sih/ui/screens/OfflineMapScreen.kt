package com.example.sih.ui.screens

import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon

import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sih.maps.mapsforge.MapsforgeMapComposable
import com.example.sih.ui.MainActivity
import com.example.sih.ui.PathTracker

@Composable
fun OfflineMapScreen(
    modifier: Modifier = Modifier,
    recenterTrigger: Long = 0L
) {
    // Observe location from MainActivity static states (as per existing architecture)
    val latString by remember { derivedStateOf { MainActivity.lat.value } }
    val lonString by remember { derivedStateOf { MainActivity.lon.value } }

    var currentLat by remember { mutableDoubleStateOf(20.59) }
    var currentLon by remember { mutableDoubleStateOf(78.96) }

    // Use a timestamp trigger for centering to avoid boolean race conditions
    // Initialize to 0 so we only center if the user clicks or a valid location update occurs
    var centerMapTrigger by remember { mutableLongStateOf(0L) }

    // React to parent's request to recenter
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            centerMapTrigger = recenterTrigger
        }
    }

    // Update coordinates when MainActivity state changes
    LaunchedEffect(latString, lonString) {
        val newLat = latString.toDoubleOrNull()
        val newLon = lonString.toDoubleOrNull()

        if (newLat != null && newLon != null && newLat != 0.0 && newLon != 0.0) {
            // Automatically center the map when a new valid location is received
            // We compare against currentLat/Lon to avoid redundant triggers
            if (currentLat != newLat || currentLon != newLon) {
                currentLat = newLat
                currentLon = newLon
                
                // Force a center event when location updates
                centerMapTrigger = System.currentTimeMillis()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapsforgeMapComposable(
            modifier = Modifier.fillMaxSize(),
            currentLat = currentLat,
            currentLon = currentLon,
            centerTrigger = centerMapTrigger
        )

        // FAB to Manually Re-Center
        FloatingActionButton(
            onClick = { centerMapTrigger = System.currentTimeMillis() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Center Map")
        }
    }
}
