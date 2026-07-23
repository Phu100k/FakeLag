import re

with open('./app/src/main/java/com/example/OverlayService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add back ButtonConfigRow
content = re.sub(
    r'(// Rows for each button\s*)',
    r'\1ButtonConfigRow("Nút VPN", FakeLagSettings.showVpnButton, FakeLagSettings.pinVpnButton)\n                            ',
    content
)

# 2. Add back vpnView initialization
vpnView_block = """
        // 1. VPN Overlay
        vpnView = DraggableFrameLayout(this).apply {
            setupViewTreeOwners()
            params = vpnParams
            windowManager = this@OverlayService.windowManager
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
        
        // 2. Freeze Overlay"""

content = re.sub(r'(\s*// 2\. Freeze Overlay)', vpnView_block, content)

# 3. Add windowManager.addView
content = re.sub(r'(windowManager\.addView\(controllerView, controllerParams\))', r'\1\n            windowManager.addView(vpnView, vpnParams)', content)

# 4. Add showVpnButton flow
content = re.sub(
    r'(FakeLagSettings\.showFreezeButton\.collect \{ show ->)',
    r'FakeLagSettings.showVpnButton.collect { show ->\n                vpnView?.visibility = if (show) View.VISIBLE else View.GONE\n            }\n            \1',
    content
)

# 5. Add pinVpnButton flow
content = re.sub(
    r'(FakeLagSettings\.pinFreezeButton\.collect \{ pin ->)',
    r'FakeLagSettings.pinVpnButton.collect { pin ->\n                (vpnView as? DraggableFrameLayout)?.isPinned = pin\n            }\n            \1',
    content
)

# 6. Add windowManager.updateViewLayout in resetOverlayPositions
content = re.sub(
    r'(vpnParams\.y = screenHeight / 4 \+ 150)',
    r'\1\n            vpnView?.let { windowManager.updateViewLayout(it, vpnParams) }',
    content
)

# 7. Add windowManager.removeView
content = re.sub(
    r'(controllerView\?\.let \{ windowManager\.removeView\(it\) \})',
    r'\1\n            vpnView?.let { windowManager.removeView(it) }',
    content
)

# 8. Add vpnView = null
content = re.sub(
    r'(controllerView = null)',
    r'\1\n        vpnView = null',
    content
)

with open('./app/src/main/java/com/example/OverlayService.kt', 'w', encoding='utf-8') as f:
    f.write(content)
