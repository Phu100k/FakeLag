package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_SHOW_OVERLAYS = "com.example.SHOW_OVERLAYS"
        const val ACTION_HIDE_OVERLAYS = "com.example.HIDE_OVERLAYS"
        private const val CHANNEL_ID = "OverlayServiceChannel"
        private const val NOTIFICATION_ID = 9081
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private val overlayScope = CoroutineScope(Dispatchers.Main)

    // Separate layouts for the floating buttons
    private var controllerView: View? = null
    private var vpnView: View? = null
    private var freezeView: View? = null
    private var ghostView: View? = null
    private var teleportView: View? = null
    private var matrixCoverView: View? = null
    private var espFovView: View? = null

    // Layout params for each button
    private lateinit var controllerParams: WindowManager.LayoutParams
    private lateinit var vpnParams: WindowManager.LayoutParams
    private lateinit var freezeParams: WindowManager.LayoutParams
    private lateinit var ghostParams: WindowManager.LayoutParams
    private lateinit var teleportParams: WindowManager.LayoutParams
    private lateinit var matrixCoverParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAYS -> showOverlays()
            ACTION_HIDE_OVERLAYS -> hideOverlays()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlays()
        overlayScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    private fun showOverlays() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            FakeLagSettings.log("❌ Không có quyền hiển thị cửa sổ phụ. Vui lòng cấp quyền!", FakeLagSettings.LogType.ERROR)
            stopSelf()
            return
        }
        if (freezeView != null) return // Already showing

        RealFpsTracker.start()
        FakeLagSettings.isOverlayActive.value = true
        FakeLagSettings.log("📺 Đang hiển thị cửa sổ phụ (Floating Overlays)...", FakeLagSettings.LogType.INFO)
        startVpnServiceAuto()

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // Initialize Layout Params with default positions
        controllerParams = createLayoutParams(50, screenHeight / 4)
        vpnParams = createLayoutParams(50, screenHeight / 4 + 150)
        freezeParams = createLayoutParams(50, screenHeight / 4 + 300)
        ghostParams = createLayoutParams(50, screenHeight / 4 + 450)
        teleportParams = createLayoutParams(50, screenHeight / 4 + 600)
        matrixCoverParams = createLayoutParams(screenWidth / 2 - 120, screenHeight - 180)

        // 0. Controller Overlay (Bánh răng)
        controllerView = DraggableFrameLayout(this).apply {
            setupViewTreeOwners()
            params = controllerParams
            windowManager = this@OverlayService.windowManager
            isPinned = FakeLagSettings.pinControllerButton.value
            composeView.setContent {
                var isExpanded by remember { mutableStateOf(false) }
                var selectedTab by remember { mutableStateOf(0) }
                val btnSizeDp by FakeLagSettings.buttonSizeDp.collectAsState()
                val btnAlpha by FakeLagSettings.buttonAlpha.collectAsState()
                val globalScale by FakeLagSettings.buttonScale.collectAsState()
                val gearSize = (btnSizeDp * 0.8f).coerceIn(36f, 72f)
                val gearIconSize = (gearSize * 0.44f).coerceIn(16f, 32f)

                val infiniteRotationTransition = rememberInfiniteTransition(label = "gearRotation")
                val gearAngle by infiniteRotationTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(12000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "gearAngle"
                )
                
                if (!isExpanded) {
                    // Collapsed Gear Icon Button with Mystic Glow
                    Surface(
                        color = Color(0xEB0C0C1E).copy(alpha = 0.92f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(gearSize.dp)
                            .scale(globalScale)
                            .graphicsLayer(alpha = btnAlpha)
                            .border(
                                width = FakeLagSettings.borderThickness.value.dp,
                                brush = Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFF9D00FF), Color(0xFFFF007F), Color(0xFF00E5FF))),
                                shape = CircleShape
                            )
                            .clip(CircleShape)
                            .clickable { isExpanded = true }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open Overlay Settings",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier
                                    .size(gearIconSize.dp)
                                    .rotate(gearAngle)
                            )
                        }
                    }
                } else {
                    // Expanded Settings Panel Card
                    Surface(
                        color = Color(0xFA0B0B16),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .width(230.dp)
                            .border(1.5.dp, Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF9D00FF))), RoundedCornerShape(18.dp))
                            .padding(12.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header
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
                                            .background(Color(0xFF00E5FF))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ĐIỀU KHIỂN PHỤ",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Collapse Settings",
                                    tint = Color(0xFFFF007F),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { isExpanded = false }
                                )
                            }
                            
                            // Compact Sub-Tab Segmented Control
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF141424))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf("GHIM", "TỈ LỆ", "CHE").forEachIndexed { idx, title ->
                                    val isSelected = selectedTab == idx
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.Transparent)
                                            .clickable { selectedTab = idx }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF777799),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1E1E34)))
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (selectedTab) {
                                    0 -> {
                                        // TAB 0: GHIM & BẬT TẮT NÚT
                                        ButtonConfigRow("Nút VPN", FakeLagSettings.showVpnButton, FakeLagSettings.pinVpnButton)
                                        ButtonConfigRow("Nút Freeze", FakeLagSettings.showFreezeButton, FakeLagSettings.pinFreezeButton)
                                        ButtonConfigRow("Nút Ghost", FakeLagSettings.showGhostButton, FakeLagSettings.pinGhostButton)
                                        ButtonConfigRow("Nút Teleport", FakeLagSettings.showTeleportButton, FakeLagSettings.pinTeleportButton)
                                        ButtonConfigRow("Che mã trận", FakeLagSettings.showMatrixCover, FakeLagSettings.pinMatrixCover)
                                        
                                        // Row for Controller Pin
                                        val pinController by FakeLagSettings.pinControllerButton.collectAsState()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF121222))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Ghim Bánh Răng", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (pinController) Color(0xFFFFEA00).copy(alpha = 0.2f) else Color(0xFF222233))
                                                    .clickable { FakeLagSettings.pinControllerButton.value = !pinController }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(
                                                        imageVector = if (pinController) Icons.Default.Lock else Icons.Default.LockOpen,
                                                        contentDescription = "Pin Controller",
                                                        tint = if (pinController) Color(0xFFFFEA00) else Color(0xFF8888AA),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(if (pinController) "GHIM" else "MỞ", color = if (pinController) Color(0xFFFFEA00) else Color(0xFF8888AA), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                    1 -> {
                                        // TAB 1: KÍCH THƯỚC & ĐỘ MỜ
                                        val btnSizeVal by FakeLagSettings.buttonSizeDp.collectAsState()
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Kích thước:", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("${btnSizeVal}dp", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                            Slider(
                                                value = btnSizeVal.toFloat(),
                                                onValueChange = { FakeLagSettings.buttonSizeDp.value = it.toInt() },
                                                valueRange = 40f..120f,
                                                modifier = Modifier.height(20.dp),
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        val btnAlphaVal by FakeLagSettings.buttonAlpha.collectAsState()
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Độ mờ nút:", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("${(btnAlphaVal * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                            Slider(
                                                value = btnAlphaVal,
                                                onValueChange = { FakeLagSettings.buttonAlpha.value = it },
                                                valueRange = 0.1f..1.0f,
                                                modifier = Modifier.height(20.dp),
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        val borderThick by FakeLagSettings.borderThickness.collectAsState()
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Độ dày viền:", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("${String.format("%.1f", borderThick)}dp", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                            Slider(
                                                value = borderThick,
                                                onValueChange = { FakeLagSettings.borderThickness.value = it },
                                                valueRange = 0.5f..5.0f,
                                                modifier = Modifier.height(20.dp),
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        val btnScale by FakeLagSettings.buttonScale.collectAsState()
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Tỷ lệ thu phóng:", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                Text("${String.format("%.1fx", btnScale)}", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            }
                                            Slider(
                                                value = btnScale,
                                                onValueChange = { FakeLagSettings.buttonScale.value = it },
                                                valueRange = 0.5f..2.0f,
                                                modifier = Modifier.height(20.dp),
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                                            )
                                        }

                                        androidx.compose.material3.Button(
                                            onClick = { 
                                                FakeLagSettings.buttonScale.value = 1.0f 
                                                FakeLagSettings.buttonAlpha.value = 1.0f
                                                FakeLagSettings.buttonSizeDp.value = 68
                                                FakeLagSettings.borderThickness.value = 1.5f
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E34)),
                                            modifier = Modifier.fillMaxWidth().height(26.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("RESET TỈ LỆ NÚT", color = Color(0xFF00E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                    2 -> {
                                        // TAB 2: THANH CHE MÃ TRẬN
                                        val isMatrixCoverActive by FakeLagSettings.showMatrixCover.collectAsState()
                                        if (isMatrixCoverActive) {
                                            val mWidth by FakeLagSettings.matrixCoverWidth.collectAsState()
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Chiều rộng:", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                    Text("${mWidth.toInt()}dp", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }
                                                Slider(
                                                    value = mWidth,
                                                    onValueChange = { FakeLagSettings.matrixCoverWidth.value = it },
                                                    valueRange = 80f..400f,
                                                    modifier = Modifier.height(20.dp),
                                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                                                )
                                            }
                                            
                                            val mHeight by FakeLagSettings.matrixCoverHeight.collectAsState()
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Chiều cao:", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                    Text("${mHeight.toInt()}dp", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }
                                                Slider(
                                                    value = mHeight,
                                                    onValueChange = { FakeLagSettings.matrixCoverHeight.value = it },
                                                    valueRange = 10f..100f,
                                                    modifier = Modifier.height(20.dp),
                                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                                                )
                                            }

                                            val mAlpha by FakeLagSettings.matrixCoverAlpha.collectAsState()
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Độ mờ thanh:", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                                    Text("${(mAlpha * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }
                                                Slider(
                                                    value = mAlpha,
                                                    onValueChange = { FakeLagSettings.matrixCoverAlpha.value = it },
                                                    valueRange = 0.1f..1.0f,
                                                    modifier = Modifier.height(20.dp),
                                                    colors = SliderDefaults.colors(thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF))
                                                )
                                            }
                                        } else {
                                            Text("Thanh che đang tắt. Bật trong tab GHIM.", color = Color(0xFF8888AA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }

                            // Reset Position button at bottom of popup
                            androidx.compose.material3.Button(
                                onClick = { resetOverlayPositions() },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1B30)),
                                modifier = Modifier.fillMaxWidth().height(28.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                                    Text("KHÔI PHỤC VỊ TRÍ MẶC ĐỊNH", color = Color(0xFF00E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. VPN Overlay
        vpnView = DraggableFrameLayout(this).apply {
            setupViewTreeOwners()
            params = vpnParams
            windowManager = this@OverlayService.windowManager
            isPinned = FakeLagSettings.pinVpnButton.value
            visibility = if (FakeLagSettings.showVpnButton.value) View.VISIBLE else View.GONE
            onClick = {
                toggleVpnService()
            }
            composeView.setContent {
                val isActive by FakeLagSettings.isVpnActive.collectAsState()
                FloatingButton(
                    type = "vpn",
                    label = "VPN",
                    color = Color(0xFFBD00FF),
                    isActive = isActive,
                    onClick = {
                        toggleVpnService()
                    }
                )
            }
        }
        
        // 2. Freeze Overlay
        freezeView = DraggableFrameLayout(this).apply {
            setupViewTreeOwners()
            params = freezeParams
            windowManager = this@OverlayService.windowManager
            isPinned = FakeLagSettings.pinFreezeButton.value
            visibility = if (FakeLagSettings.showFreezeButton.value) View.VISIBLE else View.GONE
            onClick = {
                FakeLagSettings.toggleFreeze(this@OverlayService)
            }
            composeView.setContent {
                val isActive by FakeLagSettings.isFreezeActive.collectAsState()
                FloatingButton(
                    type = "freeze",
                    label = "FREEZE",
                    color = Color(0xFF00E5FF),
                    isActive = isActive,
                    onClick = {
                        FakeLagSettings.toggleFreeze(this@OverlayService)
                    }
                )
            }
        }

        // 3. Ghost Overlay
        ghostView = DraggableFrameLayout(this).apply {
            setupViewTreeOwners()
            params = ghostParams
            windowManager = this@OverlayService.windowManager
            isPinned = FakeLagSettings.pinGhostButton.value
            visibility = if (FakeLagSettings.showGhostButton.value) View.VISIBLE else View.GONE
            onClick = {
                FakeLagSettings.toggleGhost(this@OverlayService)
            }
            composeView.setContent {
                val isActive by FakeLagSettings.isGhostActive.collectAsState()
                FloatingButton(
                    type = "ghost",
                    label = "GHOST",
                    color = Color(0xFF39FF14),
                    isActive = isActive,
                    onClick = {
                        FakeLagSettings.toggleGhost(this@OverlayService)
                    }
                )
            }
        }

        // 4. Teleport Overlay
        teleportView = DraggableFrameLayout(this).apply {
            setupViewTreeOwners()
            params = teleportParams
            windowManager = this@OverlayService.windowManager
            isPinned = FakeLagSettings.pinTeleportButton.value
            visibility = if (FakeLagSettings.showTeleportButton.value) View.VISIBLE else View.GONE
            onClick = {
                FakeLagSettings.toggleTeleport(this@OverlayService)
            }
            composeView.setContent {
                val phase by FakeLagSettings.teleportPhase.collectAsState()
                val isActive = phase > 0
                val labelText = when (phase) {
                    1 -> "P1 ACC"
                    2 -> "P2 REL"
                    else -> "OFF"
                }
                FloatingButton(
                    type = "teleport",
                    label = "TELEPORT",
                    color = Color(0xFFFF5E00),
                    isActive = isActive,
                    statusText = labelText,
                    onClick = {
                        FakeLagSettings.toggleTeleport(this@OverlayService)
                    }
                )
            }
        }

        // 5. Matrix Cover Overlay (Thanh che mã trận)
        matrixCoverView = DraggableFrameLayout(this).apply {
            setupViewTreeOwners()
            params = matrixCoverParams
            windowManager = this@OverlayService.windowManager
            isPinned = FakeLagSettings.pinMatrixCover.value
            visibility = if (FakeLagSettings.showMatrixCover.value) View.VISIBLE else View.GONE
            composeView.setContent {
                val width by FakeLagSettings.matrixCoverWidth.collectAsState()
                val height by FakeLagSettings.matrixCoverHeight.collectAsState()
                val alpha by FakeLagSettings.matrixCoverAlpha.collectAsState()
                val colorHex by FakeLagSettings.matrixCoverColorHex.collectAsState()
                val isPinnedState by FakeLagSettings.pinMatrixCover.collectAsState()
                
                val parsedColor = try {
                    Color(android.graphics.Color.parseColor(colorHex))
                } catch (e: Exception) {
                    Color.Black
                }
                
                Box(
                    modifier = Modifier
                        .width(width.dp)
                        .height(height.dp)
                        .graphicsLayer(alpha = alpha)
                        .background(parsedColor, RoundedCornerShape(4.dp))
                        .border(
                            width = if (isPinnedState) 0.dp else 1.5.dp,
                            color = Color(0xFF00E5FF).copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isPinnedState) {
                        Text(
                            text = "Che mã trận [Drag]",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 6. Fullscreen ESP & FOV Overlay Canvas
        espFovView = ComposeView(this).apply {
            setupViewTreeOwners()
            setContent {
                EspFovOverlayHUD()
            }
        }
        val espFovParams = createFullscreenLayoutParams()

        // Add to Window Manager safely
        try {
            windowManager.addView(espFovView, espFovParams)
            windowManager.addView(controllerView, controllerParams)
            windowManager.addView(vpnView, vpnParams)
            windowManager.addView(freezeView, freezeParams)
            windowManager.addView(ghostView, ghostParams)
            windowManager.addView(teleportView, teleportParams)
            windowManager.addView(matrixCoverView, matrixCoverParams)
            
            // Auto-start VPN when overlay is shown
            startVpnServiceAuto()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay views", e)
            FakeLagSettings.log("❌ Lỗi hiển thị Floating Window: ${e.localizedMessage}", FakeLagSettings.LogType.ERROR)
        }

        // Observe flows for visibility and pinning reactively (fixed coroutine collect bug)
        overlayScope.launch {
            FakeLagSettings.showVpnButton.collect { show ->
                vpnView?.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
        overlayScope.launch {
            FakeLagSettings.showFreezeButton.collect { show ->
                freezeView?.visibility = if (show) View.VISIBLE else View.GONE
                // Trigger a re-layout if needed to ensure visibility change is applied
                freezeView?.requestLayout()
            }
        }
        overlayScope.launch {
            FakeLagSettings.showGhostButton.collect { show ->
                ghostView?.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
        overlayScope.launch {
            FakeLagSettings.showTeleportButton.collect { show ->
                teleportView?.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
        overlayScope.launch {
            FakeLagSettings.showMatrixCover.collect { show ->
                matrixCoverView?.visibility = if (show) View.VISIBLE else View.GONE
            }
        }

        overlayScope.launch {
            FakeLagSettings.pinVpnButton.collect { pin ->
                (vpnView as? DraggableFrameLayout)?.isPinned = pin
            }
        }
        overlayScope.launch {
            FakeLagSettings.pinFreezeButton.collect { pin ->
                (freezeView as? DraggableFrameLayout)?.let {
                    it.isPinned = pin
                    // Ensure touch interception is reset correctly
                }
            }
        }
        overlayScope.launch {
            FakeLagSettings.pinGhostButton.collect { pin ->
                (ghostView as? DraggableFrameLayout)?.isPinned = pin
            }
        }
        overlayScope.launch {
            FakeLagSettings.pinTeleportButton.collect { pin ->
                (teleportView as? DraggableFrameLayout)?.isPinned = pin
            }
        }
        overlayScope.launch {
            FakeLagSettings.pinControllerButton.collect { pin ->
                (controllerView as? DraggableFrameLayout)?.isPinned = pin
            }
        }
        overlayScope.launch {
            FakeLagSettings.pinMatrixCover.collect { pin ->
                (matrixCoverView as? DraggableFrameLayout)?.isPinned = pin
            }
        }
    }

    private fun resetOverlayPositions() {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        try {
            controllerParams.x = 50
            controllerParams.y = screenHeight / 4
            controllerView?.let { windowManager.updateViewLayout(it, controllerParams) }
            
            vpnParams.x = 50
            vpnParams.y = screenHeight / 4 + 150
            vpnView?.let { windowManager.updateViewLayout(it, vpnParams) }
                        
            freezeParams.x = 50
            freezeParams.y = screenHeight / 4 + 300
            freezeView?.let { windowManager.updateViewLayout(it, freezeParams) }
            
            ghostParams.x = 50
            ghostParams.y = screenHeight / 4 + 450
            ghostView?.let { windowManager.updateViewLayout(it, ghostParams) }
            
            teleportParams.x = 50
            teleportParams.y = screenHeight / 4 + 600
            teleportView?.let { windowManager.updateViewLayout(it, teleportParams) }

            matrixCoverParams.x = screenWidth / 2 - 120
            matrixCoverParams.y = screenHeight - 180
            matrixCoverView?.let { windowManager.updateViewLayout(it, matrixCoverParams) }
            
            FakeLagSettings.log("↺ Đã khôi phục vị trí các nút phụ về mặc định.", FakeLagSettings.LogType.SUCCESS)
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting overlay positions", e)
        }
    }

    private fun toggleVpnService() {
        val isActive = FakeLagSettings.isVpnActive.value
        if (isActive) {
            val stopIntent = Intent(this, FakeLagVpnService::class.java).apply {
                action = FakeLagVpnService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent == null) {
                val startIntent = Intent(this, FakeLagVpnService::class.java).apply {
                    action = FakeLagVpnService.ACTION_START
                }
                startService(startIntent)
            } else {
                FakeLagSettings.log("⚠️ VPN chưa được cấp quyền. Vui lòng mở app để cấp quyền!", FakeLagSettings.LogType.WARNING)
            }
        }
    }

    private fun startVpnServiceAuto() {
        if (!FakeLagSettings.isVpnActive.value) {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent == null) {
                val startIntent = Intent(this, FakeLagVpnService::class.java).apply {
                    action = FakeLagVpnService.ACTION_START
                }
                startService(startIntent)
            } else {
                FakeLagSettings.log("⚠️ VPN chưa được cấp quyền. Vui lòng mở app để cấp quyền!", FakeLagSettings.LogType.WARNING)
            }
        }
    }

    private fun stopVpnServiceAuto() {
        if (FakeLagSettings.isVpnActive.value) {
            val stopIntent = Intent(this, FakeLagVpnService::class.java).apply {
                action = FakeLagVpnService.ACTION_STOP
            }
            startService(stopIntent)
        }
    }

    private fun hideOverlays() {
        RealFpsTracker.stop()
        FakeLagSettings.isOverlayActive.value = false
        stopVpnServiceAuto() // Auto stop VPN when overlay is hidden
        try {
            controllerView?.let { windowManager.removeView(it) }
            vpnView?.let { windowManager.removeView(it) }
            freezeView?.let { windowManager.removeView(it) }
            ghostView?.let { windowManager.removeView(it) }
            teleportView?.let { windowManager.removeView(it) }
            matrixCoverView?.let { windowManager.removeView(it) }
            espFovView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay views", e)
        }
        controllerView = null
        vpnView = null
        freezeView = null
        ghostView = null
        teleportView = null
        matrixCoverView = null
        espFovView = null
    }

    private fun createFullscreenLayoutParams(): WindowManager.LayoutParams {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun createLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun View.setupViewTreeOwners() {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
    }



    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "New Ping Pro Overlay Core",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running overlay switches background thread."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("New Ping Pro Overlay Active")
            .setContentText("Cửa sổ phím phụ đang chạy trên màn hình (Bản quyền Nguyễn Hoàng Phú).")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }
}

@Composable
fun FreezeIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val length = size.width / 2
        for (i in 0 until 6) {
            val angle = i * Math.PI / 3
            val endX = center.x + length * Math.cos(angle).toFloat()
            val endY = center.y + length * Math.sin(angle).toFloat()
            val end = Offset(endX, endY)
            drawLine(color, center, end, strokeWidth = 3f)

            val branchLength = length * 0.35f
            val branchAngle1 = angle + Math.PI / 4
            val branchAngle2 = angle - Math.PI / 4
            val branchStartX = center.x + length * 0.55f * Math.cos(angle).toFloat()
            val branchStartY = center.y + length * 0.55f * Math.sin(angle).toFloat()
            val branchStart = Offset(branchStartX, branchStartY)
            drawLine(
                color,
                branchStart,
                Offset(
                    branchStartX + branchLength * Math.cos(branchAngle1).toFloat(),
                    branchStartY + branchLength * Math.sin(branchAngle1).toFloat()
                ),
                strokeWidth = 2.5f
            )
            drawLine(
                color,
                branchStart,
                Offset(
                    branchStartX + branchLength * Math.cos(branchAngle2).toFloat(),
                    branchStartY + branchLength * Math.sin(branchAngle2).toFloat()
                ),
                strokeWidth = 2.5f
            )
        }
    }
}

@Composable
fun GhostIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.9f)
            lineTo(w * 0.15f, h * 0.45f)
            arcTo(
                rect = Rect(w * 0.15f, h * 0.1f, w * 0.85f, h * 0.8f),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(w * 0.85f, h * 0.9f)
            quadraticTo(w * 0.73f, h * 0.78f, w * 0.61f, h * 0.9f)
            quadraticTo(w * 0.5f, h * 0.78f, w * 0.38f, h * 0.9f)
            quadraticTo(w * 0.26f, h * 0.78f, w * 0.15f, h * 0.9f)
            close()
        }
        drawPath(path, color = color)
        drawCircle(Color.Black, radius = 2f, center = Offset(w * 0.38f, h * 0.42f))
        drawCircle(Color.Black, radius = 2f, center = Offset(w * 0.62f, h * 0.42f))
    }
}

@Composable
fun TeleportIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.6f, h * 0.05f)
            lineTo(w * 0.25f, h * 0.55f)
            lineTo(w * 0.55f, h * 0.55f)
            lineTo(w * 0.4f, h * 0.95f)
            lineTo(w * 0.75f, h * 0.45f)
            lineTo(w * 0.45f, h * 0.45f)
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
fun VpnIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.85f, h * 0.22f)
            lineTo(w * 0.85f, h * 0.55f)
            quadraticTo(w * 0.85f, h * 0.82f, w * 0.5f, h * 0.92f)
            quadraticTo(w * 0.15f, h * 0.82f, w * 0.15f, h * 0.55f)
            lineTo(w * 0.15f, h * 0.22f)
            close()
        }
        drawPath(path, color = color)
        // Visual keyhole
        drawCircle(Color.Black, radius = 2.5f, center = Offset(w * 0.5f, h * 0.45f))
    }
}

@Composable
fun FloatingButton(
    type: String, // "vpn", "freeze", "ghost", "teleport"
    label: String,
    color: Color,
    isActive: Boolean,
    statusText: String? = null,
    onClick: () -> Unit
) {
    val btnSizeDp by FakeLagSettings.buttonSizeDp.collectAsState()
    val btnAlpha by FakeLagSettings.buttonAlpha.collectAsState()
    val globalScale by FakeLagSettings.buttonScale.collectAsState()
    val borderThickness by FakeLagSettings.borderThickness.collectAsState()
    
    // Proportional scaling math to prevent overflow/clipping
    val indicatorSize = (btnSizeDp * 0.09f).coerceIn(4f, 8f)
    val iconSize = (btnSizeDp * 0.35f).coerceIn(16f, 32f)
    val textSize = (btnSizeDp * 0.11f).coerceIn(6f, 12f)
    val indicatorSpacer = (btnSizeDp * 0.045f).coerceIn(2f, 5f)
    val textSpacer = (btnSizeDp * 0.03f).coerceIn(1f, 4f)
    val paddingTopBottom = (btnSizeDp * 0.06f).coerceIn(2f, 6f)

    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val animatedBorderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderPulse"
    )

    val activeGlowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
 
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )
 
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(btnSizeDp.dp)
    ) {
        // Outer pulsing ring when active
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = activeGlowScale * globalScale,
                        scaleY = activeGlowScale * globalScale,
                        alpha = animatedBorderAlpha * 0.4f
                    )
                    .border(1.5.dp, color, CircleShape)
            )
        }

        Surface(
            color = if (isActive) Color(0xF20F1226) else Color(0xEB0A0A14), // Premium dark translucent backdrop
            shape = CircleShape,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale * globalScale, 
                    scaleY = scale * globalScale,
                    alpha = btnAlpha
                )
                .border(
                    width = if (isActive) (borderThickness + 0.8f).dp else borderThickness.dp,
                    color = if (isActive) color.copy(alpha = animatedBorderAlpha) else color.copy(alpha = 0.25f),
                    shape = CircleShape
                )
                .clip(CircleShape)
                .clickable { onClick() }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = paddingTopBottom.dp, bottom = paddingTopBottom.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Neon circle indicator
                Box(
                    modifier = Modifier
                        .size(indicatorSize.dp)
                        .background(
                            color = if (isActive) color else Color(0xFF444455),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.height(indicatorSpacer.dp))
                
                // Icon section
                Box(
                    modifier = Modifier.size(iconSize.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val iconColor = if (isActive) color else Color(0xFF8888AA)
                    when (type) {
                        "vpn" -> VpnIcon(color = iconColor)
                        "freeze" -> FreezeIcon(color = iconColor)
                        "ghost" -> GhostIcon(color = iconColor)
                        "teleport" -> TeleportIcon(color = iconColor)
                        else -> Text(
                            text = type.take(1).uppercase(),
                            color = iconColor,
                            fontSize = (textSize * 1.5f).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(textSpacer.dp))
                Text(
                    text = statusText ?: (if (isActive) "ON" else "OFF"),
                    color = if (isActive) Color.White else Color(0xFF666677),
                    fontSize = textSize.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

class DraggableFrameLayout(context: Context) : FrameLayout(context) {
    var params: WindowManager.LayoutParams? = null
    var windowManager: WindowManager? = null
    var onClick: (() -> Unit)? = null
    var isPinned: Boolean = false

    val composeView = ComposeView(context)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val touchSlopSquare = touchSlop * touchSlop

    init {
        addView(composeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        
        // Listen to layout updates of the composeView to dynamically update WindowManager layout bounds
        composeView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val wm = windowManager
            val lp = params
            if (wm != null && lp != null) {
                try {
                    // Check if this view is attached before updating layout to prevent crashes
                    if (isAttachedToWindow) {
                        wm.updateViewLayout(this, lp)
                    }
                } catch (e: Exception) {
                    // Fail-safe to avoid crash in corner cases (e.g. view detaching)
                }
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isPinned) {
            // When pinned, don't intercept anything, let child handle it (for click)
            return false
        }
        val lp = params ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = lp.x
                initialY = lp.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if ((dx * dx + dy * dy) > touchSlopSquare) {
                    isDragging = true
                    return true 
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPinned) {
            // When pinned, we still need to consume the events if we want the onClick to fire 
            // but we don't want to drag.
            if (event.action == MotionEvent.ACTION_UP) {
                onClick?.invoke()
            }
            return true
        }
        val wm = windowManager ?: return false
        val lp = params ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // If children didn't consume down, we must consume it to receive move/up
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (!isDragging && (dx * dx + dy * dy) > touchSlopSquare) {
                    isDragging = true
                }
                if (isDragging) {
                    lp.x = initialX + dx
                    lp.y = initialY + dy
                    try {
                        wm.updateViewLayout(this, lp)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    // Click gesture
                     onClick?.invoke()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

@Composable
fun ButtonConfigRow(
    label: String,
    showFlow: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    pinFlow: kotlinx.coroutines.flow.MutableStateFlow<Boolean>
) {
    val show by showFlow.collectAsState()
    val pin by pinFlow.collectAsState()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Visibility
            Icon(
                imageVector = if (show) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle Visibility",
                tint = if (show) Color(0xFF39FF14) else Color(0xFF555566),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { showFlow.value = !show }
            )
            // Pin
            Icon(
                imageVector = if (pin) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Toggle Pin",
                tint = if (pin) Color(0xFFFFEA00) else Color(0xFF8888AA),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { pinFlow.value = !pin }
            )
        }
    }
}


@Composable
fun EspFovOverlayHUD() {
    // Aimbot and ESP overlay features removed per user request
}

