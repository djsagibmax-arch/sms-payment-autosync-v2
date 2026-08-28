package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SyncConfig
import com.example.parser.SmsParser
import com.example.ui.theme.*

@Composable
fun WalletsScreen(
    viewModel: MainViewModel,
    config: SyncConfig,
    modifier: Modifier = Modifier
) {
    var bkashEnabled by remember(config) { mutableStateOf(config.bkashEnabled) }
    var nagadEnabled by remember(config) { mutableStateOf(config.nagadEnabled) }
    var rocketEnabled by remember(config) { mutableStateOf(config.rocketEnabled) }
    var upayEnabled by remember(config) { mutableStateOf(config.upayEnabled) }
    var cellfinEnabled by remember(config) { mutableStateOf(config.cellfinEnabled) }
    var customKeywords by remember(config) { mutableStateOf(config.customKeywords) }
    var onlyReceivedAndCashIn by remember(config) { mutableStateOf(config.onlyReceivedAndCashIn) }

    // Live Regex Tester State
    var testSmsSender by remember { mutableStateOf("bKash") }
    var testSmsInput by remember {
        mutableStateOf("You have received Tk 1,500.00 from 01712345678. Ref 100. Fee Tk 0.00. Balance Tk 5,230.50. TrxID 9K8L7M6N5P at 15/08/2026 14:30")
    }

    val parsedPreview = remember(testSmsSender, testSmsInput) {
        SmsParser.parseSms(testSmsSender, testSmsInput)
    }

    val isSenderActive = remember(testSmsSender, testSmsInput, bkashEnabled, nagadEnabled, rocketEnabled, upayEnabled, cellfinEnabled, customKeywords) {
        SmsParser.isSenderAllowed(
            senderId = testSmsSender,
            body = testSmsInput,
            bkashEnabled = bkashEnabled,
            nagadEnabled = nagadEnabled,
            rocketEnabled = rocketEnabled,
            upayEnabled = upayEnabled,
            cellfinEnabled = cellfinEnabled,
            customKeywords = customKeywords
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "MFS Wallet Filters & Regex Rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Toggle target wallets and configure custom sender IDs or keyword filters.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Dedicated Wallet Switches
        item {
            Text(
                text = "Supported Mobile Wallets",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    WalletToggleRow(
                        name = "bKash",
                        tagline = "Sender: bKash, 16247 • All formats",
                        color = BkashPink,
                        checked = bkashEnabled,
                        onCheckedChange = { bkashEnabled = it }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    WalletToggleRow(
                        name = "Nagad",
                        tagline = "Sender: NAGAD, 16167 • Cash In & Received",
                        color = NagadOrange,
                        checked = nagadEnabled,
                        onCheckedChange = { nagadEnabled = it }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    WalletToggleRow(
                        name = "Rocket (DBBL)",
                        tagline = "Sender: 16216, Rocket, DBBL",
                        color = RocketPurple,
                        checked = rocketEnabled,
                        onCheckedChange = { rocketEnabled = it }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    WalletToggleRow(
                        name = "Upay (UCB)",
                        tagline = "Sender: Upay, 16268",
                        color = UpayNavy,
                        checked = upayEnabled,
                        onCheckedChange = { upayEnabled = it }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    WalletToggleRow(
                        name = "Cellfin (IBBL)",
                        tagline = "Sender: Cellfin, IBBL, Islami Bank",
                        color = CellfinGreen,
                        checked = cellfinEnabled,
                        onCheckedChange = { cellfinEnabled = it }
                    )
                }
            }
        }

        // Custom Senders & Keyword Whitelist
        item {
            Text(
                text = "Custom Sender Keywords & Senders",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Comma-separated sender IDs or keyword filters. Any incoming SMS containing these keywords will be captured & parsed.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customKeywords,
                        onValueChange = { customKeywords = it },
                        label = { Text("Sender Keywords / Shortcodes") },
                        placeholder = { Text("e.g. 16247, 16216, NAGAD, bKash, 16268, MY_BANK") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Tag, contentDescription = null)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Only Incoming Money & Cash In",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Ignore outgoing Send Money, Merchant checkouts, or promo SMS",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = onlyReceivedAndCashIn,
                            onCheckedChange = { onlyReceivedAndCashIn = it }
                        )
                    }
                }
            }
        }

        // Live Regex Rule Inspector
        item {
            Text(
                text = "Dynamic Regex Parser Live Sandbox",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Test extraction of TrxID, Amount, and Sender from any real SMS string in real time.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = testSmsSender,
                        onValueChange = { testSmsSender = it },
                        label = { Text("Sender ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = testSmsInput,
                        onValueChange = { testSmsInput = it },
                        label = { Text("SMS Message Body") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5
                    )

                    // Parser Results Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE PARSER OUTPUT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSenderActive) StatusSuccess.copy(alpha = 0.2f) else StatusFailed.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isSenderActive) "Sender Allowed" else "Sender Blocked",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSenderActive) StatusSuccess else StatusFailed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (parsedPreview != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Detected Method:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = parsedPreview.method, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Transaction ID:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = parsedPreview.trxId, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusSuccess)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Extracted Amount:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "৳ ${String.format(java.util.Locale.US, "%,.2f", parsedPreview.amount)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (!parsedPreview.senderPhone.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Sender Phone:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = parsedPreview.senderPhone, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (!parsedPreview.reference.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Reference:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = parsedPreview.reference, fontSize = 12.sp)
                                    }
                                }
                                if (parsedPreview.balance != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "Remaining Balance:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = "৳ ${String.format(java.util.Locale.US, "%,.2f", parsedPreview.balance)}", fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Text(
                                    text = "⚠ Unable to extract TrxID or Amount. Ensure the SMS contains keywords like 'TrxID' and 'Tk/BDT'.",
                                    fontSize = 11.sp,
                                    color = StatusFailed
                                )
                            }
                        }
                    }
                }
            }
        }

        // Save Settings Button
        item {
            Button(
                onClick = {
                    val updated = config.copy(
                        bkashEnabled = bkashEnabled,
                        nagadEnabled = nagadEnabled,
                        rocketEnabled = rocketEnabled,
                        upayEnabled = upayEnabled,
                        cellfinEnabled = cellfinEnabled,
                        customKeywords = customKeywords,
                        onlyReceivedAndCashIn = onlyReceivedAndCashIn
                    )
                    viewModel.updateConfig(updated)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save Wallet Configuration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun WalletToggleRow(
    name: String,
    tagline: String,
    color: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = if (checked) 0.2f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1),
                    fontWeight = FontWeight.Bold,
                    color = if (checked) color else Color.Gray,
                    fontSize = 16.sp
                )
            }
            Column {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = tagline,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = color
            )
        )
    }
}
