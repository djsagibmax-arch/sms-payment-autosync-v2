package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import com.example.model.ConnectionMode
import com.example.model.SyncConfig
import com.example.network.NetworkResult
import com.example.ui.theme.*

@Composable
fun ConnectionScreen(
    viewModel: MainViewModel,
    config: SyncConfig,
    testResult: NetworkResult?,
    isTesting: Boolean,
    modifier: Modifier = Modifier
) {
    // Multi-Destination Toggle States
    var forwardToCustomApi by remember(config) { mutableStateOf(config.forwardToCustomApi) }
    var forwardToSupabase by remember(config) { mutableStateOf(config.forwardToSupabase) }
    var forwardToWebhook by remember(config) { mutableStateOf(config.forwardToWebhook) }
    
    // Custom API (e.g. Render, Node.js, PHP)
    var customEndpointUrl by remember(config) { mutableStateOf(config.customEndpointUrl) }
    var customAuthHeaderName by remember(config) { mutableStateOf(config.customAuthHeaderName) }
    var customAuthToken by remember(config) { mutableStateOf(config.customAuthToken) }
    var customHttpMethod by remember(config) { mutableStateOf(config.customHttpMethod) }

    // Supabase
    var supabaseUrl by remember(config) { mutableStateOf(config.supabaseUrl) }
    var supabaseApiKey by remember(config) { mutableStateOf(config.supabaseApiKey) }
    var supabaseTable by remember(config) { mutableStateOf(config.supabaseTable) }
    
    // Webhook
    var webhookUrl by remember(config) { mutableStateOf(config.webhookUrl) }
    var webhookSecret by remember(config) { mutableStateOf(config.webhookSecret) }

    fun buildCurrentConfig(): SyncConfig {
        return config.copy(
            forwardToCustomApi = forwardToCustomApi,
            forwardToSupabase = forwardToSupabase,
            forwardToWebhook = forwardToWebhook,
            connectionMode = when {
                forwardToCustomApi -> ConnectionMode.CUSTOM_API
                forwardToSupabase -> ConnectionMode.SUPABASE
                forwardToWebhook -> ConnectionMode.DIRECT_WEBHOOK
                else -> ConnectionMode.CUSTOM_API
            },
            supabaseUrl = supabaseUrl,
            supabaseApiKey = supabaseApiKey,
            supabaseTable = supabaseTable,
            customEndpointUrl = customEndpointUrl,
            customAuthHeaderName = customAuthHeaderName,
            customAuthToken = customAuthToken,
            customHttpMethod = customHttpMethod,
            webhookUrl = webhookUrl,
            webhookSecret = webhookSecret
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Multi-Forwarding Overview & Switch Board
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AltRoute, contentDescription = null, tint = PrimaryIndigo)
                        Column {
                            Text(
                                text = "Multi-Destination Forwarding",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "টাকা আসার সাথে সাথে একসাথে যেখানে যেখানে পাঠাতে চান সেগুলো চালু করুন",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 1. Custom API Switch (Render / Web Backend)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (forwardToCustomApi) PrimaryIndigo.copy(alpha = 0.08f) else Color.Transparent,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Http,
                                    contentDescription = null,
                                    tint = if (forwardToCustomApi) PrimaryIndigo else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text(
                                        text = "Render / Custom API",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Render বট / Facebook কনফার্মেশন",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = forwardToCustomApi,
                                onCheckedChange = { forwardToCustomApi = it }
                            )
                        }
                    }

                    // 2. Supabase Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (forwardToSupabase) SecondaryTeal.copy(alpha = 0.08f) else Color.Transparent,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = if (forwardToSupabase) SecondaryTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text(
                                        text = "Supabase Database",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "অনলাইন ডাটাবেজে রেকর্ড সেভ",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = forwardToSupabase,
                                onCheckedChange = { forwardToSupabase = it }
                            )
                        }
                    }

                    // 3. Direct Webhook Switch
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (forwardToWebhook) NagadOrange.copy(alpha = 0.08f) else Color.Transparent,
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Webhook,
                                    contentDescription = null,
                                    tint = if (forwardToWebhook) NagadOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column {
                                    Text(
                                        text = "Direct Webhook",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Zapier / Google Sheets / Telegram",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = forwardToWebhook,
                                onCheckedChange = { forwardToWebhook = it }
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Custom API (Render) Configuration Card
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Http, contentDescription = null, tint = PrimaryIndigo)
                            Text(
                                text = "1. Render / Custom API Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        AssistChip(
                            onClick = { forwardToCustomApi = !forwardToCustomApi },
                            label = { Text(if (forwardToCustomApi) "Enabled" else "Disabled", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (forwardToCustomApi) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (forwardToCustomApi) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Text(
                        text = "Sends formatted JSON payload to your Render server (Facebook bot automations, Node.js, Python, PHP, etc.).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customEndpointUrl,
                        onValueChange = { customEndpointUrl = it },
                        label = { Text("Render Endpoint URL") },
                        placeholder = { Text("https://my-sales-bot-5d70.onrender.com/webhook/sms") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customAuthHeaderName,
                            onValueChange = { customAuthHeaderName = it },
                            label = { Text("Auth Header") },
                            placeholder = { Text("Authorization / X-Api-Key") },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customHttpMethod,
                            onValueChange = { customHttpMethod = it.uppercase() },
                            label = { Text("Method") },
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = customAuthToken,
                        onValueChange = { customAuthToken = it },
                        label = { Text("Secret Token / Bearer Token (Optional)") },
                        placeholder = { Text("Bearer secret_key_123") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        }

        // Section 2: Supabase Configuration Card
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = SecondaryTeal)
                            Text(
                                text = "2. Supabase Database Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        AssistChip(
                            onClick = { forwardToSupabase = !forwardToSupabase },
                            label = { Text(if (forwardToSupabase) "Enabled" else "Disabled", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (forwardToSupabase) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (forwardToSupabase) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Text(
                        text = "Inserts received payment rows directly into your Supabase PostgreSQL table using the PostgREST API.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = supabaseUrl,
                        onValueChange = { supabaseUrl = it },
                        label = { Text("Supabase Project URL") },
                        placeholder = { Text("https://xyzcompany.supabase.co") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = supabaseApiKey,
                        onValueChange = { supabaseApiKey = it },
                        label = { Text("Supabase Anon / Service API Key") },
                        placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5cCI6...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = supabaseTable,
                        onValueChange = { supabaseTable = it },
                        label = { Text("Target Table Name") },
                        placeholder = { Text("payments / sms_payments") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        }

        // Section 3: Webhook Configuration Card
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Webhook, contentDescription = null, tint = NagadOrange)
                            Text(
                                text = "3. Direct Webhook Settings",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        AssistChip(
                            onClick = { forwardToWebhook = !forwardToWebhook },
                            label = { Text(if (forwardToWebhook) "Enabled" else "Disabled", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (forwardToWebhook) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (forwardToWebhook) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Text(
                        text = "Standard HTTP POST webhook with optional secret signature header.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = webhookUrl,
                        onValueChange = { webhookUrl = it },
                        label = { Text("Webhook URL") },
                        placeholder = { Text("https://webhook.site/your-unique-uuid") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = webhookSecret,
                        onValueChange = { webhookSecret = it },
                        label = { Text("Webhook Secret Header (X-Webhook-Secret)") },
                        placeholder = { Text("optional_secret_hash") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }
        }

        // Test Connection Result Box
        if (testResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testResult.isSuccess) StatusSuccess.copy(alpha = 0.12f) else StatusFailed.copy(alpha = 0.12f)
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
                                    imageVector = if (testResult.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (testResult.isSuccess) StatusSuccess else StatusFailed
                                )
                                Text(
                                    text = if (testResult.isSuccess) "Connection Succeeded!" else "Connection Failed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (testResult.isSuccess) StatusSuccess else StatusFailed
                                )
                            }
                            Text(
                                text = "${testResult.latencyMs} ms",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Target: ${testResult.endpointUsed}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Status: HTTP ${testResult.statusCode}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        if (!testResult.errorMessage.isNullOrBlank()) {
                            Text(
                                text = "Error: ${testResult.errorMessage}",
                                fontSize = 12.sp,
                                color = StatusFailed
                            )
                        }

                        if (testResult.responseBody.isNotBlank()) {
                            Text(
                                text = "Server Response:\n${testResult.responseBody}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { viewModel.clearTestResult() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Dismiss", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val current = buildCurrentConfig()
                        viewModel.testConnection(current)
                    },
                    enabled = !isTesting,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", fontSize = 13.sp)
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection", fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = {
                        val current = buildCurrentConfig()
                        viewModel.updateConfig(current)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
