import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the VPN Core Toggle Switch
pattern_vpn_toggle = r'(\s*// VPN Core Toggle Switch\s*IconButton\([\s\S]*?\}\s*\)\s*\{\s*Icon\([\s\S]*?\}\s*\))'
content = re.sub(pattern_vpn_toggle, '', content)

# Modify the BẬT OVERLAY click handler to also start/stop VPN
pattern_overlay_stop = r'(val stopIntent = Intent\(context, OverlayService::class\.java\)\.apply \{\s*action = OverlayService\.ACTION_HIDE_OVERLAYS\s*\}\s*context\.stopService\(stopIntent\))'
replacement_overlay_stop = r'''\1
                                    val stopVpnIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                        action = FakeLagVpnService.ACTION_STOP
                                    }
                                    context.startService(stopVpnIntent)'''
content = re.sub(pattern_overlay_stop, replacement_overlay_stop, content)

pattern_overlay_start = r'(val startIntent = Intent\(context, OverlayService::class\.java\)\.apply \{\s*action = OverlayService\.ACTION_SHOW_OVERLAYS\s*\}\s*context\.startService\(startIntent\))'
replacement_overlay_start = r'''\1
                                    val startVpnIntent = Intent(context, FakeLagVpnService::class.java).apply {
                                        action = FakeLagVpnService.ACTION_START
                                    }
                                    context.startService(startVpnIntent)'''
content = re.sub(pattern_overlay_start, replacement_overlay_start, content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)

