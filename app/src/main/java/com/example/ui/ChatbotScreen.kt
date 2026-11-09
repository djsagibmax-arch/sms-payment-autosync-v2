package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DEFAULT_CHATBOT_PROMPT
import com.example.model.SyncConfig
import com.example.service.ChatbotActivityItem
import com.example.service.GeminiCallResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()
    val isNotificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsState()
    val botActivities by viewModel.botActivities.collectAsState()

    // Form state initialized from config
    var isEnabled by remember(config) { mutableStateOf(config.chatbotEnabled) }
    var isWhatsAppEnabled by remember(config) { mutableStateOf(config.chatbotWhatsAppEnabled) }
    var isMessengerEnabled by remember(config) { mutableStateOf(config.chatbotMessengerEnabled) }
    var apiKey by remember(config) { mutableStateOf(config.geminiApiKey) }
    var selectedModel by remember(config) { mutableStateOf(config.geminiModel) }
    var prompt by remember(config) { mutableStateOf(config.chatbotCustomPrompt) }
    var autoVerify by remember(config) { mutableStateOf(config.chatbotAutoVerifyPayment) }
    var liveChatHandover by remember(config) { mutableStateOf(config.liveChatHandoverActive) }

    // Custom Auto-Reply State (Non-AI fixed/custom reply)
    var isCustomReplyEnabled by remember(config) { mutableStateOf(config.customAutoReplyEnabled) }
    var customReplyMessage by remember(config) { mutableStateOf(config.customAutoReplyMessage) }

    var showApiKey by remember { mutableStateOf(false) }
    var showPromptEditor by remember { mutableStateOf(false) }

    // Gemini API Connection Ping Test State
    var isPingingGemini by remember { mutableStateOf(false) }
    var pingResultText by remember { mutableStateOf<String?>(null) }
    var pingSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Test Chat Simulator State
    var simulatorInput by remember { mutableStateOf("") }
    var simulatorChatHistory by remember {
        mutableStateOf(
            listOf(
                "বট" to "আসসালামু আলাইকুম! Technography BD-তে স্বাগতম। আপনি কোন প্রোডাক্টটি দেখতে চান বলুন? যেমন: মেগা বান্ডেল, ফেসবুক রিলস ইত্যাদি।"
            )
        )
    }
    var isTestingChat by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshNotificationAccessState(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Notification Access Warning / Status Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notification_permission_banner"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNotificationAccessGranted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isNotificationAccessGranted) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (isNotificationAccessGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isNotificationAccessGranted) "নোটিফিকেশন অটো-রিপ্লাই সক্রিয়" else "নোটিফিকেশন অ্যাক্সেস অনুমতি দিন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isNotificationAccessGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isNotificationAccessGranted) {
                                "অ্যাপটি হোয়াটসঅ্যাপ ও মেসেঞ্জারের নোটিফিকেশন থেকে মেসেজ পড়ে স্বয়ংক্রিয়ভাবে উত্তর দিতে প্রস্তুত।"
                            } else {
                                "হোয়াটসঅ্যাপ বা মেসেঞ্জার ওপেন না করে সরাসরি নোটিফিকেশন বার থেকে উত্তর পাঠাতে এই অনুমতিটি প্রয়োজন।"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isNotificationAccessGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (!isNotificationAccessGranted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.openNotificationAccessSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("অনুমতি দিন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Master Chatbot Toggle Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI সেলস চ্যাটবট",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isEnabled) "অটোমেটিক রিপ্লাই চালু আছে" else "চ্যাটবট বন্ধ আছে",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                viewModel.updateConfig(config.copy(chatbotEnabled = it))
                            },
                            modifier = Modifier.testTag("switch_chatbot_master")
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Platform Toggles
                    Text(
                        text = "প্ল্যাটফর্ম নির্বাচন করুন:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("WhatsApp & WA Business", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = isWhatsAppEnabled,
                            onCheckedChange = {
                                isWhatsAppEnabled = it
                                viewModel.updateConfig(config.copy(chatbotWhatsAppEnabled = it))
                            },
                            enabled = isEnabled
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = Color(0xFF0084FF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Facebook Messenger", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = isMessengerEnabled,
                            onCheckedChange = {
                                isMessengerEnabled = it
                                viewModel.updateConfig(config.copy(chatbotMessengerEnabled = it))
                            },
                            enabled = isEnabled
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    // Live Chat Handover & Auto TrxID Verification Switches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("লাইভ চ্যাট হ্যান্ডওভার (স্মার্ট বিরতি)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("আপনি যখন নিজে চ্যাট করবেন, তখন বট ১৫ মিনিটের জন্য স্বয়ংক্রিয়ভাবে থেমে থাকবে।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = liveChatHandover,
                            onCheckedChange = {
                                liveChatHandover = it
                                viewModel.updateConfig(config.copy(liveChatHandoverActive = it))
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TrxID পেমেন্ট অটো-ভেরিফিকেশন", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("কাস্টমার ট্রানজেকশন আইডি দিলে সাথে সাথে চেক করে প্রোডাক্ট ভিআইপি লিংক পাঠিয়ে দেবে।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoVerify,
                            onCheckedChange = {
                                autoVerify = it
                                viewModel.updateConfig(config.copy(chatbotAutoVerifyPayment = it))
                            }
                        )
                    }
                }
            }
        }

        // Custom Auto-Reply Card (On/Off Switch & Custom Message Editor)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_custom_auto_reply"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF25D366).copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubble,
                                        contentDescription = null,
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "কাস্টম অটো-রিপ্লাই সেটআপ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isCustomReplyEnabled) "কাস্টম টেক্সট দিয়ে রিপ্লাই সক্রিয়" else "কাস্টম রিপ্লাই বন্ধ (AI রিপ্লাই সক্রিয়)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCustomReplyEnabled) Color(0xFF25D366) else MaterialTheme.colorScheme.outline,
                                    fontWeight = if (isCustomReplyEnabled) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }

                        Switch(
                            checked = isCustomReplyEnabled,
                            onCheckedChange = {
                                isCustomReplyEnabled = it
                                viewModel.updateConfig(
                                    config.copy(
                                        customAutoReplyEnabled = it,
                                        customAutoReplyMessage = customReplyMessage
                                    )
                                )
                            },
                            modifier = Modifier.testTag("switch_custom_reply")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "সুইচটি চালু রাখলে কাস্টমার মেসেজ দিলে কোনো এআই ছাড়াই আপনার নির্ধারিত এই নির্দিষ্ট বার্তাটি স্বয়ংক্রিয়ভাবে রিপ্লাই চলে যাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = isCustomReplyEnabled) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                text = "কাস্টম রিপ্লাই বার্তা এডিট করুন:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = customReplyMessage,
                                onValueChange = { customReplyMessage = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 220.dp)
                                    .testTag("input_custom_reply_message"),
                                placeholder = { Text("আপনার কাস্টম বার্তা এখানে লিখুন...") },
                                supportingText = {
                                    Text("ব্যবহারযোগ্য ট্যাগ: {name} = কাস্টমারের নাম, {platform} = WhatsApp/Messenger")
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Live Preview Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "লাইভ প্রিভিউ (কাস্টমার যেভাবে দেখবেন):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = customReplyMessage
                                            .replace("{name}", "সজীব হাসান")
                                            .replace("{platform}", "WhatsApp"),
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        customReplyMessage = com.example.model.DEFAULT_CUSTOM_AUTO_REPLY_MESSAGE
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ডিফল্ট বার্তা রিস্টোর", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.updateConfig(
                                            config.copy(
                                                customAutoReplyEnabled = isCustomReplyEnabled,
                                                customAutoReplyMessage = customReplyMessage
                                            )
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("সেভ করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gemini AI API Configuration Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Google Gemini AI কনফিগারেশন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gemini API Key বসালে বট মানুষের মতো বন্ধুত্বপূর্ণ ও আকর্ষণীয় বাংলায় কথা বলবে। কী না থাকলে স্মার্ট বাংলা ফলব্যাক দিয়ে কাজ করবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Key (AI Studio)") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_gemini_api_key"),
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle API Key visibility"
                                )
                            }
                        }
                    )

                    val cleanApiInput = apiKey.trim().removeSurrounding("\"").removeSurrounding("'")
                    if (cleanApiInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (cleanApiInput.startsWith("AIzaSy") || cleanApiInput.startsWith("AQ.") || cleanApiInput.length >= 30) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "✓ Google AI Studio API Key প্রস্তুত (${cleanApiInput.take(6)}...${cleanApiInput.takeLast(4)})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF047857),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "সতর্কতা: Google AI Studio থেকে সম্পূর্ণ API Key কপি করে পেস্ট করুন।",
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Gemini মডেল নির্বাচন করুন (আপডেট মডেল):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val modelOptions = listOf(
                        "gemini-3.5-flash-lite" to "3.5 Flash-Lite (Free Tier - Fastest)",
                        "gemini-3.5-flash" to "3.5 Flash (Recommended)",
                        "gemini-3.8-flash" to "3.8 Flash (Thinking Model)",
                        "gemini-3.1-pro-preview" to "3.1 Pro (Reasoning)",
                        "gemini-4-argon" to "4 Argon (Requires Paid Tier)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedModel == "gemini-3.5-flash-lite",
                                onClick = { selectedModel = "gemini-3.5-flash-lite" },
                                label = { Text("3.5 Flash-Lite (Free - Fast)", fontSize = 11.sp, fontWeight = if (selectedModel == "gemini-3.5-flash-lite") FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedModel == "gemini-3.5-flash",
                                onClick = { selectedModel = "gemini-3.5-flash" },
                                label = { Text("3.5 Flash (Recommended)", fontSize = 11.sp, fontWeight = if (selectedModel == "gemini-3.5-flash") FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedModel == "gemini-3.8-flash",
                                onClick = { selectedModel = "gemini-3.8-flash" },
                                label = { Text("3.8 Flash (Thinking)", fontSize = 11.sp, fontWeight = if (selectedModel == "gemini-3.8-flash") FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedModel == "gemini-4-argon",
                                onClick = { selectedModel = "gemini-4-argon" },
                                label = { Text("4 Argon (Paid)", fontSize = 11.sp, fontWeight = if (selectedModel == "gemini-4-argon") FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (selectedModel == "gemini-4-argon") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ℹ️ দ্রষ্টব্য: আপনার Google AI Studio প্রজেক্টে বিলিং সেটআপ না থাকলে (Free Tier) gemini-4-argon কোটা ০ থাকে (HTTP 429 দেয়)। বিনামূল্যে আনলিমিটেড ব্যবহার করতে '3.5 Flash-Lite' বা '3.8 Flash' নির্বাচন করুন।",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        label = { Text("মডেল নাম / কাস্টম মডেল") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dedicated 1-Click Gemini API Connection Test
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (apiKey.isBlank()) {
                                    pingResultText = "⚠️ দয়া করে প্রথমে উপরে আপনার Gemini API Key লিখুন।"
                                    pingSuccess = false
                                    return@OutlinedButton
                                }
                                isPingingGemini = true
                                pingResultText = null
                                pingSuccess = null
                                viewModel.testGeminiConnection(apiKey, selectedModel) { res ->
                                    isPingingGemini = false
                                    pingSuccess = res.isSuccess
                                    pingResultText = if (res.isSuccess) {
                                        "✓ Gemini API সফলভাবে সংযুক্ত হয়েছে! মডেল: ${res.modelUsed} (রেসপন্স সময়: ${res.latencyMs}ms)"
                                    } else {
                                        "❌ কানেকশন ব্যর্থ: ${res.errorMessage}"
                                    }
                                }
                            },
                            enabled = !isPingingGemini,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_test_gemini_ping")
                        ) {
                            if (isPingingGemini) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("API যাচাই করা হচ্ছে...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gemini API কানেকশন টেস্ট করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (pingResultText != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (pingSuccess == true) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pingResultText ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pingSuccess == true) Color(0xFF047857) else MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Prompt Customizer Toggle
                    TextButton(
                        onClick = { showPromptEditor = !showPromptEditor },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (showPromptEditor) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showPromptEditor) "বটের নির্দেশনাবলী বন্ধ করুন" else "বটের সেলস নির্দেশনা (Custom Prompt) এডিট করুন")
                    }

                    AnimatedVisibility(visible = showPromptEditor) {
                        Column {
                            OutlinedTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                label = { Text("AI সেলস নির্দেশনা (Prompt Instructions)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                maxLines = 10
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { prompt = DEFAULT_CHATBOT_PROMPT }
                            ) {
                                Text("ডিফল্ট প্রম্পটে রিসেট করুন", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.updateConfig(
                                config.copy(
                                    chatbotEnabled = isEnabled,
                                    chatbotWhatsAppEnabled = isWhatsAppEnabled,
                                    chatbotMessengerEnabled = isMessengerEnabled,
                                    geminiApiKey = apiKey.trim(),
                                    geminiModel = selectedModel,
                                    chatbotCustomPrompt = prompt.trim(),
                                    chatbotAutoVerifyPayment = autoVerify,
                                    liveChatHandoverActive = liveChatHandover,
                                    customAutoReplyEnabled = isCustomReplyEnabled,
                                    customAutoReplyMessage = customReplyMessage
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_chatbot_config")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("চ্যাটবট সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Interactive Test Chat Simulator Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "লাইভ চ্যাট সিমুলেটর (টেস্ট)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCustomReplyEnabled) Color(0xFF25D366).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = if (isCustomReplyEnabled) "মোড: কাস্টমাইজ" else "মোড: Gemini AI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCustomReplyEnabled) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            if (simulatorChatHistory.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                TextButton(
                                    onClick = { simulatorChatHistory = emptyList() },
                                    modifier = Modifier.testTag("btn_clear_simulator_chat")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "মুছুন",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "এখানে সরাসরি Gemini AI API রেসপন্স পরীক্ষা করা যাবে। কোনো অফলাইন মোবাইল বট মেসেজ দেওয়া হবে না—শুধু জেমিনি এআই এপিআই সক্রিয় আছে কি না তা যাচাই করা যাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Chat messages bubble container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (simulatorChatHistory.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "কোনো টেস্ট মেসেজ নেই।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        simulatorChatHistory = listOf(
                                            "কাস্টমার" to "ভাই আপনাদের মেগা বান্ডেলটায় কি কি আছে?",
                                            "বট" to "ধন্যবাদ সম্মানিত গ্রাহক! আমাদের 'Mega Bundle (All in One)'-এ পাচ্ছেন দেড় লক্ষ রিলস, ভাইরাল শর্টস ও মেগা ড্রাইভ অ্যাক্সেস মাত্র ৩০০ ৳। আপনি কি এটি নিতে চান?"
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ডেমো চ্যাট লোড করুন", fontSize = 12.sp)
                                }
                            }
                        } else {
                            simulatorChatHistory.forEachIndexed { index, (sender, msg) ->
                                val isCustomer = sender == "কাস্টমার"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isCustomer) Arrangement.End else Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (isCustomer) 12.dp else 2.dp,
                                            bottomEnd = if (isCustomer) 2.dp else 12.dp
                                        ),
                                        color = if (isCustomer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = sender,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCustomer) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val list = simulatorChatHistory.toMutableList()
                                                        if (index in list.indices) {
                                                            list.removeAt(index)
                                                            simulatorChatHistory = list
                                                        }
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "মেসেজ মুছুন",
                                                        tint = if (isCustomer) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = msg,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isCustomer) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isTestingChat) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Text(
                                        text = "AI উত্তর ভাবছে...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Input & Send
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = simulatorInput,
                            onValueChange = { simulatorInput = it },
                            placeholder = { Text("যেমন: মেগা বান্ডেলের দাম কত?") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulator_input"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = simulatorInput.trim()
                                if (text.isNotBlank() && !isTestingChat) {
                                    val newHistory = simulatorChatHistory + ("কাস্টমার" to text)
                                    simulatorChatHistory = newHistory
                                    simulatorInput = ""
                                    isTestingChat = true

                                    viewModel.testChatbotReply(
                                        context = context,
                                        query = text,
                                        customConfig = config.copy(
                                            chatbotEnabled = isEnabled,
                                            geminiApiKey = apiKey.trim(),
                                            geminiModel = selectedModel,
                                            chatbotCustomPrompt = prompt.trim(),
                                            chatbotAutoVerifyPayment = autoVerify,
                                            customAutoReplyEnabled = isCustomReplyEnabled,
                                            customAutoReplyMessage = customReplyMessage
                                        )
                                    ) { reply ->
                                        simulatorChatHistory = newHistory + ("বট" to reply)
                                        isTestingChat = false
                                    }
                                }
                            },
                            enabled = !isTestingChat && simulatorInput.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Quick prompt chips
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { simulatorInput = "মেগা বান্ডেলে কী কী আছে?" },
                            label = { Text("বান্ডেল বিবরণ", style = MaterialTheme.typography.labelSmall) }
                        )
                        SuggestionChip(
                            onClick = { simulatorInput = "টাকা পাঠিয়েছি TrxID: 9J47AB89C1" },
                            label = { Text("টাকা পাঠিয়েছি", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Live Bot Activity Logs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "সাম্প্রতিক বট অ্যাক্টিভিটি (${botActivities.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (botActivities.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearBotActivities() },
                                modifier = Modifier.testTag("btn_clear_bot_activities")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("মুছুন", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (botActivities.isEmpty()) {
                        Text(
                            text = "এখনও কোনো মেসেজের উত্তর পাঠানো হয়নি। নোটিফিকেশন আসলে এখানে লাইভ দেখতে পাবেন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            botActivities.take(5).forEach { item ->
                                BotActivityRow(
                                    item = item,
                                    onResumeBot = { viewModel.resumeBotForSender(item.senderName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun BotActivityRow(
    item: ChatbotActivityItem,
    onResumeBot: () -> Unit
) {
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(item.timestamp))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.platform.contains("WhatsApp", ignoreCase = true)) Color(0xFF25D366) else Color(0xFF0084FF)
                    ) {
                        Text(
                            text = item.platform,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.senderName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "মেসেজ: \"${item.incomingMessage}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "উত্তর: \"${item.replyMessage}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (item.isLiveChatPaused) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⏸ লাইভ চ্যাটের জন্য বট পজ করা হয়েছে",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onResumeBot, contentPadding = PaddingValues(0.dp)) {
                        Text("এখনই আনপজ করুন", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
