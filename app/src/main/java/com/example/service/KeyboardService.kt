package com.example.service

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.database.ClipboardItem
import com.example.database.CustomTheme
import com.example.database.KeyboardDatabase
import com.example.keyboard.emoji.EmojiData
import com.example.keyboard.engine.AvroEngine
import com.example.keyboard.layouts.KeyActionType
import com.example.keyboard.layouts.KeyDef
import com.example.keyboard.layouts.KeyboardLayouts
import com.example.keyboard.themes.KeyboardThemeManager
import com.example.keyboard.themes.ThemeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class KeyboardService : LifecycleInputMethodService() {

    private lateinit var database: KeyboardDatabase
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Clipboard Listener
    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        saveCopiedClip()
    }

    // Interactive mutable states for composing and display
    private var currentLayoutState = mutableStateOf("Avro")
    private var selectedThemeState = mutableStateOf<ThemeColors?>(null)
    private var heightScaleState = mutableStateOf(1.0f) // 0.8f (Small) to 1.4f (X-Large)
    private var numRowEnabledState = mutableStateOf(true)
    private var customThemesList = mutableStateListOf<CustomTheme>()

    override fun onCreate() {
        super.onCreate()
        database = KeyboardDatabase.getInstance(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)

        // Load general user preferences
        val prefs = getSharedPreferences("banglaflow_prefs", Context.MODE_PRIVATE)
        currentLayoutState.value = prefs.getString("layout", "Avro") ?: "Avro"
        heightScaleState.value = prefs.getFloat("height_scale", 1.0f)
        numRowEnabledState.value = prefs.getBoolean("num_row", true)

        loadThemesAndInitializeColors()
    }

    private fun loadThemesAndInitializeColors() {
        serviceScope.launch {
            // Read active custom theme IDs from SharedPreferences
            val prefs = getSharedPreferences("banglaflow_prefs", Context.MODE_PRIVATE)
            val themeId = prefs.getInt("active_theme_id", -1)

            if (themeId != -1) {
                val foundTheme = withContext(Dispatchers.IO) {
                    database.dao().getThemeById(themeId)
                }
                if (foundTheme != null) {
                    selectedThemeState.value = KeyboardThemeManager.resolveTheme(foundTheme)
                    return@launch
                }
            }

            // Fallback to primary built-in
            selectedThemeState.value = KeyboardThemeManager.presets[0]
        }
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        super.onDestroy()
    }

    private fun saveCopiedClip() {
        val manager = clipboardManager ?: return
        if (manager.hasPrimaryClip()) {
            val clipData = manager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty()) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            database.dao().insertClipboard(ClipboardItem(text = text))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        super.onCreateInputView()
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)

            setContent {
                val layoutType by currentLayoutState
                val activeTheme = selectedThemeState.value ?: KeyboardThemeManager.presets[0]
                val heightScale by heightScaleState
                val isNumRowEnabled by numRowEnabledState

                // Voice recognition states
                var isListeningVoice by remember { mutableStateOf(false) }
                var voiceTextResult by remember { mutableStateOf("") }

                // Mode toggles
                var isClipboardOpen by remember { mutableStateOf(false) }
                var isEmojiOpen by remember { mutableStateOf(false) }

                // Compose buffer for Avro Phonetics
                var composeBuffer by remember { mutableStateOf("") }
                
                // Track shift key
                var isShiftActive by remember { mutableStateOf(false) }

                // Query database list of clipboard items
                val clipboardItems = remember { mutableStateListOf<ClipboardItem>() }
                LaunchedEffect(isClipboardOpen) {
                    if (isClipboardOpen) {
                        database.dao().getAllClipboardFlow().firstOrNull()?.let {
                            clipboardItems.clear()
                            clipboardItems.addAll(it)
                        }
                    }
                }

                Surface(
                    color = activeTheme.backgroundColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(activeTheme.backgroundColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding() // Edge-to-Edge protection
                    ) {
                        // SUGGESTIONS BAR OR UTILITIES BAR (SETTINGS / AUDIO / THEMES)
                        SuggestionOrActionBar(
                            layoutType = layoutType,
                            composeBuffer = composeBuffer,
                            activeTheme = activeTheme,
                            voiceActive = isListeningVoice,
                            voiceText = voiceTextResult,
                            onToggleClipboard = {
                                isClipboardOpen = !isClipboardOpen
                                isEmojiOpen = false
                            },
                            onToggleEmoji = {
                                isEmojiOpen = !isEmojiOpen
                                isClipboardOpen = false
                            },
                            onToggleVoice = {
                                if (isListeningVoice) {
                                    isListeningVoice = false
                                } else {
                                    isListeningVoice = true
                                    triggerVoiceTyping { speech ->
                                        isListeningVoice = false
                                        voiceTextResult = speech
                                        currentInputConnection?.commitText(speech, 1)
                                    }
                                }
                            },
                            onCycleLayout = {
                                val order = listOf("Avro", "Probhat", "National", "English")
                                val nextIndex = (order.indexOf(layoutType) + 1) % order.size
                                updateLayoutPreference(order[nextIndex])
                            },
                            onSuggestionClicked = { suggestion ->
                                currentInputConnection?.let { ic ->
                                    if (layoutType == "Avro" && composeBuffer.isNotEmpty()) {
                                        ic.commitText(suggestion, 1)
                                        ic.commitText(" ", 1)
                                        composeBuffer = ""
                                    } else {
                                        ic.commitText(suggestion, 1)
                                    }
                                }
                            }
                        )

                        // EXPANDED UTILITY SHELF (CLIPBOARD OR EMOJI GRID)
                        AnimatedVisibility(
                            visible = isClipboardOpen || isEmojiOpen,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((150 * heightScale).dp)
                                    .background(activeTheme.backgroundColor.copy(alpha = 0.95f))
                            ) {
                                if (isClipboardOpen) {
                                    ClipboardShelf(
                                        items = clipboardItems,
                                        activeTheme = activeTheme,
                                        onClipSelected = { txt ->
                                            currentInputConnection?.commitText(txt, 1)
                                            isClipboardOpen = false
                                        },
                                        onClearAll = {
                                            serviceScope.launch(Dispatchers.IO) {
                                                database.dao().clearAllClipboard()
                                                withContext(Dispatchers.Main) {
                                                    clipboardItems.clear()
                                                }
                                            }
                                        }
                                    )
                                } else if (isEmojiOpen) {
                                    EmojiShelf(
                                        activeTheme = activeTheme,
                                        onEmojiSelected = { emo ->
                                            currentInputConnection?.commitText(emo, 1)
                                        }
                                    )
                                }
                            }
                        }

                        // STANDARD DYNAMIC KEYBOARD GRID
                        KeyboardGrid(
                            layoutType = layoutType,
                            activeTheme = activeTheme,
                            heightScale = heightScale,
                            isNumRowEnabled = isNumRowEnabled,
                            isShiftActive = isShiftActive,
                            composeBuffer = composeBuffer,
                            onKeyPressed = { keyDef ->
                                handleKeyboardKeyPress(
                                    keyDef = keyDef,
                                    currentLayout = layoutType,
                                    isShiftActive = isShiftActive,
                                    currentCompose = composeBuffer,
                                    onUpdateCompose = { composeBuffer = it },
                                    onToggleShift = { isShiftActive = !isShiftActive }
                                )
                            },
                            onSpaceDragLeft = {
                                currentInputConnection?.sendKeyEvent(
                                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
                                )
                                currentInputConnection?.sendKeyEvent(
                                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)
                                )
                            },
                            onSpaceDragRight = {
                                currentInputConnection?.sendKeyEvent(
                                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                                )
                                currentInputConnection?.sendKeyEvent(
                                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
                                )
                            }
                        )
                    }
                }
            }
        }
        return composeView
    }

    private fun updateLayoutPreference(newLayout: String) {
        currentLayoutState.value = newLayout
        getSharedPreferences("banglaflow_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("layout", newLayout)
            .apply()
    }

    private fun handleKeyboardKeyPress(
        keyDef: KeyDef,
        currentLayout: String,
        isShiftActive: Boolean,
        currentCompose: String,
        onUpdateCompose: (String) -> Unit,
        onToggleShift: () -> Unit
    ) {
        val ic = currentInputConnection ?: return

        if (keyDef.isAction) {
            when (keyDef.actionType) {
                KeyActionType.SHIFT -> {
                    onToggleShift()
                }
                KeyActionType.BACKSPACE -> {
                    if (currentLayout == "Avro" && currentCompose.isNotEmpty()) {
                        val updated = currentCompose.dropLast(1)
                        onUpdateCompose(updated)
                    } else {
                        // Standard delete backward
                        ic.deleteSurroundingText(1, 0)
                    }
                }
                KeyActionType.ENTER -> {
                    if (currentLayout == "Avro" && currentCompose.isNotEmpty()) {
                        ic.commitText(AvroEngine.convert(currentCompose), 1)
                        onUpdateCompose("")
                    }
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                }
                KeyActionType.SPACE -> {
                    if (currentLayout == "Avro" && currentCompose.isNotEmpty()) {
                        ic.commitText(AvroEngine.convert(currentCompose), 1)
                        onUpdateCompose("")
                    }
                    ic.commitText(" ", 1)
                }
                KeyActionType.MODE_CHANGE -> {
                    val order = listOf("Avro", "Probhat", "National", "English")
                    val nextIndex = (order.indexOf(currentLayout) + 1) % order.size
                    updateLayoutPreference(order[nextIndex])
                }
                KeyActionType.HIDE_KEYBOARD -> {
                    requestHideSelf(0)
                }
                KeyActionType.CLEAR_BUFFER -> {
                    onUpdateCompose("")
                }
                else -> { /* Do nothing */ }
            }
        } else {
            // Typing characters
            val literalValue = if (isShiftActive) keyDef.shiftText else keyDef.mainText
            if (currentLayout == "Avro") {
                // If it is standard space, or alphanumeric, add to compose
                if (literalValue.firstOrNull()?.isLetterOrDigit() == true) {
                    val nextCompose = currentCompose + literalValue
                    onUpdateCompose(nextCompose)
                } else {
                    // Commit current and append
                    if (currentCompose.isNotEmpty()) {
                        ic.commitText(AvroEngine.convert(currentCompose), 1)
                        onUpdateCompose("")
                    }
                    ic.commitText(literalValue, 1)
                }
            } else {
                // Direct layout output
                ic.commitText(literalValue, 1)
            }
        }
    }

    private fun triggerVoiceTyping(onResult: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD") // Default to Bangla Flow speech recognition
        }
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@KeyboardService, "বলুন...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    else -> "Speech input timeout"
                }
                Toast.makeText(this@KeyboardService, msg, Toast.LENGTH_SHORT).show()
                speechRecognizer.destroy()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onResult(matches[0])
                }
                speechRecognizer.destroy()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }
}

// ---------------- COMPOSE UI REUSABLE KEYBOARD SUB-VIEW SCHEMES ----------------

@Composable
fun SuggestionOrActionBar(
    layoutType: String,
    composeBuffer: String,
    activeTheme: ThemeColors,
    voiceActive: Boolean,
    voiceText: String,
    onToggleClipboard: () -> Unit,
    onToggleEmoji: () -> Unit,
    onToggleVoice: () -> Unit,
    onCycleLayout: () -> Unit,
    onSuggestionClicked: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(activeTheme.keyColor.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quick Toggle Action Buttons (Clipboard, Emoji, Voice, Cycle Layout Icon)
        Row(
            modifier = Modifier.wrapContentWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleClipboard,
                modifier = Modifier.testTag("clip_btn")
            ) {
                Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = "Clipboard",
                    tint = activeTheme.textColor
                )
            }
            IconButton(
                onClick = onToggleEmoji,
                modifier = Modifier.testTag("emoji_btn")
            ) {
                Icon(
                    Icons.Default.EmojiEmotions,
                    contentDescription = "Emoji Selector",
                    tint = activeTheme.textColor
                )
            }
            IconButton(
                onClick = onToggleVoice,
                modifier = Modifier.testTag("voice_btn")
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (voiceActive) Color.Red else activeTheme.textColor
                )
            }
        }

        // Layout indicator and divider
        Text(
            text = layoutType,
            color = activeTheme.textColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { onCycleLayout() }
                .padding(horizontal = 4.dp)
        )

        Spacer(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(activeTheme.textColor.copy(alpha = 0.2f))
                .padding(horizontal = 4.dp)
        )

        // SUGGESTIONS HORIZONTAL LIST
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            if (layoutType == "Avro" && composeBuffer.isNotEmpty()) {
                val avroMapped = AvroEngine.convert(composeBuffer)
                val suggestionOption2 = if (composeBuffer.length > 2) avroMapped + "র" else avroMapped + "ই"
                val suggestions = listOf(avroMapped, suggestionOption2, composeBuffer)

                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(suggestions) { keyword ->
                        Text(
                            text = keyword,
                            color = activeTheme.textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(activeTheme.keyColor.copy(alpha = 0.8f))
                                .clickable { onSuggestionClicked(keyword) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                // Static dictionary standard helpful phrases depending on layout
                val defaultBanglaKeywords = listOf("বাংলাদেশ", "ধন্যবাদ", "কেমন আছেন", "আমি", "তুমি", "সুন্দর")
                val englishKeywords = listOf("The", "I", "You", "Thank you", "Awesome", "Hello")
                val recommendations = if (layoutType == "English") englishKeywords else defaultBanglaKeywords

                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recommendations) { term ->
                        Text(
                            text = term,
                            color = activeTheme.textColor.copy(alpha = 0.82f),
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { onSuggestionClicked(term) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClipboardShelf(
    items: List<ClipboardItem>,
    activeTheme: ThemeColors,
    onClipSelected: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ক্লিপবোর্ড ম্যানেজার (Clipboard)",
                color = activeTheme.textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "ক্লিয়ার করুন (Clear)",
                color = Color.Red,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onClearAll() }
                    .padding(4.dp)
            )
        }
        
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "কপি করা কোনো লেখা নেই",
                    color = activeTheme.textColor.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items) { clip ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = activeTheme.keyColor),
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(160.dp)
                            .clickable { onClipSelected(clip.text) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = clip.text,
                                color = activeTheme.textColor,
                                fontSize = 11.sp,
                                maxLines = 4,
                                textAlign = TextAlign.Left
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiShelf(
    activeTheme: ThemeColors,
    onEmojiSelected: (String) -> Unit
) {
    var activeCategory by remember { mutableStateOf("Smileys") }
    val emojiList = EmojiData.categories[activeCategory] ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        // Categories list
        TabRow(
            selectedTabIndex = EmojiData.categories.keys.indexOf(activeCategory),
            containerColor = Color.Transparent,
            contentColor = activeTheme.textColor,
            modifier = Modifier.height(36.dp)
        ) {
            EmojiData.categories.keys.forEach { category ->
                Tab(
                    selected = activeCategory == category,
                    onClick = { activeCategory = category },
                    text = { Text(category, fontSize = 10.sp, maxLines = 1) }
                )
            }
        }

        // Emoji grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 38.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(emojiList) { smile ->
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onEmojiSelected(smile) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(smile, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun KeyboardGrid(
    layoutType: String,
    activeTheme: ThemeColors,
    heightScale: Float,
    isNumRowEnabled: Boolean,
    isShiftActive: Boolean,
    composeBuffer: String,
    onKeyPressed: (KeyDef) -> Unit,
    onSpaceDragLeft: () -> Unit,
    onSpaceDragRight: () -> Unit
) {
    // Determine active key matrix
    val rawMatrix = when (layoutType) {
        "English" -> KeyboardLayouts.englishLayout
        "Probhat" -> KeyboardLayouts.probhatLayout
        "National" -> KeyboardLayouts.nationalLayout
        "Avro" -> KeyboardLayouts.avroLayout
        else -> KeyboardLayouts.englishLayout
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(activeTheme.backgroundColor)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Number row (optional toggle)
        if (isNumRowEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((34 * heightScale).dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০").forEachIndexed { index, num ->
                    val engNum = ((index + 1) % 10).toString()
                    val displayedNum = if (layoutType == "English") engNum else num
                    Box(
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(activeTheme.cornerRadius.dp))
                            .background(activeTheme.keyColor.copy(alpha = 0.5f))
                            .clickable { onKeyPressed(KeyDef(displayedNum)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayedNum,
                            color = activeTheme.textColor.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Layout rows
        rawMatrix.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((42 * heightScale).dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEach { key ->
                    KeyView(
                        keyDef = key,
                        isShiftEnabled = isShiftActive,
                        activeTheme = activeTheme,
                        layoutType = layoutType,
                        modifier = Modifier.weight(key.weight),
                        onPressed = { onKeyPressed(key) },
                        onSpaceDragLeft = onSpaceDragLeft,
                        onSpaceDragRight = onSpaceDragRight
                    )
                }
            }
        }

        // Bottom utility row (Layout selector, spacebar slider, hide key)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height((42 * heightScale).dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Language selector button
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(activeTheme.cornerRadius.dp))
                    .background(activeTheme.keyColor)
                    .clickable { onKeyPressed(KeyDef("", isAction = true, actionType = KeyActionType.MODE_CHANGE)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = "Switch Keyboard",
                    tint = activeTheme.textColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            // SPACE BAR with swipe gesture for cursor control
            var totalDragDist by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .weight(5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(activeTheme.cornerRadius.dp))
                    .background(activeTheme.keyColor)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { totalDragDist = 0f },
                            onDragCancel = { totalDragDist = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragDist += dragAmount
                                if (totalDragDist > 40f) {
                                    onSpaceDragRight()
                                    totalDragDist = 0f
                                } else if (totalDragDist < -40f) {
                                    onSpaceDragLeft()
                                    totalDragDist = 0f
                                }
                            }
                        )
                    }
                    .clickable { onKeyPressed(KeyDef(" ", isAction = true, actionType = KeyActionType.SPACE)) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (layoutType == "Avro" && composeBuffer.isNotEmpty()) composeBuffer else "Spacebar (সোয়াইপ করুন)",
                    color = activeTheme.textColor.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    fontWeight = FontWeight.Normal
                )
            }

            // Period button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(activeTheme.cornerRadius.dp))
                    .background(activeTheme.keyColor)
                    .clickable { onKeyPressed(KeyDef(if (layoutType == "English") "." else "।")) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (layoutType == "English") "." else "।",
                    color = activeTheme.textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Hide Keyboard key
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(activeTheme.cornerRadius.dp))
                    .background(activeTheme.keyColor)
                    .clickable { onKeyPressed(KeyDef("", isAction = true, actionType = KeyActionType.HIDE_KEYBOARD)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.KeyboardHide,
                    contentDescription = "Hide Keyboard",
                    tint = activeTheme.textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyView(
    keyDef: KeyDef,
    isShiftEnabled: Boolean,
    activeTheme: ThemeColors,
    layoutType: String,
    modifier: Modifier,
    onPressed: () -> Unit,
    onSpaceDragLeft: () -> Unit,
    onSpaceDragRight: () -> Unit
) {
    val displayValue = if (isShiftEnabled) {
        keyDef.shiftText
    } else {
        keyDef.mainText
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(activeTheme.cornerRadius.dp))
            .background(
                if (keyDef.isAction) activeTheme.keyColor.copy(alpha = 0.72f) 
                else activeTheme.keyColor
            )
            .combinedClickable(
                onClick = onPressed,
                onLongClick = {
                    // Quick numeric key alternative or special symbols
                    onPressed()
                }
            )
            .testTag("key_${keyDef.mainText}"),
        contentAlignment = Alignment.Center
    ) {
        if (keyDef.isAction) {
            when (keyDef.actionType) {
                KeyActionType.BACKSPACE -> {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Delete",
                        tint = activeTheme.textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                KeyActionType.SHIFT -> {
                    Icon(
                        if (isShiftEnabled) Icons.Default.VerticalAlignTop else Icons.Default.VerticalAlignBottom,
                        contentDescription = "Shift",
                        tint = if (isShiftEnabled) Color.Green else activeTheme.textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                else -> {
                    Text(
                        text = displayValue,
                        color = activeTheme.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayValue,
                    color = activeTheme.textColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (layoutType == "English") 15.sp else 13.sp
                )
            }
        }
    }
}
