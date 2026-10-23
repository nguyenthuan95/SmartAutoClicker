package com.example.ui.activity

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import com.example.engine.LogRepository
import com.example.engine.SmartClipboardManager

/**
 * TransparentClipReaderActivity:
 * Activity vô hình (1x1 pixel, alpha 0f) để lấy Window Focus chớp nhoáng (10-20ms)
 * giúp vượt qua hoàn toàn hạn chế bảo mật chặn Background Clipboard của Android 10+.
 * Tự động kết thúc ngay lập tức mà người dùng không hề nhận thấy.
 */
class TransparentClipReaderActivity : Activity() {

    private var hasRead = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            width = 1
            height = 1
            alpha = 0f
        }
        window.decorView.postDelayed({
            if (!hasRead && !isFinishing) {
                readAndFinish()
            }
        }, 200)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasRead) {
            readAndFinish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasRead && hasWindowFocus()) {
            readAndFinish()
        }
    }

    private fun readAndFinish() {
        hasRead = true
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm != null) {
                val clip = cm.primaryClip
                val text = clip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    SmartClipboardManager.onClipCapturedWithFocus(text)
                }
            }
        } catch (e: Exception) {
            LogRepository.warn("ClipboardFocus", "Lỗi đọc clipboard với focus: ${e.message}")
        } finally {
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
