import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add buttonAlpha UI block
pattern = r'(// OVERLAY BUTTON SIZE CONFIGURATION\s*item \{\s*Text\(\s*text = "📐 KÍCH THƯỚC PHÍM PHỤ \(OVERLAY BUTTON SIZE\)",[\s\S]*?\}\s*\}\s*\})'
replacement = r'''\1

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
        }'''
content = re.sub(pattern, replacement, content)

content = re.sub(
    r'FakeLagSettings\.buttonSizeDp\.value = 68',
    r'FakeLagSettings.buttonSizeDp.value = 68\n                    FakeLagSettings.buttonAlpha.value = 1.0f',
    content
)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
