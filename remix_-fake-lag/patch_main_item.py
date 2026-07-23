import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'(\s*)\}\s*\}\s*\}        // OVERLAY BUTTON ALPHA CONFIGURATION\s*item \{'
replacement = r'\1}\n                    }\n                    \n                    // Fail-safe logic info message\n                    Box(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .background(Color(0xFF09090E), RoundedCornerShape(6.dp))\n                            .border(1.dp, Color(0xFF202030), RoundedCornerShape(6.dp))\n                            .padding(8.dp)\n                    ) {\n                        Text(\n                            text = "🛡️ LOGIC CHỐNG LỖI: Khi thay đổi kích thước, hệ thống tự động tính toán co dãn các thành phần icon/chữ bên trong để tránh tràn nội dung và tự động cập nhật vùng hiển thị lên WindowManager chống lag/giật.",\n                            color = Color(0xFF8888AA),\n                            fontSize = 8.sp,\n                            lineHeight = 11.sp,\n                            fontFamily = FontFamily.Monospace\n                        )\n                    }\n                }\n            }\n        }\n\n        // OVERLAY BUTTON ALPHA CONFIGURATION\n        item {'

content = re.sub(pattern, replacement, content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
