package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LogLevel
import com.example.data.model.LogSession
import com.example.engine.LogRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val logs by LogRepository.logs.collectAsStateWithLifecycle()
    val sessions by LogRepository.sessions.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val sessionTimeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var filterQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedSessionId by remember { mutableStateOf<Long?>(null) }

    val filteredLogs = remember(logs, filterQuery, selectedLevel, selectedSessionId) {
        logs.filter { entry ->
            val matchSession = (selectedSessionId == null || entry.sessionId == selectedSessionId)
            val matchLevel = (selectedLevel == null || entry.level == selectedLevel)
            val matchQuery = (filterQuery.isEmpty() || entry.message.contains(filterQuery, ignoreCase = true) || entry.tag.contains(filterQuery, ignoreCase = true))
            matchSession && matchLevel && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thanh tìm kiếm và nút hành động
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Tìm kiếm log...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { filterQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("log_search_field")
            )

            // Nút Xuất Log
            IconButton(
                onClick = {
                    val logText = LogRepository.exportToString(selectedSessionId)
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, logText)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Xuất Nhật Ký Chạy")
                    context.startActivity(shareIntent)
                },
                modifier = Modifier.testTag("export_logs_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = "Chia sẻ log")
            }

            // Nút Xóa Log
            IconButton(
                onClick = {
                    LogRepository.clear()
                    selectedSessionId = null
                },
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Xóa toàn bộ log", tint = MaterialTheme.colorScheme.error)
            }
        }

        // Thanh Lọc theo Phiên chạy (Session Filter)
        if (sessions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Phiên chạy:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FilterChip(
                    selected = selectedSessionId == null,
                    onClick = { selectedSessionId = null },
                    label = { Text("Tất cả phiên (${sessions.size})") }
                )
                sessions.forEach { sess ->
                    val isSelected = selectedSessionId == sess.id
                    val timeStr = sessionTimeFormat.format(Date(sess.startTimeMs))
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSessionId = if (isSelected) null else sess.id },
                        label = { Text("▶ [$timeStr] ${sess.name}") }
                    )
                }
            }
        }

        // Danh sách logs
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (logs.isEmpty()) "Chưa có bản ghi nhật ký nào." else "Không có log khớp với bộ lọc.",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(filteredLogs) { index, entry ->
                    val isFirstItem = (index == 0)
                    val prevEntry = if (index > 0) filteredLogs[index - 1] else null
                    val isNewSession = prevEntry != null && prevEntry.sessionId != entry.sessionId
                    val isSessionStartHeader = entry.message.contains("=== BẮT ĐẦU PHIÊN CHẠY") || entry.message.contains("KHỞI ĐỘNG RUN")

                    // VÁCH NGĂN CÁCH GIỮA CÁC LẦN BẤM (Phiên chạy / Run khác nhau)
                    if (isNewSession || (isSessionStartHeader && !isFirstItem)) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD29922).copy(alpha = 0.6f))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2D2200)
                            ) {
                                Text(
                                    text = "⚡ LẦN BẤM MỚI (Session: ${if (entry.sessionId > 0) dateFormat.format(Date(entry.sessionId)) else "Manual"})",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFD29922).copy(alpha = 0.6f))
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSessionStartHeader) Color(0xFF161B22) else Color(0xFF0F1115),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Thời gian HH:mm:ss.SSS màu xám mờ
                            Text(
                                text = dateFormat.format(Date(entry.timestamp)),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF6E7681),
                                modifier = Modifier.padding(top = 1.dp)
                            )

                            // Huy hiệu Level bo góc giống ảnh mẫu
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = when (entry.level) {
                                    LogLevel.INFO -> Color(0xFF0E3A66)
                                    LogLevel.ACTION -> Color(0xFF135228)
                                    LogLevel.OCR -> Color(0xFF4A1E6D)
                                    LogLevel.WARN -> Color(0xFF704200)
                                    LogLevel.ERROR -> Color(0xFF6B1818)
                                }
                            ) {
                                Text(
                                    text = entry.level.name,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = when (entry.level) {
                                        LogLevel.INFO -> Color(0xFF58A6FF)
                                        LogLevel.ACTION -> Color(0xFF3FB950)
                                        LogLevel.OCR -> Color(0xFFBC8CFF)
                                        LogLevel.WARN -> Color(0xFFD29922)
                                        LogLevel.ERROR -> Color(0xFFF85149)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Nội dung Tag & Message màu trắng/xám sáng
                            Text(
                                text = "[${entry.tag}] ${entry.message}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (isSessionStartHeader) Color(0xFFFFD54F) else Color(0xFFC9D1D9),
                                fontWeight = if (isSessionStartHeader) FontWeight.Bold else FontWeight.Normal,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
