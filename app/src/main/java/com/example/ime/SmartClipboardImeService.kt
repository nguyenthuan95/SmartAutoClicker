package com.example.ime

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.SmartClipboardManager

class SmartClipboardImeService : android.inputmethodservice.InputMethodService() {

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    color = Color(0xFF0F1115)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val history by SmartClipboardManager.historyFlow.collectAsState()
                        val nextItem = history.firstOrNull() ?: ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F2937))
                                .clickable {
                                    if (nextItem.isNotBlank()) {
                                        val valToPaste = SmartClipboardManager.poll(this@SmartClipboardImeService)
                                        if (valToPaste.isNotBlank()) {
                                            commitText(valToPaste)
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "⚡", fontSize = 14.sp)
                                Text(
                                    text = if (nextItem.isNotBlank()) "DÁN NEXT: $nextItem" else "Hàng đợi clipboard trống",
                                    color = if (nextItem.isNotBlank()) Color(0xFF58A6FF) else Color(0xFF8B949E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "(${history.size})",
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp
                            )
                        }

                        val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
                        val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
                        val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            row1.forEach { char ->
                                ImeKeyButton(text = char, modifier = Modifier.weight(1f)) { commitText(char) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            row2.forEach { char ->
                                ImeKeyButton(text = char, modifier = Modifier.weight(1f)) { commitText(char) }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            ImeKeyButton(text = "123", isSpecial = true, modifier = Modifier.weight(1.3f)) { commitText("123") }
                            row3.forEach { char ->
                                ImeKeyButton(text = char, modifier = Modifier.weight(1f)) { commitText(char) }
                            }
                            ImeKeyButton(icon = Icons.AutoMirrored.Filled.Backspace, isSpecial = true, modifier = Modifier.weight(1.3f)) { deleteBackward() }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ImeKeyButton(text = ",", modifier = Modifier.weight(1f)) { commitText(",") }
                            ImeKeyButton(text = "Dấu cách", modifier = Modifier.weight(4f)) { commitText(" ") }
                            ImeKeyButton(text = ".", modifier = Modifier.weight(1f)) { commitText(".") }
                            ImeKeyButton(icon = Icons.Default.KeyboardReturn, isSpecial = true, isHighlight = true, modifier = Modifier.weight(1.5f)) { performEnterAction() }
                        }
                    }
                }
            }
        }
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
    }

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(1, 0)
    }

    private fun performEnterAction() {
        val ic = currentInputConnection ?: return
        ic.commitText("\n", 1)
    }
}

@Composable
fun ImeKeyButton(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isSpecial: Boolean = false,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isHighlight -> Color(0xFF58A6FF)
        isSpecial -> Color(0xFF21262D)
        else -> Color(0xFF161B22)
    }
    val fg = if (isHighlight) Color(0xFF0F1115) else Color.White

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        } else if (text != null) {
            Text(text = text, color = fg, fontSize = if (text.length > 2) 11.sp else 16.sp, fontWeight = if (isSpecial || isHighlight) FontWeight.Bold else FontWeight.Normal)
        }
    }
}
