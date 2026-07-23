package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

sealed class LoginResult {
    data class Success(val username: String, val isAdmin: Boolean) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

suspend fun apiLogin(user: String, pass: String): LoginResult {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val jsonString = """{"action":"login","username":"${user.replace("\"", "\\\"")}","password":"${pass.replace("\"", "\\\"")}"}"""
            val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            var request = Request.Builder()
                .url("https://gromapp.x10.mx/api/user.php?action=login")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Referer", "https://gromapp.x10.mx/")
                .header("Origin", "https://gromapp.x10.mx")
                .post(requestBody)
                .build()

            var response = client.newCall(request).execute()
            var responseBody = response.body?.string() ?: "{}"
            
            var success = false
            var message = ""
            try {
                val json = JSONObject(responseBody)
                success = json.optBoolean("success", false)
                message = json.optString("message", "")
            } catch (e: Exception) {
                // If it fails to parse JSON, we will fallback below
            }
            
            // Fallback just like python
            if (!success && (message.lowercase().contains("action") || message.lowercase().contains("hợp lệ"))) {
                val formBody = FormBody.Builder()
                    .add("action", "login")
                    .add("username", user)
                    .add("password", pass)
                    .build()
                    
                request = Request.Builder()
                    .url("https://gromapp.x10.mx/api/user.php")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "application/json")
                    .header("Referer", "https://gromapp.x10.mx/")
                    .post(formBody)
                    .build()
                    
                response = client.newCall(request).execute()
                responseBody = response.body?.string() ?: "{}"
                try {
                    val json = JSONObject(responseBody)
                    success = json.optBoolean("success", false)
                    message = json.optString("message", "Đăng nhập thất bại")
                } catch (e: Exception) {
                    message = "Lỗi phản hồi từ server"
                }
            }

            if (success) {
                val isAdmin = user.equals("anh14082012", ignoreCase = true)
                LoginResult.Success(user, isAdmin)
            } else {
                LoginResult.Error(message.ifEmpty { "Đăng nhập thất bại!" })
            }
        } catch (e: Exception) {
            LoginResult.Error("Lỗi kết nối: ${e.localizedMessage ?: "Kiểm tra mạng!"}")
        }
    }
}

fun encryptBase64(str: String): String {
    return Base64.encodeToString(str.toByteArray(), Base64.DEFAULT).trim()
}

fun decryptBase64(str: String): String {
    return try {
        String(Base64.decode(str, Base64.DEFAULT))
    } catch (e: Exception) {
        ""
    }
}

private fun saveUserToJson(user: String, pass: String) {
    try {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val file = File(downloadDir, "user.json")
        val json = JSONObject().apply {
            put("username", user)
            put("password", pass)
            put("timestamp", System.currentTimeMillis())
        }
        file.writeText(json.toString(4))
    } catch (e: Exception) {
        // Log locally if needed
    }
}

private fun loadUserFromJson(): Pair<String, String>? {
    try {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, "user.json")
        if (file.exists()) {
            val content = file.readText()
            val json = JSONObject(content)
            val user = json.optString("username")
            val pass = json.optString("password")
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                return Pair(user, pass)
            }
        }
    } catch (e: Exception) {
        // Ignore
    }
    return null
}

@Composable
fun FakeLagLoginScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usernameVal by remember { mutableStateOf("") }
    var passwordVal by remember { mutableStateOf("") }
    var rememberMeVal by remember { mutableStateOf(true) }
    var isAuthenticatingVal by remember { mutableStateOf(false) }
    var errorMessageVal by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Read stored global login error (e.g. timeout logout message)
    val globalError by FakeLagSettings.loginErrorMessage.collectAsState()
    LaunchedEffect(globalError) {
        if (globalError != null) {
            errorMessageVal = globalError
            FakeLagSettings.loginErrorMessage.value = null
        }
    }

    // Auto-login flow on initialization
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("FakeLagPrefs", Context.MODE_PRIVATE)
        
        // Try loading from user.json first (as requested)
        val jsonUser = loadUserFromJson()
        if (jsonUser != null) {
            usernameVal = jsonUser.first
            passwordVal = jsonUser.second
            FakeLagSettings.log("📁 Đã tự động điền từ Downloads/user.json", FakeLagSettings.LogType.INFO)
        } else {
            // Fallback to internal prefs
            val savedUser = prefs.getString("saved_username", "") ?: ""
            val savedPassCipher = prefs.getString("saved_password", "") ?: ""
            if (savedUser.isNotEmpty()) {
                usernameVal = savedUser
            }
            if (savedPassCipher.isNotEmpty()) {
                passwordVal = decryptBase64(savedPassCipher)
            }
        }

        val isRemember = prefs.getBoolean("remember_me", true)
        val wasSessionValid = prefs.getBoolean("session_valid", false)
        rememberMeVal = isRemember

        // Auto-login after 400ms if session is valid and fields are not empty
        if ((wasSessionValid || jsonUser != null) && usernameVal.isNotEmpty() && passwordVal.isNotEmpty() && isRemember) {
            delay(400L)
            isAuthenticatingVal = true
            errorMessageVal = "Đang kết nối server..."

            val result = apiLogin(usernameVal, passwordVal)

            isAuthenticatingVal = false
            when (result) {
                is LoginResult.Success -> {
                    errorMessageVal = null
                    FakeLagSettings.username.value = result.username
                    FakeLagSettings.isAdmin.value = result.isAdmin
                    FakeLagSettings.remainingTimeSeconds.value = if (result.isAdmin) 9999999 else 1500
                    FakeLagSettings.isLoggedIn.value = true
                    prefs.edit().putBoolean("session_valid", true).apply()
                    FakeLagSettings.log("⚡ [AUTO-LOGIN] Đăng nhập thành công làm ${result.username}.", FakeLagSettings.LogType.SUCCESS)
                }
                is LoginResult.Error -> {
                    errorMessageVal = result.message
                    passwordVal = ""
                    prefs.edit().putBoolean("session_valid", false).apply()
                    FakeLagSettings.log("❌ [AUTO-LOGIN] Thất bại: ${result.message}", FakeLagSettings.LogType.ERROR)
                }
            }
        }
    }

    val executeLogin = {
        if (usernameVal.isBlank() || passwordVal.isBlank()) {
            errorMessageVal = "Vui lòng nhập đầy đủ thông tin!"
        } else {
            scope.launch {
                isAuthenticatingVal = true
                errorMessageVal = "Đang kết nối server..."

                val result = apiLogin(usernameVal, passwordVal)

                isAuthenticatingVal = false
                val prefs = context.getSharedPreferences("FakeLagPrefs", Context.MODE_PRIVATE)
                when (result) {
                    is LoginResult.Success -> {
                        errorMessageVal = null
                        FakeLagSettings.username.value = result.username
                        FakeLagSettings.isAdmin.value = result.isAdmin
                        FakeLagSettings.remainingTimeSeconds.value = if (result.isAdmin) 9999999 else 1500
                        FakeLagSettings.isLoggedIn.value = true

                        // Save preferences
                        val editor = prefs.edit()
                        if (rememberMeVal) {
                            editor.putString("saved_username", usernameVal)
                            editor.putString("saved_password", encryptBase64(passwordVal))
                            editor.putBoolean("remember_me", true)
                            editor.putBoolean("session_valid", true)
                        } else {
                            editor.remove("saved_username")
                            editor.remove("saved_password")
                            editor.putBoolean("remember_me", false)
                            editor.putBoolean("session_valid", false)
                        }
                        editor.apply()
                        
                        // Also save to user.json in Downloads for update persistence
                        saveUserToJson(usernameVal, passwordVal)
                        
                        FakeLagSettings.log("⚡ Đăng nhập thành công: ${result.username}.", FakeLagSettings.LogType.SUCCESS)
                    }
                    is LoginResult.Error -> {
                        errorMessageVal = result.message
                        passwordVal = "" // Clear password field on error
                        prefs.edit().putBoolean("session_valid", false).apply()
                        FakeLagSettings.log("❌ Đăng nhập thất bại: ${result.message}", FakeLagSettings.LogType.ERROR)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.ui.theme.MysticBgGradient),
        contentAlignment = Alignment.Center
    ) {
        // Decorative Mystic Animated Glows & Particles
        val infiniteTransition = rememberInfiniteTransition(label = "portalPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        val particleRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particleRotation"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Cyan Violet Portal Glow Top Right
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF9D00FF).copy(alpha = 0.18f * pulseScale), Color(0xFF00F5FF).copy(alpha = 0.08f), Color.Transparent),
                    center = center.copy(x = size.width * 0.85f, y = size.height * 0.15f),
                    radius = size.width * 0.75f * pulseScale
                )
            )
            // Cyber Pink Glow Bottom Left
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF007F).copy(alpha = 0.15f), Color(0xFF6200EE).copy(alpha = 0.06f), Color.Transparent),
                    center = center.copy(x = size.width * 0.15f, y = size.height * 0.85f),
                    radius = size.width * 0.7f
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Image Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.login_hero_dragon_1783699185749),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay to blend bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0A0A12)),
                                startY = 250f
                            )
                        )
                )
                
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACCESS PORTAL",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color(0xFF00E5FF),
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "NEW PING PRO",
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Login Form Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF161622).copy(alpha = 0.9f)
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "XÁC THỰC NGƯỜI DÙNG",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )

                    // Username Input
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "TÀI KHOẢN HỆ THỐNG",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = usernameVal,
                            onValueChange = { usernameVal = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Nhập username...", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFF00E5FF)) },
                            singleLine = true,
                            enabled = !isAuthenticatingVal,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF2A2A45),
                                focusedContainerColor = Color(0xFF0A0A12),
                                unfocusedContainerColor = Color(0xFF0A0A12)
                            )
                        )
                    }

                    // Password Input
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MẬT MÃ TRUY CẬP",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = passwordVal,
                            onValueChange = { passwordVal = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("••••••••", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF00E5FF)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isAuthenticatingVal,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF2A2A45),
                                focusedContainerColor = Color(0xFF0A0A12),
                                unfocusedContainerColor = Color(0xFF0A0A12)
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }

                    // Remember Me Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rememberMeVal = !rememberMeVal }
                    ) {
                        Checkbox(
                            checked = rememberMeVal,
                            onCheckedChange = { rememberMeVal = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF00E5FF),
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(
                            text = "Ghi nhớ phiên đăng nhập",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }

                    // Status/Error Message
                    if (!errorMessageVal.isNullOrEmpty()) {
                        Surface(
                            color = if (errorMessageVal!!.contains("kết nối")) Color(0x2000E5FF) else Color(0x20FF007F),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessageVal!!,
                                color = if (errorMessageVal!!.contains("kết nối")) Color(0xFF00E5FF) else Color(0xFFFF007F),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // Primary Login Button
                    Button(
                        onClick = { executeLogin() },
                        enabled = !isAuthenticatingVal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(if (!isAuthenticatingVal) 8.dp else 0.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF1A1A2A)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAuthenticatingVal) "ĐANG XÁC THỰC..." else "ĐĂNG NHẬP HỆ THỐNG",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            if (!isAuthenticatingVal) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Secondary Register Button
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gromapp.x10.mx/register"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi trình duyệt", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CHƯA CÓ TÀI KHOẢN? ĐĂNG KÝ NGAY",
                            color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            Text(
                text = "BẢN QUYỀN THUỘC VỀ NGUYỄN HOÀNG PHÚ © 2026 • NEW PING PRO",
                color = com.example.ui.theme.NeonFreeze.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
