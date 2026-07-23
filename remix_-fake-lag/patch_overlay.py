import re

with open('./app/src/main/java/com/example/OverlayService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove vpnView references
content = re.sub(r'windowManager\.addView\(vpnView, vpnParams\)\n?', '', content)
content = re.sub(r'vpnView\?\.visibility = [^\n]*\n?', '', content)
content = re.sub(r'\(vpnView as\? DraggableFrameLayout\)\?\.isPinned = [^\n]*\n?', '', content)
content = re.sub(r'vpnView\?\.let \{ windowManager\.updateViewLayout\(it, vpnParams\) \}\n?', '', content)
content = re.sub(r'vpnView\?\.let \{ windowManager\.removeView\(it\) \}\n?', '', content)
content = re.sub(r'vpnView = null\n?', '', content)

# Remove flow collectors for showVpnButton and pinVpnButton completely
content = re.sub(r'FakeLagSettings\.showVpnButton\.collect \{ show ->\s*\}', '', content)
content = re.sub(r'FakeLagSettings\.pinVpnButton\.collect \{ pin ->\s*\}', '', content)

with open('./app/src/main/java/com/example/OverlayService.kt', 'w', encoding='utf-8') as f:
    f.write(content)
