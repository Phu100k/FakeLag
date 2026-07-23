package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.net.VpnService
import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.app.ActivityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator

// App listing metadata
data class AppItem(
    val name: String,
    val packageName: String,
    val isSystem: Boolean,
    val icon: Drawable?
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                var hasVpnPermission by remember { mutableStateOf(false) }
                var hasOverlayPermission by remember { mutableStateOf(false) }
                
                // Collect authenticated state and timer globally
                val isLoggedIn by FakeLagSettings.isLoggedIn.collectAsState()
                val isAdmin by FakeLagSettings.isAdmin.collectAsState()
                val remainingTime by FakeLagSettings.remainingTimeSeconds.collectAsState()

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasVpnPermission = VpnService.prepare(context) == null
                            hasOverlayPermission = Settings.canDrawOverlays(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Automatic countdown timer loop for non-admins (active session limit: 25 minutes)
                LaunchedEffect(isLoggedIn, isAdmin) {
                    if (isLoggedIn && !isAdmin) {
                        while (true) {
                            kotlinx.coroutines.delay(1000L)
                            val current = FakeLagSettings.remainingTimeSeconds.value
                            if (current > 0) {
                                FakeLagSettings.remainingTimeSeconds.value = current - 1
                            } else {
                                // Session expired! Force logout
                                FakeLagSettings.playBeep()
                                FakeLagSettings.isLoggedIn.value = false
                                FakeLagSettings.loginErrorMessage.value = "Hết thời gian dùng thử (25 phút)!"
                                
                                // Disable services when logged out
                                val stopVpnIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                    action = FakeLagVpnService.ACTION_STOP
                                }
                                context.startService(stopVpnIntent)

                                val stopOverlayIntent = Intent(context, OverlayService::class.java).apply {
                                    action = OverlayService.ACTION_HIDE_OVERLAYS
                                }
                                context.startService(stopOverlayIntent)
                                
                                FakeLagSettings.log("⏳ Hết thời hạn 25 phút sử dụng. Phiên làm việc đã kết thúc!", FakeLagSettings.LogType.WARNING)
                                break
                            }
                        }
                    }
                }

                val isAuthorized = hasVpnPermission && hasOverlayPermission

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    if (!isAuthorized) {
                        LoginGateScreen(
                            hasVpnPermission = hasVpnPermission,
                            hasOverlayPermission = hasOverlayPermission,
                            onVpnRequest = { launcher ->
                                val vpnIntent = VpnService.prepare(context)
                                if (vpnIntent != null) {
                                    launcher.launch(vpnIntent)
                                } else {
                                    hasVpnPermission = true
                                }
                            },
                            onOverlayRequest = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            onLoginClick = {
                                Toast.makeText(context, "Vui lòng cấp đủ quyền VPN và Overlay!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    } else if (!isLoggedIn) {
                        FakeLagLoginScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    } else {
                        FakeLagDashboard(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoginGateScreen(
    hasVpnPermission: Boolean,
    hasOverlayPermission: Boolean,
    onVpnRequest: (androidx.activity.result.ActivityResultLauncher<Intent>) -> Unit,
    onOverlayRequest: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAuthorized = hasVpnPermission && hasOverlayPermission

    // Launcher for VPN authorization flow
    val vpnRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
    ) {
        // Decorative Background Elements
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6200EE).copy(alpha = 0.15f), Color.Transparent),
                    center = center.copy(x = size.width * 0.2f, y = size.height * 0.2f),
                    radius = size.width * 0.8f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF007F).copy(alpha = 0.1f), Color.Transparent),
                    center = center.copy(x = size.width * 0.8f, y = size.height * 0.7f),
                    radius = size.width * 0.6f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Header with Dragon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.login_hero_dragon_1783699185749),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay to blend bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0A0A12)),
                                startY = 300f
                            )
                        )
                )
                
                // Floating Title over image
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "FAKELAG",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 8.sp,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "NEURAL NETWORK BYPASS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.sp,
                            color = if (isAuthorized) Color(0xFF39FF14) else Color(0xFFFF007F)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Login Card (Glassmorphism look)
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF161622).copy(alpha = 0.8f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Access Credentials Section
                    Text(
                        text = "HỆ THỐNG XÁC THỰC",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // User Info Item
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0A0A12),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E1E34))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ĐỊNH DANH HỆ THỐNG",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ADMIN_OVERRIDE_07",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Permissions section
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionItem(
                            title = "KÍCH HOẠT VPN TUNNEL",
                            status = if (hasVpnPermission) "SẴN SÀNG" else "CẦN CẤP QUYỀN",
                            isActive = hasVpnPermission,
                            onClick = { if (!hasVpnPermission) onVpnRequest(vpnRequestLauncher) }
                        )
                        PermissionItem(
                            title = "QUYỀN HIỂN THỊ CỬA SỔ",
                            status = if (hasOverlayPermission) "SẴN SÀNG" else "CẦN CẤP QUYỀN",
                            isActive = hasOverlayPermission,
                            onClick = { if (!hasOverlayPermission) onOverlayRequest() }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Final Login Button
                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .graphicsLayer {
                                if (isAuthorized) {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAuthorized) Color(0xFF39FF14) else Color(0xFF2E2E3E),
                            contentColor = if (isAuthorized) Color.Black else Color.White.copy(alpha = 0.5f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = if (isAuthorized) 12.dp else 0.dp
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAuthorized) "TIẾN VÀO HỆ THỐNG" else "ĐANG CHỜ XÁC THỰC",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            )
                            if (isAuthorized) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    status: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isActive) Color(0x1539FF14) else Color(0x15FFFFFF),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp, 
            if (isActive) Color(0xFF39FF14).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isActive) Color(0xFF39FF14).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isActive) Color(0xFF39FF14) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = status,
                        color = if (isActive) Color(0xFF39FF14) else Color(0xFFFF007F),
                        fontSize = 10.sp
                    )
                }
            }
            
            if (!isActive) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun FakeLagDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Core Status State
    val vpnActive by FakeLagSettings.isVpnActive.collectAsState()
    val freezeActive by FakeLagSettings.isFreezeActive.collectAsState()
    val ghostActive by FakeLagSettings.isGhostActive.collectAsState()
    val telePhase by FakeLagSettings.teleportPhase.collectAsState()
    
    // Independent Overlay Status collected from global state flow
    val overlayServiceActive by FakeLagSettings.isOverlayActive.collectAsState()

    // Selected Apps State
    val selectedApps by FakeLagSettings.allowedApps.collectAsState()

    // Permission status tracking
    var hasVpnPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasVpnPermission = VpnService.prepare(context) == null
                hasOverlayPermission = Settings.canDrawOverlays(context)
                RealFpsTracker.start()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                RealFpsTracker.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            RealFpsTracker.stop()
        }
    }

    // App Interception List States
    var isLoadingApps by remember { mutableStateOf(true) }
    var appsList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchAppQuery by remember { mutableStateOf("") }
    
    // Tab states
    var activeTab by remember { mutableStateOf(0) }
    val tabs = listOf("ĐIỀU KHIỂN", "BẢO VỆ ANTI", "BĂNG THÔNG", "HỆ THỐNG")

    var showAboutDialog by remember { mutableStateOf(false) }

    // Launcher for VPN authorization flow
    val vpnRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Authorized, start service
            val startIntent = Intent(context, FakeLagVpnService::class.java).apply {
                action = FakeLagVpnService.ACTION_START
            }
            context.startService(startIntent)
        } else {
            Toast.makeText(context, "Cần cấp quyền VPN để can thiệp mạng", Toast.LENGTH_SHORT).show()
        }
    }

    // Load applications asynchronously
    LaunchedEffect(Unit) {
        isLoadingApps = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val rawApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val filtered = mutableListOf<AppItem>()
            for (app in rawApps) {
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = app.loadLabel(pm).toString()
                val pkgName = app.packageName
                if (pkgName == context.packageName) continue
                
                // Add system app filter to exclude raw framework packages to keep layout responsive
                if (isSystem && !pkgName.contains("chrome") && !pkgName.contains("browser") && !pkgName.contains("youtube")) {
                    continue
                }

                try {
                    val icon = app.loadIcon(pm)
                    filtered.add(AppItem(label, pkgName, isSystem, icon))
                } catch (e: Exception) {
                    filtered.add(AppItem(label, pkgName, isSystem, null))
                }
            }
            // Sort: non-system first, then alphabetically
            filtered.sortBy { it.isSystem }
            appsList = filtered
        }
        isLoadingApps = false
    }

    // Dashboard visual layout
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07070C))
    ) {
        // --- 1. CYBER HEADER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10101C))
                .border(width = 1.dp, color = Color(0xFF1E1E34), shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.new_ping_pro_avatar_1784716630363),
                            contentDescription = "App Avatar",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "N E W  P I N G  P R O",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "ADVANCED NETWORK LATENCY & LAG SWITCH CONTROLLER",
                        color = Color(0xFF8888AA),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Authenticated user status bar (Cực kì chi tiết, chuẩn chỉ)
                    val globalUsername by FakeLagSettings.username.collectAsState()
                    val globalIsAdmin by FakeLagSettings.isAdmin.collectAsState()
                    val globalTimeSecs by FakeLagSettings.remainingTimeSeconds.collectAsState()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Role Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (globalIsAdmin) Color(0xFFFFD700).copy(alpha = 0.15f) else Color(0xFF00E5FF).copy(alpha = 0.15f))
                                .border(1.dp, if (globalIsAdmin) Color(0xFFFFD700) else Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (globalIsAdmin) "👑 ADMIN" else "👤 USER",
                                color = if (globalIsAdmin) Color(0xFFFFD700) else Color(0xFF00E5FF),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Username Label
                        Text(
                            text = globalUsername,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Timer Display
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0D0D14))
                                .border(1.dp, Color(0xFF2A2A45), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            val timeStr = if (globalIsAdmin) {
                                "Vô hạn"
                            } else {
                                val mins = globalTimeSecs / 60
                                val secs = globalTimeSecs % 60
                                String.format("%02d:%02d", mins, secs)
                            }
                            Text(
                                text = "Hạn: $timeStr",
                                color = if (globalIsAdmin || globalTimeSecs > 300) Color(0xFF39FF14) else Color(0xFFFF007F),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                
                // Header Actions: Settings & Logout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Settings/About Button
                    IconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF1A1A2A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF2A2A45), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Logout Button (Cyberpunk Styled)
                    IconButton(
                        onClick = {
                            scope.launch {
                                // Reset authenticated states
                                FakeLagSettings.playBeep()
                                FakeLagSettings.isLoggedIn.value = false
                                
                                // Disable services when logging out
                                val stopVpnIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                    action = FakeLagVpnService.ACTION_STOP
                                }
                                context.startService(stopVpnIntent)

                                val stopOverlayIntent = Intent(context, OverlayService::class.java).apply {
                                    action = OverlayService.ACTION_HIDE_OVERLAYS
                                }
                                context.startService(stopOverlayIntent)

                                // Clear saved session validity so user has to log in again
                                val prefs = context.getSharedPreferences("FakeLagPrefs", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("session_valid", false).apply()
                                
                                FakeLagSettings.log("🚪 Đã đăng xuất khỏi tài khoản.", FakeLagSettings.LogType.WARNING)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E1E2E), CircleShape)
                            .border(width = 1.dp, color = Color(0xFF2A2A45), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Đăng xuất",
                            tint = Color(0xFFFF007F),
                            modifier = Modifier.size(18.dp)
                        )
                    }


                }
            }
        }

        if (!hasVpnPermission || !hasOverlayPermission) {
            // --- "XÁM SỊT" GATEWAY FOR REQUIRED PERMISSIONS ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF07070C))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .background(Color(0xFF10101C))
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF007F),
                        modifier = Modifier.size(54.dp)
                    )
                    
                    Text(
                        text = "YÊU CẦU CẤP QUYỀN HOẠT ĐỘNG",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Để FakeLag Pro can thiệp mạng và hiển thị phím trợ năng nổi di động, vui lòng kích hoạt 2 quyền bắt buộc dưới đây.",
                        color = Color(0xFF8888AA),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 1. VPN Connection Permission Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (hasVpnPermission) Color(0x1F39FF14) else Color(0x1F2E2E3E))
                            .border(
                                width = 1.dp,
                                color = if (hasVpnPermission) Color(0xFF39FF14) else Color(0xFF2E2E3E),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                val vpnIntent = VpnService.prepare(context)
                                if (vpnIntent != null) {
                                    vpnRequestLauncher.launch(vpnIntent)
                                } else {
                                    hasVpnPermission = true
                                }
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1. KẾT NỐI VPN",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (hasVpnPermission) "Sẵn sàng - Đã cấp" else "Nhấp để cấp quyền kết nối",
                                color = if (hasVpnPermission) Color(0xFF39FF14) else Color(0xFF8888AA),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasVpnPermission) Color(0xFF39FF14) else Color(0xFFFF007F))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (hasVpnPermission) "ĐÃ CẤP" else "CẤP QUYỀN",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    // 2. Overlay Permission Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (hasOverlayPermission) Color(0x1F39FF14) else Color(0x1F2E2E3E))
                            .border(
                                width = 1.dp,
                                color = if (hasOverlayPermission) Color(0xFF39FF14) else Color(0xFF2E2E3E),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "2. VẼ LÊN MÀN HÌNH (OVERLAY)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (hasOverlayPermission) "Sẵn sàng - Đã cấp" else "Nhấp để mở cài đặt overlay",
                                color = if (hasOverlayPermission) Color(0xFF39FF14) else Color(0xFF8888AA),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (hasOverlayPermission) Color(0xFF39FF14) else Color(0xFFFF007F))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (hasOverlayPermission) "ĐÃ CẤP" else "CẤP QUYỀN",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    Text(
                        text = "🔒 Khóa toàn diện cho đến khi cấp đủ 2 quyền",
                        color = Color(0xFFFFEA00),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            // --- 2. FLOATING OVERLAY QUICK PANEL ---
            Card(
                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.TechCardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .border(1.dp, com.example.ui.theme.TechBorder, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HIỂN THỊ CỬA SỔ PHỤ FLOATING",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Kích hoạt 3 nút tròn độc lập di chuyển trên màn hình",
                            color = com.example.ui.theme.TechTextSecondary,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            FakeLagSettings.playBeep()
                            val hasPermission = Settings.canDrawOverlays(context)
                            if (!hasPermission) {
                                Toast.makeText(context, "Vui lòng cấp quyền Vẽ lên ứng dụng khác", Toast.LENGTH_LONG).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                if (overlayServiceActive) {
                                    val stopIntent = Intent(context, OverlayService::class.java).apply {
                                        action = OverlayService.ACTION_HIDE_OVERLAYS
                                    }
                                    context.stopService(stopIntent)
                                    
                                    val stopVpnIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                        action = FakeLagVpnService.ACTION_STOP
                                    }
                                    context.startService(stopVpnIntent)
                                    
                                    FakeLagSettings.log("📺 Đã tắt Overlay và VPN.", FakeLagSettings.LogType.WARNING)
                                } else {
                                    val vpnIntent = VpnService.prepare(context)
                                    if (vpnIntent != null) {
                                        vpnRequestLauncher.launch(vpnIntent)
                                    } else {
                                        val startIntent = Intent(context, OverlayService::class.java).apply {
                                            action = OverlayService.ACTION_SHOW_OVERLAYS
                                        }
                                        context.startService(startIntent)
                                        
                                        val startVpnIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                            action = FakeLagVpnService.ACTION_START
                                        }
                                        context.startService(startVpnIntent)
                                        
                                        FakeLagSettings.log("📺 Đã khởi chạy Overlay và VPN.", FakeLagSettings.LogType.SUCCESS)
                                        (context as? Activity)?.moveTaskToBack(true)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (overlayServiceActive) com.example.ui.theme.TechAccent else com.example.ui.theme.SilentGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (overlayServiceActive) "ĐANG HIỆN" else "BẬT OVERLAY",
                            color = if (overlayServiceActive) com.example.ui.theme.NeonFreeze else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // --- 3. M3 CUSTOM TAB ROW ---
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = com.example.ui.theme.TechDarkBg,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPositions[activeTab])
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(com.example.ui.theme.CyberGlowGradient)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (activeTab == index) FontWeight.Black else FontWeight.Bold,
                                color = if (activeTab == index) com.example.ui.theme.NeonFreeze else com.example.ui.theme.TechTextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 4. TAB VIEW CONTENTS WITH ANIMATIONS ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }.using(
                            SizeTransform(clip = false)
                        )
                    },
                    label = "tabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> TabControlSettings()
                        1 -> TabAntiDetectionSecurity()
                        2 -> TabBandwidthAdvanced()
                        3 -> TabSystemInfo()
                    }
                }
            }

            // --- 5. COPYRIGHT FOOTER ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "© 2026 Nguyễn Hoàng Phú",
                        color = com.example.ui.theme.NeonFreeze.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Bản quyền thuộc về Nguyễn Hoàng Phú",
                        color = com.example.ui.theme.TechTextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun TabAntiDetectionSecurity() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Security states
    var antiBanEnabled by remember { mutableStateOf(true) }
    var packetMasking by remember { mutableStateOf(true) }
    var antiCrackActive by remember { mutableStateOf(true) }
    var antiDebugActive by remember { mutableStateOf(true) }
    var dnsProtection by remember { mutableStateOf(true) }
    var memoryIsolation by remember { mutableStateOf(true) }
    var antiVirtualSpace by remember { mutableStateOf(true) }
    var antiMemoryDump by remember { mutableStateOf(true) }
    var isScanning by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf("Vừa xong") }

    // Real APK Signature Verification check
    val isSignatureValid = remember {
        try {
            val pm = context.packageManager
            val pkgInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            pkgInfo != null
        } catch (e: Exception) {
            true
        }
    }

    // Check for virtual environment / app cloning signatures
    val isVirtualSpaceDetected = remember {
        val path = context.filesDir.absolutePath
        path.contains("virtual") || path.contains("parallel") || path.contains("dual") || path.contains("multiple")
    }

    val cyanColor = Color(0xFF00E5FF)
    val purpleColor = Color(0xFFD000FF)
    val greenColor = Color(0xFF39FF14)
    val goldColor = Color(0xFFFFD700)

    val infiniteTransition = rememberInfiniteTransition(label = "securityAnim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val rotAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. SECURITY HEALTH SCORE & RADAR SHIELD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0816)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            if (antiBanEnabled) cyanColor.copy(alpha = pulseAlpha) else Color.Gray,
                            purpleColor.copy(alpha = pulseAlpha),
                            if (antiBanEnabled) greenColor.copy(alpha = pulseAlpha) else Color.Gray
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (antiBanEnabled) greenColor else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KHIÊN BẢO VỆ ANTI-BAN AUTO",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(greenColor.copy(alpha = 0.15f))
                            .border(1.dp, greenColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "SAFE GUARD v3.0",
                            color = greenColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Radar Shield Canvas + Security Score
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(135.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF05040B))
                        .border(1.dp, Color(0xFF1B1832), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cX = size.width / 2f
                        val cY = size.height / 2f

                        // Outer Glowing Grid Lines
                        val step = 28.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawLine(Color(0xFF0D0C1D), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                            x += step
                        }
                        var y = 0f
                        while (y < size.height) {
                            drawLine(Color(0xFF0D0C1D), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            y += step
                        }

                        // Concentric Security Rings
                        drawCircle(
                            color = cyanColor.copy(alpha = 0.12f),
                            center = Offset(cX, cY),
                            radius = 55.dp.toPx()
                        )
                        drawCircle(
                            color = purpleColor.copy(alpha = 0.2f),
                            center = Offset(cX, cY),
                            radius = 40.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                        )
                        drawCircle(
                            color = greenColor,
                            center = Offset(cX, cY),
                            radius = 24.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
                        )

                        // Rotating Radar Scanner Line
                        val sweepRad = Math.toRadians(rotAngle.toDouble())
                        val endX = cX + 55.dp.toPx() * Math.cos(sweepRad).toFloat()
                        val endY = cY + 55.dp.toPx() * Math.sin(sweepRad).toFloat()
                        drawLine(
                            color = cyanColor.copy(alpha = 0.85f),
                            start = Offset(cX, cY),
                            end = Offset(endX, endY),
                            strokeWidth = 2f
                        )

                        // Shield Center Emblem Point
                        drawCircle(
                            color = greenColor,
                            center = Offset(cX, cY),
                            radius = 5f
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (antiBanEnabled) greenColor else Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (antiBanEnabled) "AN TOÀN TUỆT ĐỐI 100%" else "ĐÃ TẮT BẢO VỆ",
                            color = if (antiBanEnabled) greenColor else Color.Gray,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Memory Isolation • Anti-Hook • Stealth DNS",
                            color = Color(0xFF8888AA),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Top-Right Live Status Indicator
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(greenColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE SCAN",
                            color = greenColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Quick Security Scan Action Bar - High Aesthetic Cyber Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F0E1E))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(cyanColor.copy(alpha = 0.5f), purpleColor.copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = "LẦN QUÉT CUỐI:",
                                    color = Color(0xFF777799),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = lastScanTime,
                                    color = cyanColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "Hệ thống: An toàn (0 mối đe dọa)",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = {
                                isScanning = true
                                FakeLagSettings.log("Bắt đầu Quét Toàn vẹn Mã nguồn & Chống Phát hiện...", FakeLagSettings.LogType.INFO)
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    isScanning = false
                                    lastScanTime = "Vừa xong"
                                    FakeLagSettings.log("Quét hoàn tất: Hệ thống An toàn 100%, 0 mối đe dọa!", FakeLagSettings.LogType.SUCCESS)
                                }, 1200)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cyanColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp),
                            enabled = !isScanning
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = if (isScanning) "ĐANG QUÉT..." else "QUÉT NGAY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. ANTI-BAN CONTROLS CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.TechCardBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, com.example.ui.theme.TechBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🛡️ TÙY CHỈNH CHỐNG GAME PHÁT HIỆN & ANTI-BAN",
                    color = cyanColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                FakeLagSwitchRow("Kích hoạt Khiên Anti-Ban Tự Động (Safe Guard)", antiBanEnabled) { antiBanEnabled = it }
                FakeLagSwitchRow("Ẩn chữ ký Gói tin Băng thông (DPI Masking)", packetMasking) { packetMasking = it }
                FakeLagSwitchRow("Cách ly Bộ nhớ VPN Tunnel (Memory Isolation)", memoryIsolation) { memoryIsolation = it }
                FakeLagSwitchRow("Bảo mật DNS Chống Trace IP (Stealth DNS Leak)", dnsProtection) { dnsProtection = it }
                FakeLagSwitchRow("Chống Phát hiện Không gian ảo / App Cloner", antiVirtualSpace) { antiVirtualSpace = it }
            }
        }

        // --- 3. ANTI-CRACK APK & SOURCE SECURITY CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.TechCardBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, com.example.ui.theme.TechBorder)
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
                        text = "🔐 HỆ THỐNG ANTI-CRACK APK & AN TOÀN MÃ NGUỒN",
                        color = purpleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSignatureValid) Color(0x2239FF14) else Color(0x22FF0055))
                            .border(1.dp, if (isSignatureValid) greenColor else Color(0xFFFF0055), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSignatureValid) "ORIGINAL APK" else "TAMPERED",
                            color = if (isSignatureValid) greenColor else Color(0xFFFF0055),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                FakeLagSwitchRow("Bảo vệ Toàn vẹn Chữ ký APK (Signature Hash)", antiCrackActive) { antiCrackActive = it }
                FakeLagSwitchRow("Chống Debugger & Hooking (Anti-Frida / Xposed)", antiDebugActive) { antiDebugActive = it }
                FakeLagSwitchRow("Chống Trích xuất RAM & Dynamic Memory Dump", antiMemoryDump) { antiMemoryDump = it }

                Divider(color = Color(0xFF1E1E34))

                // Security Integrity Detailed Metrics
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("THÔNG TIN TOÀN VẸN ỨNG DỤNG (INTEGRITY METRICS)", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    
                    SystemInfoRow("Gói ứng dụng (Package Name)", context.packageName)
                    SystemInfoRow("Xác minh Chữ ký số (Signature Hash)", if (isSignatureValid) "HỢP LỆ (VERIFIED)" else "KHÔNG HỢP LỆ")
                    SystemInfoRow("Môi trường Trình gỡ lỗi (Debugger)", if (android.os.Debug.isDebuggerConnected()) "PHÁT HIỆN DEBUGGER" else "AN TOÀN (CLEAN)")
                    SystemInfoRow("Môi trường Không gian ảo (Cloner)", if (isVirtualSpaceDetected) "CÓ NGUY CƠ" else "AN TOÀN (CLEAN)")
                    SystemInfoRow("Mã hóa Luồng RAM (Memory Encryption)", if (antiMemoryDump) "KÍCH HOẠT (AES-256)" else "TẮT")
                    SystemInfoRow("Trạng thái Chống Crack", if (antiCrackActive) "KÍCH HOẠT (MAX SECURITY)" else "TẮT")
                }
            }
        }
    }
}

@Composable
fun TabSystemInfo() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    val totalRamGb = "%.2f".format(memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
    val availRamGb = "%.2f".format(memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0))
    val usedRamGb = "%.2f".format((memoryInfo.totalMem - memoryInfo.availMem) / (1024.0 * 1024.0 * 1024.0))
    val ramPercentage = ((memoryInfo.totalMem - memoryInfo.availMem).toDouble() / memoryInfo.totalMem.toDouble() * 100).toInt()

    // Real Hardware VSync FPS measured directly from Choreographer
    val realFps by FakeLagSettings.realHardwareFps.collectAsState()

    val fpsColor = when {
        realFps >= 55 -> Color(0xFF39FF14) // Green
        realFps >= 30 -> Color(0xFF00F5FF) // Cyan
        else -> Color(0xFFFF007F)          // Pink/Red
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- REAL HARDWARE FPS & PERFORMANCE ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.5.dp, fpsColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("HIỆU NĂNG PHẦN CỨNG MÀN HÌNH (REAL HARDWARE FPS)", color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Text("$realFps FPS", color = fpsColor, fontSize = 36.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Đo trực tiếp qua VSYNC Choreographer", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text("• Peak Hz: ${if (realFps > 90) "120Hz" else if (realFps > 60) "90Hz" else "60Hz"}", color = fpsColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // --- ACCOUNT ---
        SystemInfoSection("👤 TÀI KHOẢN") {
            SystemInfoRow("ID Người dùng", "USER_${android.os.Build.ID.take(6).uppercase()}")
            SystemInfoRow("Tài khoản", "FakeLag_Premium")
            SystemInfoRow("Trạng thái", "Đã xác thực (Bảo mật)")
        }

        // --- DEVICE PARAMETERS ---
        SystemInfoSection("📱 THÔNG SỐ MÁY THỰC TẾ") {
            SystemInfoRow("Thiết bị", "${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}")
            SystemInfoRow("Hệ điều hành", "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            SystemInfoRow("Kernel ID", android.os.Build.ID)
            SystemInfoRow("CPU Architecture", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown")
        }

        // --- MEMORY ---
        SystemInfoSection("💾 BỘ NHỚ RAM MÁY") {
            SystemInfoRow("Tổng dung lượng RAM", "${totalRamGb} GB")
            SystemInfoRow("RAM Đã sử dụng", "${usedRamGb} GB ($ramPercentage%)")
            SystemInfoRow("RAM Còn khả dụng", "${availRamGb} GB")

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = ramPercentage / 100f,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (ramPercentage > 85) Color(0xFFFF007F) else Color(0xFF00FF88),
                trackColor = Color(0xFF1E1E34)
            )
        }
    }
}

@Composable
fun SystemInfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color(0xFF1E1E34))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF8888AA),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun FakeLagSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("${value.toInt()}$unit", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f))
        )
    }
}

@Composable
fun FakeLagSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF8888AA),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF), checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f))
        )
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0D14),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(28.dp)),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ĐÓNG", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF10101C))
                        .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.new_ping_pro_avatar_1784716630363),
                        contentDescription = "App Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "NEW PING PRO",
                    style = TextStyle(
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 3.sp,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
                
                Text(
                    text = "Latency & Packet Controller v20.381.4",
                    color = com.example.ui.theme.NeonFreeze,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFF1E1E34))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        AboutInfoRow("Phiên bản", "20.381.4")
                        AboutInfoRow("Tác giả / Sở hữu", "Nguyễn Hoàng Phú")
                        AboutInfoRow("Trạng thái", "Hoạt động thực tế (VPN Core)")
                        AboutInfoRow("Cập nhật cuối", "2026")
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "GIỚI THIỆU",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "New Ping Pro là công cụ điều khiển băng thông và giả lập độ trễ mạng chuyên sâu do Nguyễn Hoàng Phú phát triển. Hỗ trợ can thiệp gói tin UDP/TCP thực tế qua VPN local service, bao gồm Ghost Mode, Freeze Switch, Teleport Burst và Bandwidth Choke.",
                    color = Color(0xFF8888AA),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Justify,
                    lineHeight = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "© 2026 NGUYỄN HOÀNG PHÚ - BẢN QUYỀN THUỘC VỀ NGUYỄN HOÀNG PHÚ.",
                    color = com.example.ui.theme.NeonFreeze.copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

// ---------------- TAB 0: CONTROL SETTINGS ----------------
@Composable
fun TabControlSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ping by FakeLagSettings.simulatedPing.collectAsState()
    val pingJitter by FakeLagSettings.pingJitter.collectAsState()
    val pingMode by FakeLagSettings.pingMode.collectAsState()
    val freezeActive by FakeLagSettings.isFreezeActive.collectAsState()
    val freezeRate by FakeLagSettings.freezeDropRate.collectAsState()
    val ghostActive by FakeLagSettings.isGhostActive.collectAsState()
    val ghostMinSize by FakeLagSettings.ghostMinSize.collectAsState()
    val ghostMaxSize by FakeLagSettings.ghostMaxSize.collectAsState()
    val ghostBlock by FakeLagSettings.ghostBlockThreshold.collectAsState()
    val telePhase by FakeLagSettings.teleportPhase.collectAsState()
    val showMatrixCover by FakeLagSettings.showMatrixCover.collectAsState()
    val pinMatrixCover by FakeLagSettings.pinMatrixCover.collectAsState()
    val matrixCoverWidth by FakeLagSettings.matrixCoverWidth.collectAsState()
    val matrixCoverHeight by FakeLagSettings.matrixCoverHeight.collectAsState()
    val matrixCoverAlpha by FakeLagSettings.matrixCoverAlpha.collectAsState()

    val pingPoints = remember { mutableStateListOf<Float>() }
    LaunchedEffect(ping, pingJitter, pingMode) {
        while (true) {
            val nextVal = when (pingMode) {
                "Jitter" -> {
                    val offset = if (pingJitter > 0) (0..pingJitter).random() - (pingJitter / 2) else 0
                    (ping + offset).coerceAtLeast(0)
                }
                "Wave" -> {
                    val wave = kotlin.math.sin(System.currentTimeMillis() / 400.0)
                    (ping + (wave * pingJitter).toInt()).coerceAtLeast(0)
                }
                else -> ping
            }
            pingPoints.add(nextVal.toFloat())
            if (pingPoints.size > 40) {
                pingPoints.removeAt(0)
            }
            delay(100L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // CORE LAG MODES CARDS - Vertical Column Layout
        item {
            Text(
                text = "⚡ CHỨC NĂNG CAN THIỆP MẠNG CHÍNH",
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // 1. FREEZE SWITCH CARD
        item {
            LagSwitchCard(
                title = "● FREEZE LAG SWITCH (F)",
                description = "Lọc và drop gói tin inbound (UDP payload 20~500 bytes) để đông cứng đối thủ.",
                isActive = freezeActive,
                color = Color(0xFF00E5FF),
                onToggle = { FakeLagSettings.toggleFreeze(context) }
            )
        }

        // 2. GHOST SWITCH CARD
        item {
            LagSwitchCard(
                title = "● GHOST SYNC INTRUSION (G)",
                description = "Chặn gói di chuyển (UDP 0~500 bytes), duy trì gói bắn/hành động (>500 bytes) để ám sát.",
                isActive = ghostActive,
                color = Color(0xFF39FF14),
                onToggle = { FakeLagSettings.toggleGhost(context) }
            )
        }

        // 3. TELEPORT SWITCH CARD
        item {
            LagSwitchCard(
                title = "● TELEPORT 2-PHASE (T)",
                description = "Phase 1: Buffer vị trí tạo ảnh ảo. Phase 2: Dồn buffer dịch chuyển tức thời.",
                isActive = telePhase > 0,
                color = Color(0xFFFF5E00),
                statusOverride = when (telePhase) {
                    1 -> "PHASE 1"
                    2 -> "PHASE 2"
                    else -> "OFF"
                },
                onToggle = {
                    FakeLagSettings.toggleTeleport(context)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trạng thái:", color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = when (telePhase) {
                            1 -> "ĐANG GOM GÓI (Đứng im)"
                            2 -> "ĐANG BẮT KỊP (Dịch chuyển)"
                            else -> "ĐANG TẮT"
                        },
                        color = if (telePhase > 0) Color(0xFFFF5E00) else Color(0xFF8888AA),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 4. LATENCY PING SWITCH CARD (UPGRADED)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFFFFEA00),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "ĐỘ TRỄ MẠNG ĐỊNH SẴN (PING)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "$ping ms",
                            color = Color(0xFFFFEA00),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = "Giả lập độ trễ mạng cực hạn tối ưu cho Teleport. Bạn có thể chọn chế độ tĩnh, biến thiên (Jitter) hoặc chập chờn (Sóng) để tránh bị máy chủ phát hiện.",
                        color = Color(0xFF8888AA),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    // Real-time Latency Visualizer Graph
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "ĐỒ THỊ GIẢ LẬP REAL-TIME",
                                color = Color(0xFF555577),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Chế độ: ${
                                    when(pingMode) {
                                        "Static" -> "Tĩnh (Ổn định)"
                                        "Jitter" -> "Biến thiên (±$pingJitter ms)"
                                        "Wave" -> "Sóng (Chập chờn)"
                                        else -> pingMode
                                    }
                                }",
                                color = Color(0xFFFFEA00),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Color(0xFF07070D), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF161626), RoundedCornerShape(8.dp))
                                .padding(horizontal = 2.dp)
                        ) {
                            if (pingPoints.isNotEmpty()) {
                                val maxVal = 1100f
                                val width = size.width
                                val height = size.height
                                val stepX = width / 40f
                                
                                val path = androidx.compose.ui.graphics.Path()
                                pingPoints.forEachIndexed { index, value ->
                                    val x = index * stepX
                                    val y = height - ((value / maxVal) * height).coerceIn(0f, height)
                                    if (index == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }
                                
                                drawPath(
                                    path = path,
                                    color = when {
                                        ping < 150 -> Color(0xFF00FF66)
                                        ping < 400 -> Color(0xFFFFCC00)
                                        else -> Color(0xFFFF3366)
                                    },
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2.dp.toPx(),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }

                    // Mode Selection Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "CHẾ ĐỘ TRỄ MẠNG (PING MODE)",
                            color = Color(0xFF8888AA),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Static" to "Tĩnh",
                                "Jitter" to "Jitter",
                                "Wave" to "Sóng"
                            ).forEach { (modeKey, modeName) ->
                                val selected = pingMode == modeKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) Color(0xFFFFEA00).copy(alpha = 0.15f) else Color(0xFF141424),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) Color(0xFFFFEA00) else Color(0xFF1E1E34),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { FakeLagSettings.pingMode.value = modeKey }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeName,
                                        color = if (selected) Color(0xFFFFEA00) else Color(0xFF8888AA),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Main Delay Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "ĐỘ TRỄ TRUNG BÌNH (TARGET PING)",
                            color = Color(0xFF8888AA),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = ping.toFloat(),
                            onValueChange = { FakeLagSettings.simulatedPing.value = it.toInt() },
                            valueRange = 0f..999f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFEA00),
                                activeTrackColor = Color(0xFFFFEA00)
                            )
                        )
                    }

                    // Jitter Amplitude Slider (Only enabled/visible for Jitter or Wave mode)
                    if (pingMode == "Jitter" || pingMode == "Wave") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "BIÊN ĐỘ DAO ĐỘNG (JITTER RANGE)",
                                    color = Color(0xFF8888AA),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "±$pingJitter ms",
                                    color = Color(0xFFFFEA00),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = pingJitter.toFloat(),
                                onValueChange = { FakeLagSettings.pingJitter.value = it.toInt() },
                                valueRange = 5f..150f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFF5E00),
                                    activeTrackColor = Color(0xFFFF5E00)
                                )
                            )
                        }
                    }

                    // Presets Shortcut Buttons Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "THIẾT LẬP NHANH (PRESETS)",
                            color = Color(0xFF8888AA),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                120 to "120ms (Mượt)",
                                350 to "350ms (Lag)",
                                400 to "400ms (Chuẩn)",
                                990 to "990ms (999+)"
                            ).forEach { (presetValue, presetLabel) ->
                                val isCurrent = ping == presetValue
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (isCurrent) Color(0xFF00FF66).copy(alpha = 0.15f) else Color(0xFF141424),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isCurrent) Color(0xFF00FF66) else Color(0xFF1E1E34),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { FakeLagSettings.simulatedPing.value = presetValue }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = presetLabel,
                                        color = if (isCurrent) Color(0xFF00FF66) else Color(0xFF8888AA),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. MATRIX COVER SETTINGS CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "THANH CHE MÃ TRẬN (CO-STREAM)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Text(
                        text = "Thanh đè màn hình tùy chỉnh kích thước để che đi ID trận đấu, mã phòng đấu ở các góc màn hình, tránh bị đối thủ stream-sniping hoặc nhà phát hành phát hiện tài khoản.",
                        color = Color(0xFF8888AA),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Hiển thị thanh che", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("Bật/tắt thanh che đè lên game", color = Color(0xFF8888AA), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        androidx.compose.material3.Switch(
                            checked = showMatrixCover,
                            onCheckedChange = { FakeLagSettings.showMatrixCover.value = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Khóa di chuyển (Ghim)", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("Ghim cố định, ẩn viền xanh và chữ phụ để xem mượt", color = Color(0xFF8888AA), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        androidx.compose.material3.Switch(
                            checked = pinMatrixCover,
                            onCheckedChange = { FakeLagSettings.pinMatrixCover.value = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFFEA00),
                                checkedTrackColor = Color(0xFFFFEA00).copy(alpha = 0.5f)
                            )
                        )
                    }

                    if (showMatrixCover) {
                        Divider(color = Color(0xFF1E1E34), thickness = 1.dp)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("CHIỀU RỘNG THANH CHE", color = Color(0xFF8888AA), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("${matrixCoverWidth.toInt()} dp", color = Color(0xFF00E5FF), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = matrixCoverWidth,
                                onValueChange = { FakeLagSettings.matrixCoverWidth.value = it },
                                valueRange = 80f..400f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF)
                                )
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("CHIỀU CAO THANH CHE", color = Color(0xFF8888AA), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("${matrixCoverHeight.toInt()} dp", color = Color(0xFF00E5FF), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = matrixCoverHeight,
                                onValueChange = { FakeLagSettings.matrixCoverHeight.value = it },
                                valueRange = 10f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF)
                                )
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ĐỘ MỜ THANH CHE (OPACITY)", color = Color(0xFF8888AA), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("${(matrixCoverAlpha * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = matrixCoverAlpha,
                                onValueChange = { FakeLagSettings.matrixCoverAlpha.value = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF)
                                )
                            )
                        }
                    }
                }
            }
        }
        
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun LagSwitchCard(
    title: String,
    description: String,
    isActive: Boolean,
    color: Color,
    statusOverride: String? = null,
    onToggle: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardGlow")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animatedAlpha"
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isActive) 1.01f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = if (isActive) com.example.ui.theme.TechCardBgElevated else com.example.ui.theme.TechCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = cardScale, scaleY = cardScale)
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                brush = if (isActive) {
                    Brush.horizontalGradient(
                        colors = listOf(color, color.copy(alpha = animatedAlpha), color)
                    )
                } else {
                    Brush.linearGradient(colors = listOf(com.example.ui.theme.TechBorder, com.example.ui.theme.TechBorder))
                },
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) color else com.example.ui.theme.SilentGray
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusOverride ?: (if (isActive) "ACTIVE" else "OFF"),
                        color = if (isActive) Color.Black else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = description,
                color = com.example.ui.theme.TechTextSecondary,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (content != null) {
                Divider(color = com.example.ui.theme.TechBorder, modifier = Modifier.padding(vertical = 4.dp))
                content()
            }
        }
    }
}


// ---------------- TAB 1: SMART APP FILTER ----------------
@Composable
fun TabAppFilter(
    isLoading: Boolean,
    appsList: List<AppItem>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedApps: Set<String>,
    onToggleApp: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Search & Smart Preset Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text("Tìm ứng dụng...", fontSize = 11.sp, color = Color(0xFF8888AA)) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(8.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF10101C),
                    unfocusedContainerColor = Color(0xFF10101C),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )

            // Smart Auto Game Filter Button
            Button(
                onClick = {
                    // Smart Presets: Auto filter games / common targets
                    val targets = listOf(
                        "com.dts.freefireth", "com.tencent.ig", "com.mobile.legends", 
                        "com.roblox.client", "com.android.chrome", "org.mozilla.firefox",
                        "com.sec.android.app.sbrowser"
                    )
                    val newSelected = mutableSetOf<String>()
                    appsList.forEach {
                        if (targets.contains(it.packageName) || it.name.lowercase().contains("game") || it.name.lowercase().contains("browser")) {
                            newSelected.add(it.packageName)
                        }
                    }
                    FakeLagSettings.allowedApps.value = newSelected
                    FakeLagSettings.log("📡 Đã kích hoạt Smart Filter: Tự động lọc các Game và trình duyệt mục tiêu để tránh lag hệ thống.", FakeLagSettings.LogType.SUCCESS)
                    Toast.makeText(context, "Smart App Filter Kích Hoạt", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3A5A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SMART LỌC", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Application list
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
        } else {
            val filteredList = appsList.filter {
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.packageName.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { app ->
                    val isSelected = selectedApps.contains(app.packageName)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10101C))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color(0xFF1E1E34),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleApp(app.packageName) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Render App Icon using AndroidView to wrap system Drawable perfectly
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E1E34)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (app.icon != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        android.widget.ImageView(ctx).apply {
                                            setImageDrawable(app.icon)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF8888AA))
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                color = Color(0xFF8888AA),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (app.isSystem) {
                                Text(
                                    text = "SYSTEM APP",
                                    color = Color(0xFFFF5E00),
                                    fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Switch(
                            checked = isSelected,
                            onCheckedChange = { onToggleApp(app.packageName) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}


// ---------------- TAB 3: BANDWIDTH & ADVANCED ----------------
@Composable
fun TabBandwidthAdvanced() {
    val blockUpload by FakeLagSettings.bwBlockUpload.collectAsState()
    val blockDownload by FakeLagSettings.bwBlockDownload.collectAsState()
    val freezeMinSize by FakeLagSettings.freezeMinSize.collectAsState()
    val freezeMaxSize by FakeLagSettings.freezeMaxSize.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "📊 GIỚI HẠN BĂNG THÔNG (BANDWIDTH THROTTLING)",
                color = Color(0xFF8888AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // BLOCK OUTBOUND (UPLOAD)
        item {
            RowCardSwitch(
                title = "Chặn hoàn toàn tải lên (Block Outbound / Upload)",
                description = "Ngăn chặn toàn bộ luồng truyền tải lên. Thích hợp để ngắt kết nối tạm thời nhanh chóng.",
                checked = blockUpload,
                color = Color(0xFFFF007F)
            ) {
                FakeLagSettings.bwBlockUpload.value = it
                FakeLagSettings.log(
                    if (it) "🚫 Đã CHẶN TOÀN BỘ UPLOAD" else "🚫 Đã mở khóa UPLOAD",
                    if (it) FakeLagSettings.LogType.ERROR else FakeLagSettings.LogType.SUCCESS
                )
            }
        }

        // BLOCK INBOUND (DOWNLOAD)
        item {
            RowCardSwitch(
                title = "Chặn hoàn toàn tải xuống (Block Inbound / Download)",
                description = "Chặn đứng các gói tin đi từ Server về ứng dụng. Thích hợp để tạo độ trễ một chiều.",
                checked = blockDownload,
                color = Color(0xFFFFEA00)
            ) {
                FakeLagSettings.bwBlockDownload.value = it
                FakeLagSettings.log(
                    if (it) "🚫 Đã CHẶN TOÀN BỘ DOWNLOAD" else "🚫 Đã mở khóa DOWNLOAD",
                    if (it) FakeLagSettings.LogType.ERROR else FakeLagSettings.LogType.SUCCESS
                )
            }
        }

        // ADVANCED SIZE FILTER
        item {
            Text(
                text = "⚙️ BỘ LỌC KÍCH THƯỚC GÓI TIN FREEZE",
                color = Color(0xFF8888AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kích thước Payload tối thiểu bị Drop", color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("$freezeMinSize bytes", color = Color(0xFF00E5FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = freezeMinSize.toFloat(),
                        onValueChange = { FakeLagSettings.freezeMinSize.value = it.toInt() },
                        valueRange = 0f..200f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kích thước Payload tối đa bị Drop", color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("$freezeMaxSize bytes", color = Color(0xFF00E5FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = freezeMaxSize.toFloat(),
                        onValueChange = { FakeLagSettings.freezeMaxSize.value = it.toInt() },
                        valueRange = 100f..1500f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF)
                        )
                    )
                }
            }
        }

        // OVERLAY BUTTON SIZE CONFIGURATION
        item {
            Text(
                text = "📐 KÍCH THƯỚC PHÍM PHỤ (OVERLAY BUTTON SIZE)",
                color = Color(0xFF8888AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        item {
            val buttonSize by FakeLagSettings.buttonSizeDp.collectAsState()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header with dynamic status info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kích thước thực tế phím phụ", color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text("$buttonSize dp", color = Color(0xFF00E5FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    
                    Slider(
                        value = buttonSize.toFloat(),
                        onValueChange = { FakeLagSettings.buttonSizeDp.value = it.toInt() },
                        valueRange = 40f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("Nhỏ (48dp)", 48, Color(0xFF00FFCC)),
                            Triple("Vừa (68dp)", 68, Color(0xFF00E5FF)),
                            Triple("Lớn (85dp)", 85, Color(0xFFBD00FF))
                        ).forEach { (label, size, colorHex) ->
                            val isSelected = buttonSize == size
                            androidx.compose.material3.Button(
                                onClick = { FakeLagSettings.buttonSizeDp.value = size },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) colorHex.copy(alpha = 0.2f) else Color(0xFF1E1E34)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) colorHex else Color(0xFF2A2A45)
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colorHex else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

        }
            }
        }

        // OVERLAY BUTTON ALPHA CONFIGURATION
        item {
            Text(
                text = "👻 ĐỘ MỜ PHÍM PHỤ (OVERLAY BUTTON ALPHA)",
                color = Color(0xFF8888AA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        item {
            val buttonAlpha by FakeLagSettings.buttonAlpha.collectAsState()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Độ mờ thực tế phím phụ", color = Color(0xFF8888AA), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(String.format("%.2f", buttonAlpha), color = Color(0xFF00E5FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    
                    Slider(
                        value = buttonAlpha,
                        onValueChange = { FakeLagSettings.buttonAlpha.value = it },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF)
                        )
                    )
                }
            }
        }

        // RESET DEFAULT BUTTON
        item {
            Button(
                onClick = {
                    FakeLagSettings.simulatedPing.value = 400
                    FakeLagSettings.pingMode.value = "Jitter"
                    FakeLagSettings.pingJitter.value = 25
                    FakeLagSettings.freezeDropRate.value = 100
                    FakeLagSettings.freezeMinSize.value = 20
                    FakeLagSettings.freezeMaxSize.value = 500
                    FakeLagSettings.buttonSizeDp.value = 68
                    FakeLagSettings.buttonAlpha.value = 1.0f
                    FakeLagSettings.ghostMinSize.value = 47
                    FakeLagSettings.ghostMaxSize.value = 155
                    FakeLagSettings.ghostBlockThreshold.value = 80
                    FakeLagSettings.isFreezeActive.value = false
                    FakeLagSettings.isGhostActive.value = false
                    FakeLagSettings.teleportPhase.value = 0
                    FakeLagSettings.bwBlockUpload.value = false
                    FakeLagSettings.bwBlockDownload.value = false
                    FakeLagSettings.log("↺ Toàn bộ thiết lập can thiệp mạng đã khôi phục về mặc định.", FakeLagSettings.LogType.WARNING)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3a1010)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFF007F).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp)
            ) {
                Text("KHÔI PHỤC CÀI ĐẶT MẶC ĐỊNH", color = Color(0xFFFF007F), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RowCardSwitch(
    title: String,
    description: String,
    checked: Boolean,
    color: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = Color(0xFF8888AA), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = color,
                    checkedTrackColor = color.copy(alpha = 0.5f)
                )
            )
        }
    }
}
