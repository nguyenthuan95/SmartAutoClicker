package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppRepository
import com.example.data.model.ScriptEntity
import com.example.engine.ExecutionManager
import com.example.engine.SampleScripts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    repository: AppRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scripts by repository.allScripts.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedScriptId by remember { mutableStateOf<Long?>(null) }
    var scriptName by remember { mutableStateOf("") }
    var scriptDesc by remember { mutableStateOf("") }
    var scriptCode by remember { mutableStateOf("") }

    // Tự động load script đầu tiên khi danh sách tải xong
    LaunchedEffect(scripts) {
        if (scripts.isNotEmpty() && selectedScriptId == null) {
            val first = scripts.first()
            selectedScriptId = first.id
            scriptName = first.name
            scriptDesc = first.description
            scriptCode = first.code
        }
    }

    fun selectScript(s: ScriptEntity) {
        selectedScriptId = s.id
        scriptName = s.name
        scriptDesc = s.description
        scriptCode = s.code
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Hàng chọn kịch bản & nút Thêm mới
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var expandedDropdown by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = scriptName.ifEmpty { "Chọn kịch bản..." },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false }
                ) {
                    scripts.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(item.name, fontWeight = FontWeight.SemiBold)
                                    if (item.isPreset) {
                                        Text("[Script Mẫu]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            onClick = {
                                selectScript(item)
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    selectedScriptId = 0L
                    scriptName = "Script mới #${System.currentTimeMillis() % 1000}"
                    scriptDesc = "Mô tả script"
                    scriptCode = """
// Khởi tạo script mới
log("Bắt đầu thực thi script...");
toast("Xin chào!");

var size = screenSize();
log("Kích thước màn hình: " + size.width + "x" + size.height);

// Thao tác mẫu:
// click(500, 1000);
// sleep(1000);

log("Hoàn thành.");
                    """.trimIndent()
                },
                modifier = Modifier.testTag("new_script_button")
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = "Tạo mới")
            }

            IconButton(
                onClick = {
                    if (selectedScriptId != null && selectedScriptId != 0L) {
                        coroutineScope.launch {
                            val target = scripts.find { it.id == selectedScriptId }
                            if (target != null) {
                                repository.deleteScript(target)
                                selectedScriptId = null
                                Toast.makeText(context, "Đã xóa script", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
            }
        }

        // Tên và mô tả ngắn
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = scriptName,
                onValueChange = { scriptName = it },
                label = { Text("Tên script") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    coroutineScope.launch {
                        val entity = ScriptEntity(
                            id = selectedScriptId ?: 0L,
                            name = scriptName.trim().ifEmpty { "Script không tên" },
                            description = scriptDesc.trim(),
                            code = scriptCode,
                            isPreset = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        if (selectedScriptId == null || selectedScriptId == 0L) {
                            val newId = repository.saveScript(entity)
                            selectedScriptId = newId
                        } else {
                            repository.updateScript(entity)
                        }
                        Toast.makeText(context, "Đã lưu script thành công!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("save_script_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Lưu")
            }
        }

        // Thanh tiện ích: Đếm dòng + Nút Xóa sạch + Nút Dán đè tránh bị dán lặp code
        val lineCount = remember(scriptCode) { scriptCode.lines().size }
        val isPotentiallyDuplicated = remember(scriptCode) {
            scriptCode.contains("🚀 [SPX] Bắt đầu") && scriptCode.indexOf("🚀 [SPX] Bắt đầu") != scriptCode.lastIndexOf("🚀 [SPX] Bắt đầu")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Mã nguồn ($lineCount dòng, ${scriptCode.length} ký tự)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPotentiallyDuplicated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPotentiallyDuplicated) {
                    Text(
                        text = "⚠ Phát hiện code bị dán lặp 2 lần!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Nút Dán đè từ Clipboard (xóa sạch code cũ rồi dán trực tiếp chuỗi hoàn chỉnh từ Clipboard, tránh giới hạn IME commitText)
                OutlinedButton(
                    onClick = {
                        try {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipItem = clipboard.primaryClip?.getItemAt(0)
                            val clip = clipItem?.coerceToText(context)?.toString() ?: ""
                            if (clip.isNotBlank()) {
                                scriptCode = clip
                                Toast.makeText(context, "Đã dán đè mã mới (${clip.length} ký tự, ${clip.lines().size} dòng)!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Bộ nhớ tạm (Clipboard) đang trống!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi đọc clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dán đè", fontSize = 11.sp)
                }

                // Nút Xóa sạch code
                OutlinedButton(
                    onClick = {
                        scriptCode = ""
                        Toast.makeText(context, "Đã xóa sạch nội dung", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xóa hết", fontSize = 11.sp)
                }
            }
        }

        // Khung soạn thảo mã nguồn (Code Editor) - Chiếm trọn không gian, hiển thị rộng rãi
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            OutlinedTextField(
                value = scriptCode,
                onValueChange = { scriptCode = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false
                ),
                placeholder = { Text("// Viết code JavaScript tại đây...") },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("code_editor_field")
            )
        }
    }
}
