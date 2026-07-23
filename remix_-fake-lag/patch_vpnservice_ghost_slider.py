import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'(val threshold by FakeLagSettings\.ghostBlockThreshold\.collectAsState\(\)\s*Card[\s\S]*?Slider\(\s*value = threshold\.toFloat\(\),\s*onValueChange = \{ FakeLagSettings\.ghostBlockThreshold\.value = it\.toInt\(\) \},\s*valueRange = 20f\.\.500f,)'
replacement = r'val threshold by FakeLagSettings.ghostBlockThreshold.collectAsState()\n            Card(\n                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .border(1.dp, Color(0xFF1E1E34), RoundedCornerShape(12.dp))\n            ) {\n                Column(modifier = Modifier.padding(16.dp)) {\n                    Row(\n                        modifier = Modifier.fillMaxWidth(),\n                        horizontalArrangement = Arrangement.SpaceBetween,\n                        verticalAlignment = Alignment.CenterVertically\n                    ) {\n                        Text("Lọc bỏ qua <", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)\n                        Text("500 bytes", color = Color(0xFF39FF14), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)\n                    }\n                    Text("Luôn cho qua các gói tin <= 500 bytes. Chặn các gói từ 501-700 bytes.", color = Color(0xFF8888AA), fontSize = 8.sp, fontFamily = FontFamily.Monospace)\n                    Slider(\n                        value = 500f,\n                        onValueChange = { },\n                        valueRange = 20f..700f,'
content = re.sub(pattern, replacement, content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
