package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncLogEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainViewModel,
    logs: List<SyncLogEntity>,
    selectedLog: SyncLogEntity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, searchQuery, filterStatus) {
        logs.filter { log ->
            val matchesFilter = when (filterStatus) {
                "SUCCESS" -> log.status == "SUCCESS"
                "FAILED" -> log.status == "FAILED"
                "FILTERED" -> log.status == "FILTERED_IGNORED" || log.status == "PARSED_ONLY"
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                log.trxId.contains(searchQuery, ignoreCase = true) ||
                log.senderId.contains(searchQuery, ignoreCase = true) ||
                (log.senderPhone?.contains(searchQuery, ignoreCase = true) == true) ||
                log.rawMessage.contains(searchQuery, ignoreCase = true) ||
                log.method.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar & Clear Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by TrxID, phone, sender...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                IconButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = StatusFailed
                    )
                }
            }

            // Status Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterStatus == "ALL",
                    onClick = { filterStatus = "ALL" },
                    label = { Text("All (${logs.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = filterStatus == "SUCCESS",
                    onClick = { filterStatus = "SUCCESS" },
                    label = {
                        Text(
                            "Success (${logs.count { it.status == "SUCCESS" }})",
                            fontSize = 11.sp
                        )
                    }
                )
                FilterChip(
                    selected = filterStatus == "FAILED",
                    onClick = { filterStatus = "FAILED" },
                    label = {
                        Text(
                            "Failed (${logs.count { it.status == "FAILED" }})",
                            fontSize = 11.sp
                        )
                    }
                )
                FilterChip(
                    selected = filterStatus == "FILTERED",
                    onClick = { filterStatus = "FILTERED" },
                    label = {
                        Text(
                            "Ignored (${logs.count { it.status == "FILTERED_IGNORED" || it.status == "PARSED_ONLY" }})",
                            fontSize = 11.sp
                        )
                    }
                )
            }

            // Log List
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || filterStatus != "ALL") "No matching activity logs" else "No activity logs recorded yet",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        RecentLogCard(log = log, onClick = { viewModel.selectLog(log) })
                    }
                }
            }
        }
    }

    // Detail Dialog
    if (selectedLog != null) {
        val methodColor = when (selectedLog.method.uppercase()) {
            "BKASH" -> BkashPink
            "NAGAD" -> NagadOrange
            "ROCKET" -> RocketPurple
            "UPAY" -> UpayNavy
            "CELLFIN" -> CellfinGreen
            else -> PrimaryIndigo
        }

        AlertDialog(
            onDismissRequest = { viewModel.selectLog(null) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.retrySync(context, selectedLog) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry Sync")
                    }
                    TextButton(onClick = { viewModel.selectLog(null) }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val textToCopy = "TrxID: ${selectedLog.trxId}\nAmount: ৳${selectedLog.amount}\nMethod: ${selectedLog.method}\nFrom: ${selectedLog.senderPhone ?: selectedLog.senderId}\nRaw: ${selectedLog.rawMessage}"
                        clipboardManager.setText(AnnotatedString(textToCopy))
                    }
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(methodColor)
                    )
                    Text(
                        text = "${selectedLog.method} Payment Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DetailRow("Status", selectedLog.status, if (selectedLog.status == "SUCCESS") StatusSuccess else StatusFailed)
                                DetailRow("HTTP Code", if (selectedLog.responseCode > 0) "${selectedLog.responseCode}" else "N/A", MaterialTheme.colorScheme.onSurface)
                                DetailRow("Amount", "৳ ${String.format(Locale.US, "%,.2f", selectedLog.amount)}", StatusSuccess)
                                DetailRow("Transaction ID", selectedLog.trxId, MaterialTheme.colorScheme.onSurface, isMonospace = true)
                                if (!selectedLog.senderPhone.isNullOrBlank()) {
                                    DetailRow("Sender Phone", selectedLog.senderPhone, MaterialTheme.colorScheme.onSurface)
                                }
                                if (!selectedLog.reference.isNullOrBlank()) {
                                    DetailRow("Reference", selectedLog.reference, MaterialTheme.colorScheme.onSurface)
                                }
                                if (selectedLog.balance != null) {
                                    DetailRow("Balance", "৳ ${String.format(Locale.US, "%,.2f", selectedLog.balance)}", MaterialTheme.colorScheme.onSurface)
                                }
                                DetailRow("Timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(selectedLog.timestamp)), MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (selectedLog.endpointUrl.isNotBlank()) {
                        item {
                            Text("Target Endpoint:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(selectedLog.endpointUrl, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    item {
                        Text("Original Raw SMS Message:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = selectedLog.rawMessage,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (selectedLog.responseBody.isNotBlank()) {
                        item {
                            Text("Server Response:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = selectedLog.responseBody,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(8.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    // Clear Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Logs?") },
            text = { Text("This will delete all stored SMS payment sync history from local database.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusFailed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, valueColor: Color, isMonospace: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}
