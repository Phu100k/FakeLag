import re

with open('./app/src/main/java/com/example/FakeLagSettings.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add buttonAlpha setting
if 'val buttonAlpha = MutableStateFlow' not in content:
    content = re.sub(
        r'val buttonSizeDp = MutableStateFlow\(68\) // Default is 68dp',
        r'val buttonSizeDp = MutableStateFlow(68) // Default is 68dp\n    val buttonAlpha = MutableStateFlow(1.0f) // Default is 1.0f',
        content
    )

with open('./app/src/main/java/com/example/FakeLagSettings.kt', 'w', encoding='utf-8') as f:
    f.write(content)

