package com.example.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SyncConfig
import com.example.network.RealtimeStatus
import com.example.service.WhatsAppAutomationState
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusSuccess
import com.example.util.PhoneNumberSanitizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppScreen(
    viewModel: MainViewModel,
    config: SyncConfig
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val waState by viewModel.whatsappAutomationState.collectAsStateWithLifecycle()
    val rtStatus by viewModel.supabaseRealtimeStatus.collectAsStateWithLifecycle()
    val rtLog by viewModel.supabaseRealtimeLog.collectAsStateWithLifecycle()
    val pendingQueueCount by viewModel.pendingQueueCount.collectAsStateWithLifecycle()

    var automationEnabled by remember(config.whatsappAutomationEnabled) { mutableStateOf(config.whatsappAutomationEnabled) }
    var tableName by remember(config.supabasePaymentsTable) { mutableStateOf(config.supabasePaymentsTable) }
    var numberCol by remember(config.whatsappNumberColumn) { mutableStateOf(config.whatsappNumberColumn) }
    var sentCol by remember(config.whatsappSentColumn) { mutableStateOf(config.whatsappSentColumn) }
    var templateText by remember(config.whatsappMessageTemplate) { mutableStateOf(config.whatsappMessageTemplate) }
    var sendDelayMs by remember(config.whatsappSendDelayMs) { mutableStateOf(config.whatsappSendDelayMs) }
    var autoBack by remember(config.whatsappAutoBack) { mutableStateOf(config.whatsappAutoBack) }
    var preferredPackage by remember(config.preferredWhatsAppPackage) { mutableStateOf(config.preferredWhatsAppPackage) }

    // Test form state
    var testPhone by remember { mutableStateOf("01712345678") }
    var testCustomerName by remember { mutableStateOf("MD SOJIBUL Hassan") }
    var testProductName by remember { mutableStateOf("Mega Bundle (All in One)") }
    var testProductLink by remember { mutableStateOf("https://technographybd.xyz/mega-bundle-vip-access.html") }
    var testBrandName by remember { mutableStateOf("Technography") }
    var testMethod by remember { mutableStateOf("bKash") }
    var testTrxId by remember { mutableStateOf("9K8L7M6N5P") }
    var testAmount by remember { mutableStateOf("1500") }
    var showAdvancedTestFields by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshWhatsAppState(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Header & Service Readiness Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF25D366)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "WhatsApp UI Automation",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Zero QR • Zero Cloud API • 100% Free",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = automationEnabled,
                        onCheckedChange = {
                            automationEnabled = it
                            viewModel.updateConfig(
                                config.copy(
                                    whatsappAutomationEnabled = it,
                                    supabasePaymentsTable = tableName,
                                    whatsappNumberColumn = numberCol,
                                    whatsappSentColumn = sentCol,
                                    whatsappMessageTemplate = templateText,
                                    whatsappSendDelayMs = sendDelayMs,
                                    whatsappAutoBack = autoBack,
                                    preferredWhatsAppPackage = preferredPackage
                                )
                            )
                            if (it) {
                                viewModel.restartRealtimeListener(context)
                            }
                        },
                        modifier = Modifier.testTag("wa_automation_switch")
                    )
                }

                HorizontalDivider()

                // Accessibility Service Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Auto-Sender",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (waState.isAccessibilityEnabled) "✓ এক্সেসিবিলিটি সার্ভিস সক্রিয় আছে" else "⚠ এক্সেসিবিলিটি সার্ভিস বন্ধ (চালু করতে হবে)",
                            fontSize = 11.sp,
                            color = if (waState.isAccessibilityEnabled) StatusSuccess else StatusFailed
                        )
                    }

                    if (!waState.isAccessibilityEnabled) {
                        Button(
                            onClick = { viewModel.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusFailed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("open_accessibility_btn")
                        ) {
                            Text("Turn ON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StatusSuccess.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Active",
                                color = StatusSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // WhatsApp App Installed Status & Selector
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "মেসেজ পাঠানোর WhatsApp অ্যাপ",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "আপনার যে অ্যাপে অ্যাকাউন্ট লগইন করা আছে সেটি সিলেক্ট রাখুন",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = if (waState.isWhatsAppInstalled || waState.isBusinessInstalled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (waState.isWhatsAppInstalled || waState.isBusinessInstalled) StatusSuccess else StatusFailed
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = preferredPackage == "com.whatsapp",
                            onClick = {
                                preferredPackage = "com.whatsapp"
                                viewModel.updateConfig(config.copy(preferredWhatsAppPackage = "com.whatsapp"))
                            },
                            label = { Text("WhatsApp Messenger (সাধারণ)") },
                            leadingIcon = {
                                if (preferredPackage == "com.whatsapp") {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = preferredPackage == "com.whatsapp.w4b",
                            onClick = {
                                preferredPackage = "com.whatsapp.w4b"
                                viewModel.updateConfig(config.copy(preferredWhatsAppPackage = "com.whatsapp.w4b"))
                            },
                            label = { Text("WhatsApp Business") },
                            leadingIcon = {
                                if (preferredPackage == "com.whatsapp.w4b") {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Supabase Realtime Listener Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Supabase Realtime Trigger",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Status: ${rtStatus.name} • $rtLog",
                            fontSize = 11.sp,
                            color = when (rtStatus) {
                                RealtimeStatus.LISTENING, RealtimeStatus.CONNECTED -> StatusSuccess
                                RealtimeStatus.CONNECTING -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (pendingQueueCount > 0) {
                            Text(
                                text = "পেন্ডিং মেসেজ কিউ: $pendingQueueCount টি",
                                fontSize = 11.sp,
                                color = PrimaryIndigo,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.restartRealtimeListener(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reconnect Realtime", tint = PrimaryIndigo)
                    }
                }
            }
        }

        // Section 2: Test WhatsApp Automation Button & Simulation
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryIndigo)
                    Text(
                        text = "টেস্ট অটোমেশন (Test Auto Send)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Text(
                    text = "যেকোনো একটি নাম্বার দিয়ে টেস্ট করুন। অ্যাপ সরাসরি WhatsApp খুলে সেন্ড বাটনে ক্লিক করবে এবং ব্যাক বাটনে ফিরে আসবে।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = testPhone,
                    onValueChange = { testPhone = it },
                    label = { Text("Customer WhatsApp Number") },
                    placeholder = { Text("017XXXXXXXX") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    supportingText = {
                        val sanitized = PhoneNumberSanitizer.sanitize(testPhone)
                        Text(
                            text = if (sanitized != null) "Sanitized: +$sanitized (Valid BD format)" else "Invalid number (01... বা 880... দিন)",
                            color = if (sanitized != null) StatusSuccess else StatusFailed
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wa_test_phone_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = testCustomerName,
                        onValueChange = { testCustomerName = it },
                        label = { Text("Customer Name") },
                        placeholder = { Text("MD SOJIBUL Hassan") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = testAmount,
                        onValueChange = { testAmount = it },
                        label = { Text("Price (৳)") },
                        placeholder = { Text("1500") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = testProductName,
                    onValueChange = { testProductName = it },
                    label = { Text("Product Name") },
                    placeholder = { Text("Mega Bundle (All in One)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = testProductLink,
                    onValueChange = { testProductLink = it },
                    label = { Text("VIP Link / Download URL") },
                    placeholder = { Text("https://technographybd.xyz/mega-bundle-vip-access.html") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val previewMessage = templateText
                    .replace("{name}", testCustomerName)
                    .replace("{product_name}", testProductName)
                    .replace("{product_link}", testProductLink)
                    .replace("{vip_link}", testProductLink)
                    .replace("{link}", testProductLink)
                    .replace("{amount}", testAmount)
                    .replace("{price}", testAmount)
                    .replace("{brand_name}", testBrandName)
                    .replace("{order_id}", "ORD-2026")
                    .replace("{method}", testMethod)
                    .replace("{trx_id}", testTrxId)
                    .replace("{phone}", testPhone)

                Button(
                    onClick = {
                        viewModel.triggerTestWhatsAppSend(
                            context = context,
                            phone = testPhone,
                            message = previewMessage,
                            method = testMethod,
                            trxId = testTrxId,
                            amount = testAmount.toDoubleOrNull() ?: 1500.0
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wa_test_send_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("টেস্ট সেন্ড পাঠান (Auto-Click Test)", fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (waState.lastStatusMessage.isNotBlank()) {
                    Text(
                        text = "Status: ${waState.lastStatusMessage}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 3: Message Template & Variables Customization
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Icon(Icons.Default.Message, contentDescription = null, tint = PrimaryIndigo)
                        Text(
                            text = "মেসেজ টেমপ্লেট (Message Template)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            templateText = com.example.model.DEFAULT_VIP_WHATSAPP_TEMPLATE
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🌟 VIP টেমপ্লেট লোড", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "কাস্টমারকে যে মেসেজ পাঠানো হবে তা এখানে লিখুন। ভেরিয়েবল যোগ করতে নিচে ট্যাপ করুন:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Variable insertion chips
                Text("ভেরিয়েবল যোগ করতে ট্যাপ করুন:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("{name}", "{product_name}", "{product_link}").forEach { variable ->
                            SuggestionChip(
                                onClick = { templateText = "$templateText $variable" },
                                label = { Text(variable, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("{amount}", "{brand_name}", "{order_id}", "{trx_id}").forEach { variable ->
                            SuggestionChip(
                                onClick = { templateText = "$templateText $variable" },
                                label = { Text(variable, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = templateText,
                    onValueChange = { templateText = it },
                    label = { Text("WhatsApp Message Text") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wa_template_input")
                )

                // Website Product Message Guide Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryIndigo.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
                            Text("ওয়েবসাইট থেকে আলাদা আলাদা প্রোডাক্টের মেসেজ/লিংক পাঠানোর নিয়ম:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryIndigo)
                        }
                        Text(
                            text = "• নিয়ম ১ (সম্পূর্ণ মেসেজ ওয়েবসাইট থেকে): Supabase টেবিলে 'custom_message' বা 'message' কলাম রাখুন। ওয়েবসাইট থেকে যে মেসেজ বা লিংক পাঠাবেন, অ্যাপ হুবহু সেটাই কাস্টমারকে পাঠিয়ে দিবে।\n" +
                                    "• নিয়ম ২ (ভেরিয়েবল দিয়ে): টেবিলে 'product_name' ও 'product_link' কলাম রাখলে অ্যাপ স্বয়ংক্রিয়ভাবে উপরের টেমপ্লেটের {product_name} ও {product_link} পরিবর্তন করে কাস্টমারকে পাঠিয়ে দিবে।",
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Preview box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("মেসেজ প্রিভিউ (Live Preview):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = templateText
                                .replace("{name}", testCustomerName)
                                .replace("{product_name}", testProductName)
                                .replace("{product_link}", testProductLink)
                                .replace("{vip_link}", testProductLink)
                                .replace("{link}", testProductLink)
                                .replace("{amount}", testAmount)
                                .replace("{price}", testAmount)
                                .replace("{brand_name}", testBrandName)
                                .replace("{order_id}", "ORD-2026")
                                .replace("{method}", testMethod)
                                .replace("{trx_id}", testTrxId)
                                .replace("{phone}", testPhone),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Section 4: Supabase Table & Field Mapping
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = PrimaryIndigo)
                    Text(
                        text = "Supabase টেবিল ও কলাম ম্যাপিং",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                OutlinedTextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = { Text("Supabase Table Name") },
                    placeholder = { Text("payments") },
                    singleLine = true,
                    supportingText = { Text("Realtime INSERT ও Polling এই টেবিলে হবে") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = numberCol,
                        onValueChange = { numberCol = it },
                        label = { Text("Phone Column") },
                        placeholder = { Text("whatsapp_number") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sentCol,
                        onValueChange = { sentCol = it },
                        label = { Text("Sent Status Column") },
                        placeholder = { Text("whatsapp_sent") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("অটো-ব্যাক (Auto Back)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("মেসেজ সেন্ড করার সাথে সাথে ব্যাক বাটন চেপে অ্যাপে ফিরে আসবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoBack,
                        onCheckedChange = { autoBack = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("সেন্ড ক্লিক ডিলে (Safe Delay)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("WhatsApp খুলে টেক্সট লোড হওয়ার জন্য ১-১.৫ সেকেন্ড অপেক্ষা", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${sendDelayMs}ms",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PrimaryIndigo
                    )
                }

                Button(
                    onClick = {
                        val newConfig = config.copy(
                            whatsappAutomationEnabled = automationEnabled,
                            supabasePaymentsTable = tableName,
                            whatsappNumberColumn = numberCol,
                            whatsappSentColumn = sentCol,
                            whatsappMessageTemplate = templateText,
                            whatsappSendDelayMs = sendDelayMs,
                            whatsappAutoBack = autoBack,
                            preferredWhatsAppPackage = preferredPackage
                        )
                        viewModel.updateConfig(newConfig)
                        viewModel.restartRealtimeListener(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("সেটিংস সেভ করুন (Save Settings)", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 5: Step-by-Step Guidance in Bengali
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = PrimaryIndigo.copy(alpha = 0.08f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = PrimaryIndigo)
                    Text(
                        text = "অটোমেশন যেভাবে কাজ করে (Guide)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = PrimaryIndigo
                    )
                }

                Text(
                    text = "১. সুপাবেস রিয়েলটাইম (Supabase Realtime): পেমেন্টস টেবিলে নতুন সারি (INSERT) আসলেই অ্যাপ তা সাথে সাথে শনাক্ত করে।\n" +
                            "২. ডুপ্লিকেট রোধ: 'whatsapp_sent = false' চেক করে। মেসেজ পাঠানো শেষ হলেই অটোমেটিক 'whatsapp_sent = true' আপডেট করে দেয়।\n" +
                            "৩. জিরো কিউআর ও ক্লাউড এপিআই ফ্রি: অফিসিয়াল হোয়াটসঅ্যাপ ইন্টেন্ট দিয়ে চ্যাট ওপেন হয় এবং ব্যাকগ্রাউন্ড এক্সেসিবিলিটি সার্ভিস ১.২ সেকেন্ড পর স্বয়ংক্রিয়ভাবে সেন্ড বাটন ক্লিক করে।\n" +
                            "৪. ২৪/৭ ব্যাকগ্রাউন্ড সার্ভিস: অ্যাপ মিনিমাইজ বা ব্যাকগ্রাউন্ডে থাকলেও কোনো মেসেজ মিস হবে না।",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
