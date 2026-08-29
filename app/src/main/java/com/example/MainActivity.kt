package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusSuccess
import com.example.util.KeepAliveHelper

enum class AppNavTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    WALLETS("Wallets", Icons.Default.Tune),
    CONNECTION("Connection", Icons.Default.CloudSync),
    LOGS("Live Logs", Icons.Default.ReceiptLong),
    SIMULATOR("Simulator", Icons.Default.BugReport)
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

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        Log.d("MainActivity", "Permission auto-request result: allGranted=$allGranted, details=$result")
        if (allGranted) {
            // Automatically start the foreground service once permissions are granted
            SyncForegroundService.start(this)
            viewModel.refreshServiceState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.refreshServiceState()

        // 1. Auto-request all runtime permissions on startup
        checkAndRequestAllPermissions()

        // 2. Auto-prompt battery optimization exemption for 24/7 background run if not yet granted
        if (!KeepAliveHelper.isBatteryOptimizationIgnored(this)) {
            KeepAliveHelper.requestIgnoreBatteryOptimization(this)
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
        if (missingPermissions.isNotEmpty()) {
            permissionRequestLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Already have permissions, ensure service is running
            SyncForegroundService.start(this)
            viewModel.refreshServiceState()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshServiceState()
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermissions = permissions[Manifest.permission.RECEIVE_SMS] == true
        if (hasSmsPermissions) {
            SyncForegroundService.start(context)
            viewModel.refreshServiceState()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
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
                        modifier = Modifier.padding(end = 8.dp)
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppNavTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(imageVector = tab.icon, contentDescription = tab.title)
                        },
                        label = {
                            Text(text = tab.title, fontSize = 10.sp, fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal)
                        },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission Alert Banner if not granted
            if (!hasSmsPermissions) {
                Surface(
                    color = StatusFailed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusFailed, modifier = Modifier.size(20.dp))
                            Text(
                                text = "SMS & Notification permissions required for 24/7 AutoSync.",
                                fontSize = 12.sp,
                                color = StatusFailed
                            )
                        }
                        Button(
                            onClick = {
                                onRecheckPermissions()
                                val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Allow All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    onNavigateToSimulator = { currentTab = AppNavTab.SIMULATOR }
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
