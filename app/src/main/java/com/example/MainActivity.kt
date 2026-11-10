package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ConnectionMode
import com.example.service.SyncForegroundService
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryIndigo
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusSuccess
import com.example.util.KeepAliveHelper

enum class AppNavTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("ড্যাশবোর্ড", Icons.Default.Dashboard),
    WHATSAPP("অটো-সেন্ড", Icons.Default.Chat),
    CONNECTION("কানেকশন", Icons.Default.CloudSync),
    WALLETS("ওয়ালেট", Icons.Default.Tune),
    LOGS("লগস", Icons.Default.ReceiptLong),
    SIMULATOR("সিমুলেটর", Icons.Default.BugReport)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requiredPermissions: Array<String>
        get() {
            val list = mutableListOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return list.toTypedArray()
        }

    private var permissionClickAttempts = 0

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        Log.d("MainActivity", "Permission auto-request result: allGranted=$allGranted, details=$result")
        if (allGranted) {
            SyncForegroundService.start(this)
            viewModel.refreshServiceState()
        } else {
            // If permissions were denied or system suppressed the dialog, open App Settings so user can enable directly
            openAppSystemSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Unconditionally ensure 24/7 service and Supabase listener are running
        try {
            SyncForegroundService.start(this)
            com.example.network.SupabaseRealtimeManager.start(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start services in onCreate: ${e.message}")
        }
        viewModel.refreshServiceState()

        val sp = getSharedPreferences("app_launch_prefs", Context.MODE_PRIVATE)
        val hasPromptedInitial = sp.getBoolean("has_prompted_initial_permissions", false)

        val hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasSms && !hasPromptedInitial) {
            sp.edit().putBoolean("has_prompted_initial_permissions", true).apply()
            checkAndRequestAllPermissions()
        }

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel, onRecheckPermissions = {
                    checkAndRequestAllPermissions()
                })
            }
        }
    }

    private fun checkAndRequestAllPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            SyncForegroundService.start(this)
            com.example.network.SupabaseRealtimeManager.start(this)
            viewModel.refreshServiceState()
            return
        }

        try {
            permissionRequestLauncher.launch(missingPermissions.toTypedArray())
        } catch (e: Exception) {
            openAppSystemSettings()
        }
    }

    private fun openAppSystemSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            android.widget.Toast.makeText(
                this,
                "দয়া করে অ্যাপ সেটিংসে 'Permissions'-এ ট্যাপ করে SMS ও নোটিফিকেশন চালু করুন",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open settings: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshServiceState()
        viewModel.refreshWhatsAppState(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    onRecheckPermissions: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppNavTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    val config by viewModel.config.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val allLogs by viewModel.allLogs.collectAsStateWithLifecycle()
    val recentLogs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val testResult by viewModel.testConnectionResult.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTestingConnection.collectAsStateWithLifecycle()
    val selectedLog by viewModel.selectedLog.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    var hasSmsPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(Unit) {
        hasSmsPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CurrencyExchange,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(id = R.string.full_app_name),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "bKash • Nagad • Rocket • Webhooks",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Permission Indicator & Service Status Tag
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isServiceRunning && hasSmsPermissions) StatusSuccess.copy(alpha = 0.15f) else StatusFailed.copy(alpha = 0.15f),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onRecheckPermissions() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceRunning && hasSmsPermissions) StatusSuccess else StatusFailed)
                            )
                            Text(
                                text = if (!hasSmsPermissions) "Grant Permissions" else if (isServiceRunning) "Running (24/7)" else "Service Stopped",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isServiceRunning && hasSmsPermissions) StatusSuccess else StatusFailed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                ScrollableTabRow(
                    selectedTabIndex = currentTab.ordinal,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = PrimaryIndigo,
                    indicator = { tabPositions ->
                        if (currentTab.ordinal < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                                color = PrimaryIndigo
                            )
                        }
                    },
                    divider = { HorizontalDivider() }
                ) {
                    AppNavTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) PrimaryIndigo else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val waState by viewModel.whatsappAutomationState.collectAsStateWithLifecycle()
            var isBatteryIgnored by remember {
                mutableStateOf(KeepAliveHelper.isBatteryOptimizationIgnored(context))
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        hasSmsPermissions = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                        viewModel.refreshWhatsAppState(context)
                        viewModel.refreshServiceState()
                        isBatteryIgnored = KeepAliveHelper.isBatteryOptimizationIgnored(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            var isBannerDismissed by remember { mutableStateOf(false) }
            val needsAnyPermission = !hasSmsPermissions || !isBatteryIgnored || !waState.isAccessibilityEnabled
            if (needsAnyPermission && !isBannerDismissed) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusFailed, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "প্রয়োজনীয় পারমিশন সেটআপ (Required Setup)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = StatusFailed
                                )
                            }
                            IconButton(
                                onClick = { isBannerDismissed = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = StatusFailed, modifier = Modifier.size(16.dp))
                            }
                        }

                        // 1. SMS & Notification Permission
                        if (!hasSmsPermissions) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("১. SMS ও নোটিফিকেশন পারমিশন", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Button(
                                    onClick = {
                                        onRecheckPermissions()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("অনুমতি দিন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 2. Battery Saver Optimization Exemption
                        if (!isBatteryIgnored) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("২. ২৪/৭ ব্যাকগ্রাউন্ড সার্ভিস (ব্যাটারি সেভার)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Button(
                                    onClick = {
                                        KeepAliveHelper.requestIgnoreBatteryOptimization(context)
                                        isBatteryIgnored = KeepAliveHelper.isBatteryOptimizationIgnored(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("সেভার বন্ধ করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 3. Accessibility Service for WhatsApp Auto Send
                        if (!waState.isAccessibilityEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("৩. হোয়াটসঅ্যাপ অটো-সেন্ডার (Accessibility)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                Button(
                                    onClick = { viewModel.openAccessibilitySettings(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("চালু করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Tab Content
            when (currentTab) {
                AppNavTab.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    stats = stats,
                    isServiceRunning = isServiceRunning,
                    config = config,
                    recentLogs = recentLogs,
                    onNavigateToWallets = { currentTab = AppNavTab.WALLETS },
                    onNavigateToConnection = { currentTab = AppNavTab.CONNECTION },
                    onNavigateToLogs = { currentTab = AppNavTab.LOGS },
                    onNavigateToSimulator = { currentTab = AppNavTab.SIMULATOR },
                    onNavigateToWhatsApp = { currentTab = AppNavTab.WHATSAPP }
                )
                AppNavTab.WHATSAPP -> WhatsAppScreen(
                    viewModel = viewModel,
                    config = config
                )
                AppNavTab.WALLETS -> WalletsScreen(
                    viewModel = viewModel,
                    config = config
                )
                AppNavTab.CONNECTION -> ConnectionScreen(
                    viewModel = viewModel,
                    config = config,
                    testResult = testResult,
                    isTesting = isTesting
                )
                AppNavTab.LOGS -> LogsScreen(
                    viewModel = viewModel,
                    logs = allLogs,
                    selectedLog = selectedLog
                )
                AppNavTab.SIMULATOR -> SimulatorScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
