import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'(\s*)Spacer\(modifier = Modifier\.height\(10\.dp\)\)\s*// Fail-safe logic info message\s*Box\(\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.background\(Color\(0xFF09090E\), RoundedCornerShape\(6\.dp\)\)\s*\.border\(1\.dp, Color\(0xFF202030\), RoundedCornerShape\(6\.dp\)\)\s*\.padding\(8\.dp\)\s*\) \{\s*Text\(\s*text = "🛡️ LOGIC CHỐNG LỖI: Khi thay đổi kích thước, hệ thống tự động tính toán co dãn các thành phần icon/chữ bên trong để tránh tràn nội dung và tự động cập nhật vùng hiển thị lên WindowManager chống lag/giật\.",\s*color = Color\(0xFF8888AA\),\s*fontSize = 8\.sp,\s*lineHeight = 11\.sp,\s*fontFamily = FontFamily\.Monospace\s*\)\s*\}\s*\}\s*\}\s*\}'
replacement = r''
content = re.sub(pattern, replacement, content)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
