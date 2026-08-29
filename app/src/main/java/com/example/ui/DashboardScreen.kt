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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncLogEntity
import com.example.model.ConnectionMode
import com.example.model.SyncConfig
import com.example.ui.theme.*
import com.example.util.KeepAliveHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    stats: DashboardStats,
    isServiceRunning: Boolean,
    config: SyncConfig,
    recentLogs: List<SyncLogEntity>,
    onNavigateToWallets: () -> Unit,
    onNavigateToConnection: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isBatteryIgnored by remember {
        mutableStateOf(KeepAliveHelper.isBatteryOptimizationIgnored(context))
    }
    var showKeepAliveGuide by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Service Status Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceRunning) PrimaryIndigoDark else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isServiceRunning) StatusSuccess.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isServiceRunning) Icons.Default.Sync else Icons.Default.SyncDisabled,
                                contentDescription = null,
                                tint = if (isServiceRunning) StatusSuccess else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isServiceRunning) "AutoSync Active" else "AutoSync Idle",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isServiceRunning) StatusSuccess else StatusFailed)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isServiceRunning) "Listening for SMS from enabled MFS wallets" else "Tap start to enable background sync listener",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { viewModel.toggleService(context) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isServiceRunning) StatusFailed.copy(alpha = 0.9f) else StatusSuccess,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isServiceRunning) "Stop" else "Start",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 24/7 Background Keep-Alive & Anti-Kill Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBatteryIgnored) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else StatusFailed.copy(alpha = 0.1f)
                ),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isBatteryIgnored) Icons.Default.Shield else Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = if (isBatteryIgnored) StatusSuccess else StatusFailed,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "24/7 Background Protection",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBatteryIgnored) StatusSuccess.copy(alpha = 0.15f) else StatusFailed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isBatteryIgnored) "Unrestricted ✓" else "Action Required ⚠",
                                color = if (isBatteryIgnored) StatusSuccess else StatusFailed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isBatteryIgnored) {
                            "Battery optimization is disabled. The app can receive SMS and sync payments even when the phone screen is locked or turned off."
                        } else {
                            "To keep this app alive 24/7 when closed from recent apps or screen is off, disable battery optimization and enable Auto-start."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isBatteryIgnored) {
                            Button(
                                onClick = {
                                    KeepAliveHelper.requestIgnoreBatteryOptimization(context)
                                    isBatteryIgnored = KeepAliveHelper.isBatteryOptimizationIgnored(context)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Disable Battery Saver", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                KeepAliveHelper.openAutoStartSettings(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.AppSettingsAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Start / App Info", fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = { showKeepAliveGuide = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Help Guide", tint = PrimaryIndigo)
                        }
                    }
                }
            }
        }

        // Active Connection Target Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToConnection() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryIndigo.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (config.connectionMode) {
                                    ConnectionMode.SUPABASE -> Icons.Default.Storage
                                    ConnectionMode.CUSTOM_API -> Icons.Default.Http
                                    ConnectionMode.DIRECT_WEBHOOK -> Icons.Default.Webhook
                                },
                                contentDescription = null,
                                tint = PrimaryIndigo
                            )
                        }

                        Column {
                            Text(
                                text = "Target: ${config.connectionMode.name.replace("_", " ")}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = when (config.connectionMode) {
                                    ConnectionMode.SUPABASE -> if (config.supabaseUrl.isBlank()) "Supabase not configured" else config.supabaseUrl
                                    ConnectionMode.CUSTOM_API -> if (config.customEndpointUrl.isBlank()) "Endpoint URL not set" else config.customEndpointUrl
                                    ConnectionMode.DIRECT_WEBHOOK -> if (config.webhookUrl.isBlank()) "Webhook not set" else config.webhookUrl
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Configure",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 4 KPI Metrics Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Total Synced",
                        value = "৳ ${String.format(Locale.US, "%,.0f", stats.totalAmount)}",
                        subtitle = "${stats.successCount} transactions",
                        icon = Icons.Default.AccountBalanceWallet,
                        iconTint = StatusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Success Rate",
                        value = "${stats.successRate}%",
                        subtitle = "${stats.totalCount} total processed",
                        icon = Icons.Default.CheckCircle,
                        iconTint = PrimaryIndigoLight,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Successful",
                        value = "${stats.successCount}",
                        subtitle = "Forwarded to server",
                        icon = Icons.Default.CloudDone,
                        iconTint = SecondaryTeal,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Failed / Retry",
                        value = "${stats.failedCount}",
                        subtitle = "Needs inspection",
                        icon = Icons.Default.ErrorOutline,
                        iconTint = if (stats.failedCount > 0) StatusFailed else Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigateToSimulator() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(imageVector = Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SMS Simulator", fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Test API Sync", fontSize = 13.sp)
                }
            }
        }

        // Active Wallets Summary Pill Row
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToWallets() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monitored MFS Wallets",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Manage →",
                            fontSize = 12.sp,
                            color = PrimaryIndigo,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WalletPill(name = "bKash", color = BkashPink, enabled = config.bkashEnabled)
                        WalletPill(name = "Nagad", color = NagadOrange, enabled = config.nagadEnabled)
                        WalletPill(name = "Rocket", color = RocketPurple, enabled = config.rocketEnabled)
                        WalletPill(name = "Upay", color = UpayNavy, enabled = config.upayEnabled)
                        WalletPill(name = "Cellfin", color = CellfinGreen, enabled = config.cellfinEnabled)
                    }
                }
            }
        }

        // Recent Activity Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onNavigateToLogs) {
                    Text("View All Logs", fontSize = 13.sp)
                }
            }
        }

        if (recentLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No payment SMS recorded yet",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Incoming SMS or Simulator test events will appear here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(recentLogs) { log ->
                RecentLogCard(log = log, onClick = { viewModel.selectLog(log) })
            }
        }
    }

    if (showKeepAliveGuide) {
        AlertDialog(
            onDismissRequest = { showKeepAliveGuide = false },
            icon = {
                Icon(Icons.Default.Shield, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    text = "অ্যাপটি ২৪ ঘণ্টা চালু রাখার নিয়ম",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "মোবাইল স্ক্রিন বন্ধ থাকলেও বা রিসেন্ট অ্যাপস থেকে ক্লিয়ার করলেও অ্যাপ যাতে বন্ধ না হয়, সেজন্য নিচের সেটিংসগুলো নিশ্চিত করুন:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "১. রিসেন্ট অ্যাপস-এ Lock 🔒 করুন:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PrimaryIndigo
                            )
                            Text(
                                text = "রিসেন্ট অ্যাপস (Recent Apps) স্ক্রিনে গিয়ে এই অ্যাপের উপরে থাকা Lock আইকনে চাপ দিয়ে লক করে দিন যাতে সোয়াইপ করলেও অ্যাপ বন্ধ না হয়।",
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "২. ব্যাটারি অপ্টিমাইজেশন (Unrestricted):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PrimaryIndigo
                            )
                            Text(
                                text = "ব্যাটারি সেটিংসে গিয়ে অ্যাপটিকে 'Unrestricted' বা 'Don't Optimize' নির্বাচন করুন।",
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "৩. অটো-স্টার্ট (Auto-start / iManager):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PrimaryIndigo
                            )
                            Text(
                                text = "Vivo, Xiaomi বা Oppo মোবাইলে Auto-start এবং High Background Power Consumption অন রাখুন।",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showKeepAliveGuide = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) {
                    Text("বুঝেছি")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        KeepAliveHelper.openAutoStartSettings(context)
                        showKeepAliveGuide = false
                    }
                ) {
                    Text("সেটিংস খুলুন")
                }
            }
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun WalletPill(name: String, color: Color, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) color.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f),
        border = if (enabled) null else CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (enabled) color else Color.Gray)
            )
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) color else Color.Gray
            )
        }
    }
}

@Composable
fun RecentLogCard(log: SyncLogEntity, onClick: () -> Unit) {
    val methodColor = when (log.method.uppercase()) {
        "BKASH" -> BkashPink
        "NAGAD" -> NagadOrange
        "ROCKET" -> RocketPurple
        "UPAY" -> UpayNavy
        "CELLFIN" -> CellfinGreen
        else -> PrimaryIndigo
    }

    val isSuccess = log.status == "SUCCESS"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(methodColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = log.method.take(2).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = methodColor,
                        fontSize = 14.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = log.method,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Trx: ${log.trxId}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (!log.senderPhone.isNullOrBlank()) "From: ${log.senderPhone}" else "Sender: ${log.senderId}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date(log.timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (log.amount > 0) "৳ ${String.format(Locale.US, "%,.2f", log.amount)}" else "৳ --",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isSuccess) StatusSuccess else MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSuccess) StatusSuccess.copy(alpha = 0.15f) else StatusFailed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isSuccess) "${log.responseCode} OK" else "Failed",
                        color = if (isSuccess) StatusSuccess else StatusFailed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
