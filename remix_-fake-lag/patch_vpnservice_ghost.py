import re

with open('./app/src/main/java/com/example/FakeLagVpnService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'(// 3\. GHOST MODE \(Intercepts position UDP packets, drops them\)\s*if \(FakeLagSettings\.isGhostActive\.value\) \{\s*val blockThreshold = FakeLagSettings\.ghostBlockThreshold\.value\s*if \(length <= blockThreshold\) \{)'
replacement = r'''// 3. GHOST MODE (Intercepts position UDP packets, drops them)
        if (FakeLagSettings.isGhostActive.value) {
            val blockThreshold = FakeLagSettings.ghostBlockThreshold.value
            // Only block 501-700
            if (length in 501..700) {'''
content = re.sub(pattern, replacement, content)

with open('./app/src/main/java/com/example/FakeLagVpnService.kt', 'w', encoding='utf-8') as f:
    f.write(content)
