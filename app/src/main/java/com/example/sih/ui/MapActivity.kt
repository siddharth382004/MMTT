package com.example.sih.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sih.db.SmsDatabase
import com.example.sih.db.SmsEntity
import com.example.sih.ui.screens.OfflineMapScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.ArrayList

class MapActivity : ComponentActivity() {

    private val targetDevices = listOf(
        TargetDevice("ROGER 1", "+917898185721", "esp01"),
        TargetDevice("ROGER 2", "+919685168488", "esp02")
    )

    private val locationCommand = "#LOCATION"
    private val operationalKeywords = listOf("LAT", "LON", "DANGER", "CONNECTION LOST", "ROGER")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidGraphicFactory.createInstance(application)

        checkPermissions()
        setContent { MainAppUI() }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.INTERNET
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissionsToRequest.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissions(notGranted.toTypedArray(), 1)
        }
    }

    private suspend fun getFilteredSmsList(context: Context, isSosFilter: Boolean = false): List<SmsEntity> = withContext(Dispatchers.IO) {
        SmsDatabase.getDatabase(context).smsDao().getAllMessages().filter { sms ->
            if (isSosFilter) {
                sms.message.contains("DANGER", ignoreCase = true) ||
                sms.message.contains("SOS", ignoreCase = true) ||
                sms.message.contains("HELP", ignoreCase = true)
            } else {
                operationalKeywords.any { keyword ->
                    sms.message.contains(keyword, ignoreCase = true)
                }
            }
        }
    }

    private fun sendLocationCommandSms(context: Context, number: String, command: String) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "SMS permission not granted.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, command, null, null)
            Toast.makeText(context, "Command sent to $number", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "SMS failed to send.", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun MainAppUI() {
        var selectedTab by remember { mutableIntStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.LocationOn, "Offline") },
                        label = { Text("Offline") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF1976D2), indicatorColor = Color(0xFFE3F2FD))
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Place, "Online") },
                        label = { Text("Online") },
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF1976D2), indicatorColor = Color(0xFFE3F2FD))
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Email, "Inbox") },
                        label = { Text("Inbox") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF1976D2), indicatorColor = Color(0xFFE3F2FD))
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Warning, "SOS") },
                        label = { Text("SOS") },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Red, indicatorColor = Color(0xFFFFEBEE))
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                val webView = rememberWebViewWithLifecycle()

                when(selectedTab) {
                    0 -> MapTab(0L)
                    1 -> InboxTab { sms ->
                        MainActivity.lat.value = sms.lat
                        MainActivity.lon.value = sms.lon
                        selectedTab = 0
                    }
                    2 -> SosAlertsScreen { sms ->
                        MainActivity.lat.value = sms.lat
                        MainActivity.lon.value = sms.lon
                        selectedTab = 0
                    }
                    3 -> OnlineWebTab(webView)
                }
            }
        }
    }

    @Composable
    fun OnlineWebTab(webView: WebView) {
        AndroidView({ webView }, modifier = Modifier.fillMaxSize())
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MapTab(reopenTrigger: Long) {
        val sheetState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
        )
        BottomSheetScaffold(
            scaffoldState = sheetState,
            sheetPeekHeight = 40.dp,
            sheetContainerColor = Color.White,
            sheetContent = { MultiSendCommandCard(targetDevices = targetDevices) }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                OfflineMapScreen(recenterTrigger = reopenTrigger)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MultiSendCommandCard(targetDevices: List<TargetDevice>) {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Select unit to send command:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            targetDevices.forEach { device ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Target: ...${device.number.takeLast(3)}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                        }
                        Button(
                            onClick = { sendLocationCommandSms(context, device.number, locationCommand) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(locationCommand, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    fun InboxTab(onMessageLocate: (SmsEntity) -> Unit) {
        var smsList by remember { mutableStateOf(listOf<SmsEntity>()) }
        var openedSender by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Selection States
        val selectedSenders = remember { mutableStateListOf<String>() }
        val selectedMessages = remember { mutableStateListOf<SmsEntity>() }

        fun loadData() {
            scope.launch { smsList = getFilteredSmsList(context) }
        }

        LaunchedEffect(Unit) { loadData() }

        val groupedSms = remember(smsList) { smsList.groupBy { it.sender } }

        BackHandler(enabled = openedSender != null || selectedSenders.isNotEmpty() || selectedMessages.isNotEmpty()) {
            if (selectedMessages.isNotEmpty()) selectedMessages.clear()
            else if (openedSender != null) openedSender = null
            else if (selectedSenders.isNotEmpty()) selectedSenders.clear()
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header Logic
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (openedSender != null) {
                    // MESSAGE LEVEL HEADER
                    if (selectedMessages.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedMessages.clear() }) { Icon(Icons.Default.Clear, "Clear") }
                            Text("${selectedMessages.size} Selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Row {
                            IconButton(onClick = {
                                val currentSenderMsgs = groupedSms[openedSender] ?: emptyList()
                                if (selectedMessages.containsAll(currentSenderMsgs)) selectedMessages.clear()
                                else { selectedMessages.clear(); selectedMessages.addAll(currentSenderMsgs) }
                            }) { Icon(Icons.Default.CheckCircle, "Select All") }
                            IconButton(onClick = {
                                val toDelete = selectedMessages.toList()
                                scope.launch(Dispatchers.IO) {
                                    SmsDatabase.getDatabase(context).smsDao().deleteMessages(toDelete)
                                    withContext(Dispatchers.Main) {
                                        selectedMessages.clear()
                                        loadData()
                                    }
                                }
                            }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openedSender = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                            Text(openedSender ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // SENDER LEVEL HEADER
                    if (selectedSenders.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedSenders.clear() }) { Icon(Icons.Default.Clear, "Clear") }
                            Text("${selectedSenders.size} Selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Row {
                            IconButton(onClick = {
                                val allSenders = groupedSms.keys.toList()
                                if (selectedSenders.containsAll(allSenders)) selectedSenders.clear()
                                else { selectedSenders.clear(); selectedSenders.addAll(allSenders) }
                            }) { Icon(Icons.Default.CheckCircle, "Select All") }
                            IconButton(onClick = {
                                val sendersToDelete = selectedSenders.toList()
                                scope.launch(Dispatchers.IO) {
                                    val msgsToDelete = smsList.filter { it.sender in sendersToDelete }
                                    SmsDatabase.getDatabase(context).smsDao().deleteMessages(msgsToDelete)
                                    withContext(Dispatchers.Main) {
                                        selectedSenders.clear()
                                        loadData()
                                    }
                                }
                            }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                        }
                    } else {
                        Text("Inbox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyColumn {
                if (openedSender == null) {
                    val senders = groupedSms.keys.toList()
                    items(senders, key = { it }) { sender ->
                        val messages = groupedSms[sender] ?: emptyList()
                        val isSelected = selectedSenders.contains(sender)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                                onClick = {
                                    if (selectedSenders.isNotEmpty()) {
                                        if (isSelected) selectedSenders.remove(sender) else selectedSenders.add(sender)
                                    } else {
                                        openedSender = sender
                                    }
                                },
                                onLongClick = {
                                    if (isSelected) selectedSenders.remove(sender) else selectedSenders.add(sender)
                                }
                            ),
                            colors = CardDefaults.cardColors(containerColor = if(isSelected) Color(0xFFBBDEFB) else MaterialTheme.colorScheme.surfaceVariant),
                            border = if(isSelected) BorderStroke(2.dp, Color(0xFF1976D2)) else null
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = Color(0xFF1976D2))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(sender, fontWeight  = FontWeight.Bold, fontSize = 18.sp)
                                    Text("${messages.size} Messages", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFF1976D2))
                            }
                        }
                    }
                } else {
                    val messages = groupedSms[openedSender] ?: emptyList()
                    items(messages, key = { it.id }) { sms ->
                        val isSelected = selectedMessages.contains(sms)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                                onClick = {
                                    if (selectedMessages.isNotEmpty()) {
                                        if (isSelected) selectedMessages.remove(sms) else selectedMessages.add(sms)
                                    } else {
                                        onMessageLocate(sms)
                                    }
                                },
                                onLongClick = {
                                    if (isSelected) selectedMessages.remove(sms) else selectedMessages.add(sms)
                                }
                            ),
                            colors = CardDefaults.cardColors(containerColor = if(isSelected) Color(0xFFBBDEFB) else Color.White),
                            border = if(isSelected) BorderStroke(2.dp, Color(0xFF1976D2)) else BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Email, null, tint = Color(0xFF1976D2))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(sms.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    Spacer(modifier = Modifier.weight(1f))
                                    if(isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFF1976D2))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(sms.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SosAlertsScreen(onMessageLocate: (SmsEntity) -> Unit) {
        var sosList by remember { mutableStateOf(listOf<SmsEntity>()) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val selectedSos = remember { mutableStateListOf<SmsEntity>() }

        fun loadData() {
            scope.launch { sosList = getFilteredSmsList(context, isSosFilter = true) }
        }

        LaunchedEffect(Unit) { loadData() }

        BackHandler(enabled = selectedSos.isNotEmpty()) { selectedSos.clear() }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedSos.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedSos.clear() }) { Icon(Icons.Default.Clear, "Clear") }
                        Text("${selectedSos.size} Selected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Red)
                    }
                    Row {
                        IconButton(onClick = {
                            if (selectedSos.containsAll(sosList)) selectedSos.clear()
                            else { selectedSos.clear(); selectedSos.addAll(sosList) }
                        }) { Icon(Icons.Default.CheckCircle, "Select All", tint = Color.Red) }
                        IconButton(onClick = {
                            val toDelete = selectedSos.toList()
                            scope.launch(Dispatchers.IO) {
                                SmsDatabase.getDatabase(context).smsDao().deleteMessages(toDelete)
                                withContext(Dispatchers.Main) {
                                    selectedSos.clear()
                                    loadData()
                                }
                            }
                        }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                    }
                } else {
                    Column {
                        Text("🚨 Emergency Alerts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.Red)
                        Text("Critical messages received", color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (sosList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No SOS messages found.", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(sosList) { sms ->
                        val isSelected = selectedSos.contains(sms)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).combinedClickable(
                                onClick = {
                                    if (selectedSos.isNotEmpty()) {
                                        if (isSelected) selectedSos.remove(sms) else selectedSos.add(sms)
                                    } else {
                                        onMessageLocate(sms)
                                    }
                                },
                                onLongClick = {
                                    if (isSelected) selectedSos.remove(sms) else selectedSos.add(sms)
                                }
                            ),
                            colors = CardDefaults.cardColors(containerColor = if(isSelected) Color(0xFFFFCDD2) else Color(0xFFFFEBEE)),
                            border = BorderStroke(if(isSelected) 3.dp else 1.dp, Color.Red)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = Color.Red)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(sms.sender, fontWeight = FontWeight.Bold, color = Color.Red)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(sms.time, style = MaterialTheme.typography.labelSmall)
                                    if(isSelected) Icon(Icons.Default.Check, null, tint = Color.Red)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(sms.message, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row {
                                    Text("Loc: ${sms.lat}, ${sms.lon}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun rememberWebViewWithLifecycle(): WebView {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            webViewClient = object : WebViewClient() {
                 override fun onPageFinished(view: WebView?, url: String?) {
                    Toast.makeText(context, "Web page loaded", Toast.LENGTH_SHORT).show()
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    val errorMsg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        "${error?.errorCode}: ${error?.description}"
                    } else { "WebView Error" }
                    Toast.makeText(context, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
            loadUrl("https://mmtt-frontend.vercel.app/")
        }
    }
    return webView
}



data class TargetDevice(val name: String, val number: String, val id: String)