import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'(\s*)// OVERLAY BUTTON ALPHA CONFIGURATION\s*item \{\s*Text\(\s*text = "👻 ĐỘ MỜ PHÍM PHỤ \(OVERLAY BUTTON ALPHA\)",\s*color = Color\(0xFF8888AA\),\s*fontSize = 10\.sp,\s*fontWeight = FontWeight\.Bold,\s*fontFamily = FontFamily\.Monospace,\s*modifier = Modifier\.padding\(top = 10\.dp\)\s*\)\s*\}'
replacement = r'\1}\n            }\n        }\n\n        // OVERLAY BUTTON ALPHA CONFIGURATION\n        item {\n            Text(\n                text = "👻 ĐỘ MỜ PHÍM PHỤ (OVERLAY BUTTON ALPHA)",\n                color = Color(0xFF8888AA),\n                fontSize = 10.sp,\n                fontWeight = FontWeight.Bold,\n                fontFamily = FontFamily.Monospace,\n                modifier = Modifier.padding(top = 10.dp)\n            )\n        }'

content = re.sub(pattern, replacement, content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
