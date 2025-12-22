package com.example.sih.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sih.db.SmsDatabase
import com.example.sih.db.SmsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter

class SmsHistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmsGroupedUI() }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun SmsGroupedUI() {
        var smsList by remember { mutableStateOf(listOf<SmsEntity>()) }
        var isLoading by remember { mutableStateOf(true) }

        // Search State
        var searchQuery by remember { mutableStateOf("") }

        // Selection State
        val selectedMessages = remember { mutableStateListOf<SmsEntity>() }
        val isSelectionMode = selectedMessages.isNotEmpty()

        val expandedSenders = remember { mutableStateListOf<String>() }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Function to refresh data from DB
        fun loadData() {
            scope.launch(Dispatchers.IO) {
                try {
                    val db = SmsDatabase.getDatabase(applicationContext)
                    smsList = db.smsDao().getAllMessages()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }

        // Initial Load
        LaunchedEffect(Unit) { loadData() }

        // Export Logic
        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
            onResult = { uri ->
                uri?.let {
                    scope.launch(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                val writer = BufferedWriter(OutputStreamWriter(outputStream))
                                writer.write("ID,Sender,Message,Latitude,Longitude,Battery,Signal,Time\n")
                                for (sms in smsList) {
                                    val safeMessage = sms.message.replace(",", " ").replace("\n", " ")
                                    writer.write("${sms.id},${sms.sender},\"$safeMessage\",${sms.lat},${sms.lon},${sms.battery},${sms.signal},${sms.time}\n")
                                }
                                writer.flush()
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "✅ Saved to CSV!", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )

        // Delete Logic
        fun deleteSelected() {
            if (selectedMessages.isEmpty()) return

            scope.launch(Dispatchers.IO) {
                try {
                    val db = SmsDatabase.getDatabase(applicationContext)
                    db.smsDao().deleteMessages(selectedMessages.toList())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Deleted ${selectedMessages.size} messages", Toast.LENGTH_SHORT).show()
                        selectedMessages.clear()
                        loadData() // Refresh list to remove deleted items
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Delete error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // FILTERING LOGIC
        val filteredList = if (searchQuery.isEmpty()) {
            smsList
        } else {
            smsList.filter {
                it.sender.contains(searchQuery, ignoreCase = true) ||
                        it.message.contains(searchQuery, ignoreCase = true)
            }
        }

        val grouped = filteredList.groupBy { it.sender }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedMessages.size} Selected", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text("SMS History", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isSelectionMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = if (isSelectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { selectedMessages.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Selection")
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = { deleteSelected() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(onClick = {
                                if (smsList.isNotEmpty()) {
                                    exportLauncher.launch("SIH_Logs_${System.currentTimeMillis()}.csv")
                                } else {
                                    Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Export to Excel")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

                // --- SEARCH BAR ---
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = { Text("Search Sender or Message...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages found.", style = MaterialTheme.typography.headlineSmall)
                    }
                } else {
                    LazyColumn {
                        grouped.forEach { (sender, messages) ->
                            // Check if ALL messages from this sender are currently selected
                            val isSenderSelected = messages.all { selectedMessages.contains(it) }

                            item {
                                SenderHeader(
                                    sender = sender,
                                    count = messages.size,
                                    isSelected = isSenderSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSenderSelected) {
                                                selectedMessages.removeAll(messages)
                                            } else {
                                                val toAdd = messages.filter { !selectedMessages.contains(it) }
                                                selectedMessages.addAll(toAdd)
                                            }
                                        } else {
                                            if (sender in expandedSenders) expandedSenders.remove(sender)
                                            else expandedSenders.add(sender)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            selectedMessages.addAll(messages)
                                            if (sender !in expandedSenders) expandedSenders.add(sender)
                                            Toast.makeText(context, "Selected all from $sender", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            if (sender in expandedSenders) {
                                items(messages, key = { it.id }) { sms ->
                                    val isSelected = selectedMessages.contains(sms)
                                    SmsItem(
                                        sms = sms,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedMessages.remove(sms) else selectedMessages.add(sms)
                                            } else {
                                                openMap(sms.lat, sms.lon)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectedMessages.add(sms)
                                                Toast.makeText(context, "Selection Mode Started", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SenderHeader(
        sender: String,
        count: Int,
        isSelected: Boolean,
        isSelectionMode: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = sender, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = "$count Messages", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SmsItem(
        sms: SmsEntity,
        isSelected: Boolean,
        isSelectionMode: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.White

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, bottom = 8.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Lat: ${sms.lat}, Lon: ${sms.lon}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = sms.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // FIXED: Ensure text wraps correctly with fillMaxWidth
                    Text(
                        text = sms.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)) {
                            Text(text = "Bat: ${sms.battery}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)) {
                            Text(text = "Sig: ${sms.signal}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    private fun openMap(lat: String, lon: String) {
        val intent = Intent(this, MapActivity::class.java)
        intent.putExtra("lat", lat)
        intent.putExtra("lon", lon)
        startActivity(intent)
    }
}