package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.parser.SmsParser
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsPreset(
    val id: String,
    val name: String,
    val method: String,
    val category: String, // "Send Money", "Cash In", "Payment"
    val sender: String,
    val idSpec: String, // "10-char Alphanumeric", "8-char Alphanumeric", "10-digit Numeric"
    val generateId: () -> String,
    val bodyTemplate: (trxId: String, dateStr: String) -> String,
    val color: Color
)

/**
 * Official bKash TrxID generator: 10 alphanumeric characters (e.g. 9K8L7M6N5P, BL7M9K2P1Q)
 */
fun generateBkashTrxId(): String {
    val charPool = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    return (1..10).map { charPool.random() }.joinToString("")
}

/**
 * Official Nagad TxnID generator: exactly 8 alphanumeric characters (e.g. 71G58D9C, 7XYZ89AB, 8KD92FA1)
 */
fun generateNagadTxnId(): String {
    val charPool = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    val startChar = listOf('7', '8', '9', 'B', 'C', 'D', 'E', 'F').random()
    val rest = (1..7).map { charPool.random() }.joinToString("")
    return "$startChar$rest"
}

/**
 * Official Rocket TxnId generator: exactly 10 digits purely numeric (e.g. 3482910482, 9876543210)
 */
fun generateRocketTxnId(): String {
    return (1..10).map { (0..9).random() }.joinToString("")
}

/**
 * Official Upay TrxID generator: 10 alphanumeric characters (e.g. UP98765432 or 2408291234)
 */
fun generateUpayTrxId(): String {
    val charPool = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    return "UP" + (1..8).map { charPool.random() }.joinToString("")
}

/**
 * Official Cellfin Trx ID generator: 10 to 12 alphanumeric characters (e.g. CF2408291048)
 */
fun generateCellfinTrxId(): String {
    val charPool = "0123456789"
    return "CF" + (1..8).map { charPool.random() }.joinToString("")
}

@Composable
fun SimulatorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    val simulationResult by viewModel.simulationResult.collectAsStateWithLifecycle()
    val isSimulating by viewModel.isSimulating.collectAsStateWithLifecycle()

    var forwardToCustomApi by remember(config) { mutableStateOf(config.forwardToCustomApi) }
    var forwardToSupabase by remember(config) { mutableStateOf(config.forwardToSupabase) }
    var forwardToWebhook by remember(config) { mutableStateOf(config.forwardToWebhook) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }
    val currentDateStr = remember { dateFormat.format(Date()) }

    val presets = remember {
        listOf(
            // bKash Presets (Official 10-char Alphanumeric)
            SmsPreset(
                id = "bkash_send_received",
                name = "bKash Send Money (Received)",
                method = "bKash",
                category = "Send Money",
                sender = "bKash",
                idSpec = "10-char (বিকাশ অফিসিয়াল)",
                generateId = { generateBkashTrxId() },
                bodyTemplate = { trxId, dateStr ->
                    "You have received Tk 1,500.00 from 01712345678. Ref Shopping. Fee Tk 0.00. Balance Tk 6,250.00. TrxID $trxId at $dateStr"
                },
                color = BkashPink
            ),
            SmsPreset(
                id = "bkash_send_sent",
                name = "bKash Send Money (Sent)",
                method = "bKash",
                category = "Send Money",
                sender = "bKash",
                idSpec = "10-char (বিকাশ অফিসিয়াল)",
                generateId = { generateBkashTrxId() },
                bodyTemplate = { trxId, dateStr ->
                    "Send Money Tk 1,000.00 to 01987654321 successful. Ref Rent. Fee Tk 5.00. Balance Tk 5,245.00. TrxID $trxId at $dateStr"
                },
                color = BkashPink
            ),
            SmsPreset(
                id = "bkash_payment_received",
                name = "bKash Payment Received",
                method = "bKash",
                category = "Payment",
                sender = "bKash",
                idSpec = "10-char (বিকাশ অফিসিয়াল)",
                generateId = { generateBkashTrxId() },
                bodyTemplate = { trxId, dateStr ->
                    "You have received payment Tk 2,500.00 from 01898765432. Ref Invoice42. Fee Tk 0.00. Balance Tk 9,630.50. TrxID $trxId at $dateStr"
                },
                color = BkashPink
            ),
            SmsPreset(
                id = "bkash_cash_in",
                name = "bKash Cash In",
                method = "bKash",
                category = "Cash In",
                sender = "bKash",
                idSpec = "10-char (বিকাশ অফিসিয়াল)",
                generateId = { generateBkashTrxId() },
                bodyTemplate = { trxId, dateStr ->
                    "Cash In Tk 2,500.00 from 01712345678 successful. Fee Tk 0.00. Balance Tk 8,430.50. TrxID $trxId at $dateStr"
                },
                color = BkashPink
            ),

            // Nagad Presets (Official 8-char Alphanumeric TxnID)
            SmsPreset(
                id = "nagad_send_received",
                name = "Nagad Send Money (Received)",
                method = "Nagad",
                category = "Send Money",
                sender = "NAGAD",
                idSpec = "8-char (নগদ অফিসিয়াল)",
                generateId = { generateNagadTxnId() },
                bodyTemplate = { trxId, dateStr ->
                    "Money Received. Amount: Tk 2,000.00. Sender: 01799887766. TxnID: $trxId. Date: $dateStr. Balance: Tk 12,450.00"
                },
                color = NagadOrange
            ),
            SmsPreset(
                id = "nagad_send_sent",
                name = "Nagad Send Money (Sent)",
                method = "Nagad",
                category = "Send Money",
                sender = "NAGAD",
                idSpec = "8-char (নগদ অফিসিয়াল)",
                generateId = { generateNagadTxnId() },
                bodyTemplate = { trxId, dateStr ->
                    "Send Money successful. Amount: Tk 800.00. Receiver: 01611223344. TxnID: $trxId. Date: $dateStr. Balance: Tk 11,650.00"
                },
                color = NagadOrange
            ),
            SmsPreset(
                id = "nagad_payment",
                name = "Nagad Merchant Payment",
                method = "Nagad",
                category = "Payment",
                sender = "NAGAD",
                idSpec = "8-char (নগদ অফিসিয়াল)",
                generateId = { generateNagadTxnId() },
                bodyTemplate = { trxId, dateStr ->
                    "Payment received. Amount: Tk 1,500.00. Customer: 01922334455. TxnID: $trxId. Date: $dateStr. Balance: Tk 13,950.00"
                },
                color = NagadOrange
            ),
            SmsPreset(
                id = "nagad_cash_in",
                name = "Nagad Cash In",
                method = "Nagad",
                category = "Cash In",
                sender = "NAGAD",
                idSpec = "8-char (নগদ অফিসিয়াল)",
                generateId = { generateNagadTxnId() },
                bodyTemplate = { trxId, dateStr ->
                    "Cash In. Amount: Tk 3,500.00. Sender: 01711223344. TxnID: $trxId. Date: $dateStr. Balance: Tk 17,450.00"
                },
                color = NagadOrange
            ),

            // Rocket Presets (Official 10-digit Numeric TxnId)
            SmsPreset(
                id = "rocket_send_received",
                name = "Rocket Send Money (Received)",
                method = "Rocket",
                category = "Send Money",
                sender = "16216",
                idSpec = "10-digit (রকেট অফিসিয়াল)",
                generateId = { generateRocketTxnId() },
                bodyTemplate = { trxId, dateStr ->
                    "Received Tk 1,800.00 from A/C: 018123456789. TxnId: $trxId. Balance: Tk 15,245.00. $dateStr"
                },
                color = RocketPurple
            ),
            SmsPreset(
                id = "rocket_send_sent",
                name = "Rocket Send Money (Sent)",
                method = "Rocket",
                category = "Send Money",
                sender = "16216",
                idSpec = "10-digit (রকেট অফিসিয়াল)",
                generateId = { generateRocketTxnId() },
                bodyTemplate = { trxId, dateStr ->
                    "Send Money to A/C: 017554433221. Tk 500.00. Fee Tk 4.50. TxnId: $trxId. Balance: Tk 14,740.50. $dateStr"
                },
                color = RocketPurple
            ),
            SmsPreset(
                id = "rocket_cash_in",
                name = "Rocket Cash In",
                method = "Rocket",
                category = "Cash In",
                sender = "16216",
                idSpec = "10-digit (রকেট অফিসিয়াল)",
                generateId = { generateRocketTxnId() },
                bodyTemplate = { trxId, dateStr ->
                    "Cash In from A/C: 017123456789. Tk 4,500.00. TxnId: $trxId. Balance: Tk 19,240.50. $dateStr"
                },
                color = RocketPurple
            ),

            // Upay Presets (Official 10-char Alphanumeric TrxID)
            SmsPreset(
                id = "upay_send_received",
                name = "Upay Send Money (Received)",
                method = "Upay",
                category = "Send Money",
                sender = "Upay",
                idSpec = "10-char (উপায় অফিসিয়াল)",
                generateId = { generateUpayTrxId() },
                bodyTemplate = { trxId, dateStr ->
                    "Received Tk 1,200.00 from 01511223344. TrxID: $trxId. Balance: Tk 3,350.00 at $dateStr"
                },
                color = UpayNavy
            ),
            SmsPreset(
                id = "upay_send_sent",
                name = "Upay Send Money (Sent)",
                method = "Upay",
                category = "Send Money",
                sender = "Upay",
                idSpec = "10-char (উপায় অফিসিয়াল)",
                generateId = { generateUpayTrxId() },
                bodyTemplate = { trxId, dateStr ->
                    "Send Money Tk 600.00 to 01711223344 successful. TrxID: $trxId. Balance: Tk 2,750.00 at $dateStr"
                },
                color = UpayNavy
            ),

            // Cellfin Presets (Official 10-char Trx ID)
            SmsPreset(
                id = "cellfin_transfer_received",
                name = "Cellfin Transfer (Received)",
                method = "Cellfin",
                category = "Send Money",
                sender = "Cellfin",
                idSpec = "10-char (সেলফিন অফিসিয়াল)",
                generateId = { generateCellfinTrxId() },
                bodyTemplate = { trxId, _ ->
                    "Received Tk. 6,000.00 from Cellfin A/C 01799887766. Trx ID: $trxId. Available Bal: BDT 35,000.00"
                },
                color = CellfinGreen
            ),
            SmsPreset(
                id = "cellfin_transfer_sent",
                name = "Cellfin Fund Transfer (Sent)",
                method = "Cellfin",
                category = "Send Money",
                sender = "Cellfin",
                idSpec = "10-char (সেলফিন অফিসিয়াল)",
                generateId = { generateCellfinTrxId() },
                bodyTemplate = { trxId, _ ->
                    "Fund transfer Tk 2,500.00 to A/C 01888990011 successful. Trx ID: $trxId. Available Bal: BDT 32,500.00"
                },
                color = CellfinGreen
            )
        )
    }

    var selectedWalletFilter by remember { mutableStateOf("All") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val filteredPresets = remember(selectedWalletFilter, selectedCategoryFilter) {
        presets.filter { preset ->
            (selectedWalletFilter == "All" || preset.method.equals(selectedWalletFilter, ignoreCase = true)) &&
            (selectedCategoryFilter == "All" || preset.category.equals(selectedCategoryFilter, ignoreCase = true))
        }
    }

    // Default to bKash Send Money Received
    var selectedSender by remember { mutableStateOf(presets[0].sender) }
    var selectedBody by remember {
        mutableStateOf(presets[0].bodyTemplate(presets[0].generateId(), currentDateStr))
    }

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
        // Info Banner with official specs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "অফিসিয়াল ট্রানজেকশন কোড ও সেন্ড মানি সিমুলেটর",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "বিকাশ (১০ অক্ষর) • নগদ (৮ অক্ষর) • রকেট (১০ সংখ্যা) • উপায় ও সেলফিন",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("বিকাশ: ১০ অক্ষর", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BkashPink)
                            Text("নগদ: ৮ অক্ষর", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NagadOrange)
                            Text("রকেট: ১০ সংখ্যা", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RocketPurple)
                        }
                    }
                }
            }
        }

        // Wallet & Category Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ফিল্টার নির্বাচন করুন",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Category Filter (All vs Send Money vs Payment vs Cash In)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val categories = listOf("All", "Send Money", "Payment", "Cash In")
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(if (cat == "Send Money") "Send Money (সেন্ড মানি)" else cat, fontSize = 12.sp, fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (cat == "Send Money") {
                                { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }

                // Wallet Name Filter
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val wallets = listOf("All", "bKash", "Nagad", "Rocket", "Upay", "Cellfin")
                    items(wallets) { wallet ->
                        FilterChip(
                            selected = selectedWalletFilter == wallet,
                            onClick = { selectedWalletFilter = wallet },
                            label = { Text(wallet, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // 1-Tap Presets Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredPresets.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val newTrxId = preset.generateId()

                            OutlinedCard(
                                onClick = {
                                    selectedSender = preset.sender
                                    selectedBody = preset.bodyTemplate(newTrxId, dateFormat.format(Date()))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = if (selectedSender == preset.sender && selectedBody.contains(preset.method, ignoreCase = true)) {
                                        preset.color.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(preset.color)
                                    )
                                    Column {
                                        Text(
                                            text = preset.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = preset.idSpec,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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
                text = "SMS Payload Editor",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sender & SMS Body",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        TextButton(
                            onClick = {
                                // Generate a new ID according to detected wallet
                                val method = SmsParser.detectPaymentMethod(selectedSender, selectedBody)
                                val newId = when (method) {
                                    "bKash" -> generateBkashTrxId()
                                    "Nagad" -> generateNagadTxnId()
                                    "Rocket" -> generateRocketTxnId()
                                    "Upay" -> generateUpayTrxId()
                                    else -> generateCellfinTrxId()
                                }
                                val idKey = if (method == "Rocket") "TxnId" else if (method == "Nagad") "TxnID" else "TrxID"

                                selectedBody = selectedBody.replace(
                                    Regex("""(?:TrxID|TxnID|TxnId|Trx ID|ID)[:\s]+([A-Za-z0-9_]+)"""),
                                    "$idKey $newId"
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Official ID", fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = selectedSender,
                        onValueChange = { selectedSender = it },
                        label = { Text("Sender Phone / ID (e.g., bKash, NAGAD, 16216)") },
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

                    // Extracted Data Preview & Live Validation
                    if (liveParsed != null) {
                        val officialSpecText = when (liveParsed.method) {
                            "bKash" -> "বিকাশ অফিসিয়াল (১০ অক্ষর)"
                            "Nagad" -> "নগদ অফিসিয়াল (৮ অক্ষর)"
                            "Rocket" -> "রকেট অফিসিয়াল (১০ সংখ্যা)"
                            "Upay" -> "উপায় অফিসিয়াল (১০ অক্ষর)"
                            "Cellfin" -> "সেলফিন অফিসিয়াল (১০ অক্ষর)"
                            else -> "MFS Format"
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusSuccess.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "PARSER SUCCESSFUL",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StatusSuccess
                                        )
                                    }
                                    Text(
                                        text = "${liveParsed.trxId.length} Chars ($officialSpecText)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusSuccess
                                    )
                                }
                                Text(
                                    text = "• Method: ${liveParsed.method}\n• TrxID: ${liveParsed.trxId} [${liveParsed.trxId.length} digit/char]\n• Amount: ৳${String.format(Locale.US, "%,.2f", liveParsed.amount)}\n• Type: ${liveParsed.transactionType}\n• Sender Phone: ${liveParsed.senderPhone ?: "N/A"}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusFailed.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusFailed, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "এসএমএস ফরম্যাটে TrxID বা Amount পাওয়া যায়নি",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusFailed
                                    )
                                }
                                Text(
                                    text = "এসএমএস-এ অবশ্যই টাকার পরিমাণ (যেমন: Tk 500) এবং TrxID থাকতে হবে। উপরে থাকা যেকোনো ১টি প্রিসেটে ট্যাপ করুন:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        selectedSender = "bKash"
                                        selectedBody = "You have received Tk 1,500.00 from 01712345678. Ref SendMoney. Fee Tk 0.00. Balance Tk 6,250.00. TrxID ${generateBkashTrxId()} at ${dateFormat.format(Date())}"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Load Valid Send Money SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Forwarding Destinations Control Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Forward Target Destinations:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Active endpoints",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = forwardToCustomApi,
                            onClick = {
                                forwardToCustomApi = !forwardToCustomApi
                                viewModel.updateConfig(config.copy(forwardToCustomApi = forwardToCustomApi))
                            },
                            label = { Text("Render API", fontSize = 12.sp) },
                            leadingIcon = {
                                if (forwardToCustomApi) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )

                        FilterChip(
                            selected = forwardToSupabase,
                            onClick = {
                                forwardToSupabase = !forwardToSupabase
                                viewModel.updateConfig(config.copy(forwardToSupabase = forwardToSupabase))
                            },
                            label = { Text("Supabase DB", fontSize = 12.sp) },
                            leadingIcon = {
                                if (forwardToSupabase) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )

                        FilterChip(
                            selected = forwardToWebhook,
                            onClick = {
                                forwardToWebhook = !forwardToWebhook
                                viewModel.updateConfig(config.copy(forwardToWebhook = forwardToWebhook))
                            },
                            label = { Text("Webhook", fontSize = 12.sp) },
                            leadingIcon = {
                                if (forwardToWebhook) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }

                    if (forwardToCustomApi) {
                        Text(
                            text = "• Render Target: ${config.customEndpointUrl.ifEmpty { "(No Render URL set in Connection tab)" }}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (config.customEndpointUrl.isBlank() || config.customEndpointUrl.contains("placeholder")) StatusFailed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (forwardToSupabase) {
                        Text(
                            text = "• Supabase Table: ${config.supabaseTable} (${config.supabaseUrl.take(30)}...)",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Live Simulation Result Card
        if (simulationResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (simulationResult!!.isSuccess) StatusSuccess.copy(alpha = 0.12f) else StatusFailed.copy(alpha = 0.12f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (simulationResult!!.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (simulationResult!!.isSuccess) StatusSuccess else StatusFailed
                                )
                                Text(
                                    text = if (simulationResult!!.isSuccess) "Forward Succeeded!" else "Forward Failed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (simulationResult!!.isSuccess) StatusSuccess else StatusFailed
                                )
                            }
                            Text(
                                text = "${simulationResult!!.latencyMs} ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Endpoints: ${simulationResult!!.endpointUsed}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Status: HTTP ${simulationResult!!.statusCode}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        if (!simulationResult!!.errorMessage.isNullOrBlank()) {
                            Text(
                                text = "Errors: ${simulationResult!!.errorMessage}",
                                fontSize = 12.sp,
                                color = StatusFailed
                            )
                        }

                        if (simulationResult!!.responseBody.isNotBlank()) {
                            Text(
                                text = "Response Breakdown:\n${simulationResult!!.responseBody}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { viewModel.clearSimulationResult() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Dismiss", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Fire Button
        item {
            Button(
                onClick = {
                    val activeConfig = config.copy(
                        forwardToCustomApi = forwardToCustomApi,
                        forwardToSupabase = forwardToSupabase,
                        forwardToWebhook = forwardToWebhook
                    )
                    viewModel.simulateIncomingSms(context, selectedSender, selectedBody, activeConfig)
                },
                enabled = !isSimulating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                if (isSimulating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Forwarding to Server(s)...", fontSize = 14.sp)
                } else {
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
}
