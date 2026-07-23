import re

with open('./app/src/main/java/com/example/OverlayService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern1 = r'(val btnSizeDp by FakeLagSettings\.buttonSizeDp\.collectAsState\(\))'
replacement1 = r'\1\n                val btnAlpha by FakeLagSettings.buttonAlpha.collectAsState()'
content = re.sub(pattern1, replacement1, content, count=1)

pattern1_apply = r'(Surface\(\s*color = Color\(0xEB0C0C1E\),)'
replacement1_apply = r'Surface(\n                        color = Color(0xEB0C0C1E).copy(alpha = btnAlpha * 0.92f),'
content = re.sub(pattern1_apply, replacement1_apply, content, count=1)


pattern2 = r'(val btnSizeDp by FakeLagSettings\.buttonSizeDp\.collectAsState\(\))'
replacement2 = r'\1\n    val btnAlpha by FakeLagSettings.buttonAlpha.collectAsState()'
content = re.sub(pattern2, replacement2, content)

pattern2_apply = r'(Surface\(\s*color = Color\(0xEB0A0A14\),)'
replacement2_apply = r'Surface(\n        color = Color(0xEB0A0A14).copy(alpha = btnAlpha * 0.92f),'
content = re.sub(pattern2_apply, replacement2_apply, content)

with open('./app/src/main/java/com/example/OverlayService.kt', 'w', encoding='utf-8') as f:
    f.write(content)
