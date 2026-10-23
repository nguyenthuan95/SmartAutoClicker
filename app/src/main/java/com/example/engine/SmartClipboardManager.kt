package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Phần tử clipboard có timestamp và tự hết hạn sau 1 giờ nếu không dùng
 */
data class SmartClipboardItem(
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    @Volatile var lastAccessedAt: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMs: Long = SmartClipboardManager.DEFAULT_TTL_MS): Boolean {
        return (System.currentTimeMillis() - lastAccessedAt) > ttlMs
    }

    fun touch() {
        lastAccessedAt = System.currentTimeMillis()
    }
}

/**
 * SmartClipboardManager: Quản lý bộ nhớ tạm thông minh cho Script Engine.
 * - Tự động theo dõi lịch sử clipboard (lưu tối đa 50 mục gần nhất)
 * - Tự động hủy/xóa mục sau 1 giờ (1 hour TTL = 3,600,000ms) nếu không được truy cập/sử dụng
 * - Tự động trích xuất và chuẩn hóa Số điện thoại (Phone)
 * - Tự động trích xuất Mã vận đơn (Tracking code: SPX, VNPost, ViettelPost, J&T...)
 * - Tự động trích xuất Link / URL
 * - Trực tiếp tiêm dữ liệu vào ô nhập không phụ thuộc bàn phím
 */
object SmartClipboardManager {

    private const val MAX_HISTORY = 50
    const val DEFAULT_TTL_MS = 60 * 60 * 1000L // 1 giờ (3.600.000 ms) tự động hủy nếu không dùng

    private val _items = CopyOnWriteArrayList<SmartClipboardItem>()
    private val _historyFlow = MutableStateFlow<List<String>>(emptyList())
    val historyFlow: StateFlow<List<String>> = _historyFlow.asStateFlow()

    private var clipboardManager: ClipboardManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListenerAttached = false
    @Volatile
    private var lastSyncedRawText: String = ""

    // Định kỳ dọn dẹp các mục quá 1 giờ không dùng (quét mỗi 60 giây)
    private val cleanupRunnable = object : Runnable {
        override fun run() {
            cleanupExpiredItems()
            mainHandler.postDelayed(this, 60_000L)
        }
    }

    // Regex trích xuất SĐT thuần túy (+84 hoặc 03x, 05x, 07x, 08x, 09x)
    private val PHONE_REGEX = PatternRegex(Regex("""(?<!\w)(?:\+?84|0)[\s.\-]*\d{3}[\s.\-]*\d{3}[\s.\-]*\d{3}(?!\w)"""))
    // Regex trích xuất Mã đơn hàng / Mã vận đơn phổ biến (SPX: SPXVN..., VN...S, v.v.)
    private val TRACKING_REGEX = PatternRegex(Regex("""\b(?:SPX[A-Z0-9]{8,20}|VN\d{9,15}[A-Z0-9]?|[A-Z]{2,4}\d{8,15}[A-Z0-9]?)\b""", RegexOption.IGNORE_CASE))
    // Regex trích xuất URL / Link
    private val URL_REGEX = PatternRegex(Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE))

    fun init(context: Context) {
        if (isListenerAttached) return
        mainHandler.post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboardManager = cm
                cm?.let { manager ->
                    val initialText = getCurrentTextFromManager(manager)
                    if (initialText.isNotBlank()) {
                        lastSyncedRawText = initialText
                        pushToHistory(initialText, isInitial = true)
                    }

                    manager.addPrimaryClipChangedListener {
                        val clip = try { manager.primaryClip } catch (e: Exception) { null }
                        if (clip == null) {
                            readClipViaFocus(context, "ListenerDenied")
                        } else {
                            syncFromSystemClipboard(context)
                        }
                    }
                    isListenerAttached = true
                }
                mainHandler.removeCallbacks(cleanupRunnable)
                mainHandler.postDelayed(cleanupRunnable, 2000L)
            } catch (e: Exception) {
                LogRepository.warn("SmartClipboard", "Không thể lắng nghe clipboard: ${e.message}")
            }
        }
    }

    fun readClipViaFocus(context: Context, reason: String) {
        try {
            val intent = android.content.Intent(context, com.example.ui.activity.TransparentClipReaderActivity::class.java).apply {
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            LogRepository.warn("SmartClipboard", "readClipViaFocus failed: ${e.message}")
        }
    }

    fun onClipCapturedWithFocus(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotBlank() && trimmed != lastSyncedRawText) {
            lastSyncedRawText = trimmed
            pushToHistory(trimmed)
            LogRepository.info("SmartClipboard", "📋 Đã lưu clipboard mới (via Focus): '${trimmed.take(30)}${if (trimmed.length > 30) "..." else ""}' (Tổng: ${_items.size} mục)")
            showNextQueueToast()
        }
    }

    fun syncFromSystemClipboard(context: Context): String {
        return try {
            val cm = clipboardManager ?: (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            val text = cm?.let { getCurrentTextFromManager(it) } ?: ""
            if (text.isNotBlank() && text != lastSyncedRawText) {
                lastSyncedRawText = text
                pushToHistory(text)
                LogRepository.info("SmartClipboard", "📋 Đã lưu clipboard mới: '${text.take(30)}${if (text.length > 30) "..." else ""}' (Tổng: ${_items.size} mục)")
                showNextQueueToast()
            }
            text
        } catch (e: Exception) {
            ""
        }
    }

    private fun showNextQueueToast() {
        mainHandler.post {
            try {
                val appCtx = com.example.SmartClickerApp.instance ?: return@post
                if (_items.isNotEmpty()) {
                    val next = _items.first().text
                    android.widget.Toast.makeText(appCtx, "📥 Hàng đợi (${_items.size}): ${next.take(30)}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun getCurrentTextFromManager(manager: ClipboardManager): String {
        return try {
            val item = manager.primaryClip?.getItemAt(0)
            item?.text?.toString()?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun pushToHistory(text: String, isInitial: Boolean = false) {
        val rawTrimmed = text.trim()
        if (rawTrimmed.isEmpty()) return

        cleanupExpiredItems()

        val phoneExtracted = if (!TRACKING_REGEX.matches(rawTrimmed)) {
            extractPhone(rawTrimmed, formatted = true)
        } else ""

        val finalStoredText = if (phoneExtracted.isNotBlank()) {
            phoneExtracted
        } else {
            rawTrimmed
        }

        val existingIndex = _items.indexOfFirst { it.text == finalStoredText }
        if (existingIndex >= 0) {
            val item = _items.removeAt(existingIndex)
            item.touch()
            _items.add(0, item)
        } else {
            _items.add(0, SmartClipboardItem(finalStoredText))
        }

        while (_items.size > MAX_HISTORY) {
            _items.removeAt(_items.size - 1)
        }
        updateHistoryFlow()
    }

    private fun cleanupExpiredItems() {
        val countBefore = _items.size
        _items.removeAll { it.isExpired(DEFAULT_TTL_MS) }
        if (_items.size != countBefore) {
            updateHistoryFlow()
            LogRepository.info("SmartClipboard", "⏱ Đã tự động dọn ${countBefore - _items.size} mục clipboard quá 1 giờ không dùng.")
        }
    }

    private fun updateHistoryFlow() {
        _historyFlow.value = _items.map { it.text }
    }

    fun getText(context: Context): String {
        cleanupExpiredItems()
        val text = syncFromSystemClipboard(context)
        if (text.isNotBlank()) {
            return text
        }
        val top = _items.firstOrNull()
        top?.touch()
        return top?.text ?: ""
    }

    fun setText(context: Context, text: String) {
        val trimmed = text.trim()
        mainHandler.post {
            try {
                val cm = clipboardManager ?: (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                val clip = ClipData.newPlainText("smart_clipboard", trimmed)
                cm?.setPrimaryClip(clip)
                pushToHistory(trimmed)
            } catch (e: Exception) {
                LogRepository.error("SmartClipboard", "Lỗi đặt clipboard: ${e.message}")
            }
        }
    }

    fun getHistory(): List<String> {
        cleanupExpiredItems()
        return _items.map { it.text }
    }

    fun getItem(index: Int): String {
        cleanupExpiredItems()
        val item = _items.getOrNull(index) ?: return ""
        item.touch()
        return item.text
    }

    fun extractPhone(rawText: String? = null, context: Context? = null, formatted: Boolean = true): String {
        val text = rawText ?: (context?.let { getText(it) } ?: run {
            cleanupExpiredItems()
            val top = _items.firstOrNull()
            top?.touch()
            top?.text ?: ""
        })
        val clean = text.replace("[Oo]".toRegex(), "0")
        val match = PHONE_REGEX.find(clean) ?: return ""
        val digits = match.replace("[^\\d]".toRegex(), "")

        val standardized = when {
            digits.startsWith("84") && digits.length == 11 -> digits
            digits.startsWith("0") && digits.length == 10 -> "84" + digits.substring(1)
            else -> return ""
        }

        return if (formatted) {
            "+84 " + standardized.substring(2, 5) + " " + standardized.substring(5, 8) + " " + standardized.substring(8, 11)
        } else {
            "0" + standardized.substring(2)
        }
    }

    fun extractTrackingCode(rawText: String? = null, context: Context? = null): String {
        val text = rawText ?: (context?.let { getText(it) } ?: run {
            cleanupExpiredItems()
            val top = _items.firstOrNull()
            top?.touch()
            top?.text ?: ""
        })
        val match = TRACKING_REGEX.find(text)
        return match?.trim() ?: ""
    }

    fun extractUrl(rawText: String? = null, context: Context? = null): String {
        val text = rawText ?: (context?.let { getText(it) } ?: run {
            cleanupExpiredItems()
            val top = _items.firstOrNull()
            top?.touch()
            top?.text ?: ""
        })
        val match = URL_REGEX.find(text)
        return match?.trim() ?: ""
    }

    fun poll(context: Context? = null): String {
        context?.let { syncFromSystemClipboard(it) }
        cleanupExpiredItems()
        if (_items.isEmpty()) return ""
        val item = _items.removeAt(0)
        updateHistoryFlow()

        // Tự động đẩy mục tiếp theo (NEXT) lên hệ thống clipboard giống app gốc
        if (_items.isNotEmpty() && context != null) {
            try {
                val nextText = _items.first().text
                lastSyncedRawText = nextText
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Smart Clipboard Next", nextText)
                cm?.setPrimaryClip(clip)
                LogRepository.info("SmartClipboard", "➔ Đã tự động đẩy NEXT lên clipboard: '$nextText'")
            } catch (e: Exception) {
                // ignore
            }
        }

        return item.text
    }

    fun pollFifo(context: Context? = null): String {
        context?.let { syncFromSystemClipboard(it) }
        cleanupExpiredItems()
        if (_items.isEmpty()) return ""
        val item = _items.removeAt(_items.size - 1)
        updateHistoryFlow()
        return item.text
    }

    fun peek(context: Context? = null): String {
        context?.let { syncFromSystemClipboard(it) }
        cleanupExpiredItems()
        val item = _items.firstOrNull()
        item?.touch()
        return item?.text ?: ""
    }

    fun queueSize(): Int {
        cleanupExpiredItems()
        return _items.size
    }

    fun clearHistory() {
        _items.clear()
        _historyFlow.value = emptyList()
    }

    private class PatternRegex(private val regex: Regex) {
        fun find(input: String): String? {
            return regex.find(input)?.value
        }
        fun matches(input: String): Boolean {
            return regex.containsMatchIn(input)
        }
    }
}
