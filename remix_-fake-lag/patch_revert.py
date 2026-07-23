import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Revert VPN tied to Overlay
pattern_overlay_stop = r'(val stopIntent = Intent\(context, OverlayService::class\.java\)\.apply \{\s*action = OverlayService\.ACTION_HIDE_OVERLAYS\s*\}\s*context\.stopService\(stopIntent\))\s*val stopVpnIntent = Intent\(context, FakeLagVpnService::class\.java\)\.apply \{\s*action = FakeLagVpnService\.ACTION_STOP\s*\}\s*context\.startService\(stopVpnIntent\)'
content = re.sub(pattern_overlay_stop, r'\1', content)

pattern_overlay_start = r'(val startIntent = Intent\(context, OverlayService::class\.java\)\.apply \{\s*action = OverlayService\.ACTION_SHOW_OVERLAYS\s*\}\s*context\.startService\(startIntent\))\s*val startVpnIntent = Intent\(context, FakeLagVpnService::class\.java\)\.apply \{\s*action = FakeLagVpnService\.ACTION_START\s*\}\s*context\.startService\(startVpnIntent\)'
content = re.sub(pattern_overlay_start, r'\1', content)

# Add back VPN toggle
pattern_toggle = r'(Icon\(\s*imageVector = Icons\.Default\.ExitToApp,\s*contentDescription = "Đăng xuất",\s*tint = Color\(0xFFFF007F\),\s*modifier = Modifier\.size\(18\.dp\)\s*\)\s*\})'
replacement_toggle = r'''\1

                    // VPN Core Toggle Switch
                    IconButton(
                        onClick = {
                            if (vpnActive) {
                                val stopIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                    action = FakeLagVpnService.ACTION_STOP
                                }
                                context.startService(stopIntent)
                            } else {
                                val vpnIntent = VpnService.prepare(context)
                                if (vpnIntent != null) {
                                    vpnRequestLauncher.launch(vpnIntent)
                                } else {
                                    val startIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                        action = FakeLagVpnService.ACTION_START
                                    }
                                    context.startService(startIntent)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .border(
                                width = 1.5.dp,
                                color = if (vpnActive) Color(0xFF39FF14) else Color(0xFFFF007F),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (vpnActive) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = "Toggle VPN Interceptor",
                            tint = if (vpnActive) Color(0xFF39FF14) else Color(0xFFFF007F),
                            modifier = Modifier.size(18.dp)
                        )
                    }'''

content = re.sub(pattern_toggle, replacement_toggle, content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
