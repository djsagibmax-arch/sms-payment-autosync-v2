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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.SmsParser
import com.example.ui.theme.*

data class SmsPreset(
    val name: String,
    val method: String,
    val sender: String,
    val body: String,
    val color: Color
)

@Composable
fun SimulatorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val presets = remember {
        listOf(
            SmsPreset(
                name = "bKash Cash In",
                method = "bKash",
                sender = "bKash",
                body = "Cash In Tk 2,500.00 from 01712345678 successful. Fee Tk 0.00. Balance Tk 8,430.50. TrxID 9K8L7M6N5P at 15/08/2026 14:30",
                color = BkashPink
            ),
            SmsPreset(
                name = "bKash Payment Received",
                method = "bKash",
                sender = "bKash",
                body = "You have received payment Tk 1,200.00 from 01898765432. Ref Invoice42. Fee Tk 0.00. Balance Tk 9,630.50. TrxID BL98765432 at 15/08/2026 15:10",
                color = BkashPink
            ),
            SmsPreset(
                name = "Nagad Money Received",
                method = "Nagad",
                sender = "NAGAD",
                body = "Money Received. Amount: Tk 3,000.00. Sender: 01911223344. TxnID: 7XYZ89AB. Date: 15/08/2026 16:10. Balance: Tk 12,450.00",
                color = NagadOrange
            ),
            SmsPreset(
                name = "Rocket Cash In",
                method = "Rocket",
                sender = "16216",
                body = "Cash In from A/C: 017123456789. Tk 4,500.00. TxnId: 9876543210. Balance: Tk 18,000.00. 15/08/2026 16:45",
                color = RocketPurple
            ),
            SmsPreset(
                name = "Upay Money Received",
                method = "Upay",
                sender = "Upay",
                body = "Received Tk 750.00 from 01511223344. TrxID: UP88990011. Balance: Tk 2,150.00 at 15/08/2026",
                color = UpayNavy
            ),
            SmsPreset(
                name = "Cellfin Transfer",
                method = "Cellfin",
                sender = "Cellfin",
                body = "Received Tk. 6,000.00 from Cellfin A/C 01799887766. Trx ID: CF554433. Available Bal: BDT 35,000.00",
                color = CellfinGreen
            )
        )
    }

    var selectedSender by remember { mutableStateOf(presets[0].sender) }
    var selectedBody by remember { mutableStateOf(presets[0].body) }

    val liveParsed = remember(selectedSender, selectedBody) {
        SmsParser.parseSms(selectedSender, selectedBody)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info Banner
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
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "SMS Payment Sandbox & Simulator",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Simulate realistic Bangladeshi MFS SMS messages to test regex parsing and forward payloads directly to your API/DB.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 1-Tap Presets
        item {
            Text(
                text = "1-Tap Preset MFS Messages",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            OutlinedCard(
                                onClick = {
                                    // Generate dynamic TrxID for variety
                                    val randomSuffix = (1000..9999).random()
                                    val updatedBody = preset.body.replace(
                                        Regex("""(?:TrxID|TxnID|TxnId|Trx ID)[:\s]+([A-Za-z0-9]+)"""),
                                        "TrxID ${preset.method.take(2).uppercase()}$randomSuffix"
                                    )
                                    selectedSender = preset.sender
                                    selectedBody = updatedBody
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (selectedSender == preset.sender && selectedBody.startsWith(preset.body.take(20))) {
                                        preset.color.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(preset.color)
                                    )
                                    Text(
                                        text = preset.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom SMS Editor Box
        item {
            Text(
                text = "Simulation Payload Editor",
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
                    OutlinedTextField(
                        value = selectedSender,
                        onValueChange = { selectedSender = it },
                        label = { Text("Sender Phone / ID") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = selectedBody,
                        onValueChange = { selectedBody = it },
                        label = { Text("SMS Body Text") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5
                    )

                    // Extracted Data Preview
                    if (liveParsed != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "LIVE PARSER INSPECTION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryIndigo
                                )
                                Text(
                                    text = "• Method: ${liveParsed.method}\n• TrxID: ${liveParsed.trxId}\n• Amount: ৳${String.format(java.util.Locale.US, "%,.2f", liveParsed.amount)}\n• Sender Phone: ${liveParsed.senderPhone ?: "N/A"}\n• Type: ${liveParsed.transactionType}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fire Button
        item {
            Button(
                onClick = {
                    viewModel.simulateIncomingSms(context, selectedSender, selectedBody)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fire Simulated SMS & AutoSync",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
