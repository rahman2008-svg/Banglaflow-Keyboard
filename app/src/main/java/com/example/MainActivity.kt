package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.ClipboardItem
import com.example.database.CustomTheme
import com.example.database.KeyboardDatabase
import com.example.keyboard.themes.KeyboardThemeManager
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = {
                                Text(
                                    "BanglaFlow Keyboard",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { KeyboardDatabase.getInstance(context) }

    // Settings States
    var selectedLayout by remember { mutableStateOf("Avro") }
    var heightScale by remember { mutableStateOf(1.0f) }
    var numRowEnabled by remember { mutableStateOf(true) }

    // Themes states
    val customThemes = remember { mutableStateListOf<CustomTheme>() }
    var activeThemeId by remember { mutableStateOf(-1) }

    // Clipboard and Test states
    val customClips = remember { mutableStateListOf<ClipboardItem>() }
    var testInputText by remember { mutableStateOf("") }

    // Dialog flags
    var isThemeCreatorOpen by remember { mutableStateOf(false) }

    // Theme values for Custom Theme Editor
    var newThemeName by remember { mutableStateOf("আমার থিম (My Theme)") }
    var newThemeBgColor by remember { mutableStateOf(Color(0xFF1E293B)) }
    var newThemeKeyColor by remember { mutableStateOf(Color(0xFF334155)) }
    var newThemeTextColor by remember { mutableStateOf(Color(0xFFF8FAFC)) }
    var newThemeRadius by remember { mutableStateOf(8f) }

    // Load initial user state
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("banglaflow_prefs", Context.MODE_PRIVATE)
        selectedLayout = prefs.getString("layout", "Avro") ?: "Avro"
        heightScale = prefs.getFloat("height_scale", 1.0f)
        numRowEnabled = prefs.getBoolean("num_row", true)
        activeThemeId = prefs.getInt("active_theme_id", -1)

        // Loading saved database values
        scope.launch {
            database.dao().getAllThemesFlow().collect { themes ->
                customThemes.clear()
                customThemes.addAll(themes)
            }
        }
        scope.launch {
            database.dao().getAllClipboardFlow().collect { clips ->
                customClips.clear()
                customClips.addAll(clips)
            }
        }
    }

    // Save Preference Helper
    val savePref = { key: String, changeValue: Any ->
        val prefs = context.getSharedPreferences("banglaflow_prefs", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        when (changeValue) {
            is String -> edit.putString(key, changeValue)
            is Float -> edit.putFloat(key, changeValue)
            is Boolean -> edit.putBoolean(key, changeValue)
            is Int -> edit.putInt(key, changeValue)
        }
        edit.apply()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome and App Setup section
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "স্বাগতম! BanglaFlow সেটিংস",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "সহজ ও দ্রুত অফলাইন বাংলা ফোনেটিক এবং স্লেট কিবোর্ড ব্যবহার করুন সম্পূর্ণ বিজ্ঞাপন্মুক্ত ও নিরাপদ উপায়ে।",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Guided setup buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                    Toast.makeText(context, "BanglaFlow চালু করুন", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setup_enable_btn")
                        ) {
                            Text("১. কিবোর্ড চালু", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val im = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                if (im != null) {
                                    im.showInputMethodPicker()
                                } else {
                                    Toast.makeText(context, "Input Method Picker unavailable", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setup_switch_btn")
                        ) {
                            Text("২. কিবোর্ড নির্বাচন", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Layout settings
        item {
            Column {
                Text(
                    text = "কিবোর্ড কনফিগারেশন (Configuration)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Num Row toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("নাম্বার রো দেখান (Number Row Row)", fontSize = 13.sp)
                            Switch(
                                checked = numRowEnabled,
                                onCheckedChange = {
                                    numRowEnabled = it
                                    savePref("num_row", it)
                                }
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                        // Keyboard Height Resizer Slider
                        Text(
                            text = "কিবোর্ডের উচ্চতা নিয়ন্ত্রণ (Keyboard Height)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ছোট", fontSize = 11.sp, color = Color.Gray)
                            Slider(
                                value = heightScale,
                                onValueChange = {
                                    heightScale = it
                                    savePref("height_scale", it)
                                },
                                valueRange = 0.8f..1.3f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("বড়", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // Themes manager
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "থিম স্টোর ও কাস্টম থিম (Themes)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { isThemeCreatorOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Custom Theme")
                        Text("থিম তৈরি", fontSize = 12.sp)
                    }
                }

                // Preset themes scrolling view
                Text(
                    "প্রিলোডেড স্টাইল সংগ্রহ (Presets):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(KeyboardThemeManager.presets) { preset ->
                        val isSelected = activeThemeId == -1 && preset.name == "AMOLED Dark" // demo link
                        Card(
                            colors = CardDefaults.cardColors(containerColor = preset.backgroundColor),
                            modifier = Modifier
                                .size(width = 110.dp, height = 75.dp)
                                .clickable {
                                    activeThemeId = -1
                                    savePref("active_theme_id", -1)
                                    Toast
                                        .makeText(
                                            context,
                                            "${preset.name} সক্রিয় করা হয়েছে",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    preset.name,
                                    color = preset.textColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Mock keys preview
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    repeat(4) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 16.dp, height = 18.dp)
                                                .clip(RoundedCornerShape((preset.cornerRadius / 2).dp))
                                                .background(preset.keyColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("ক", color = preset.textColor, fontSize = 7.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Display Custom Created Themes if any exists
                if (customThemes.isNotEmpty()) {
                    Text(
                        "আপনার কাস্টমাইজড থিম সমূহ (Custom):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(customThemes) { customTheme ->
                            val isSelected = activeThemeId == customTheme.id
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(customTheme.backgroundColor)),
                                modifier = Modifier
                                    .size(width = 110.dp, height = 75.dp)
                                    .clickable {
                                        activeThemeId = customTheme.id
                                        savePref("active_theme_id", customTheme.id)
                                        Toast
                                            .makeText(
                                                context,
                                                "${customTheme.name} সক্রিয় করা হয়েছে",
                                                Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            customTheme.name,
                                            color = Color(customTheme.textColor),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete theme",
                                            tint = Color.Red,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable {
                                                    scope.launch(Dispatchers.IO) {
                                                        database.dao().deleteTheme(customTheme)
                                                        if (activeThemeId == customTheme.id) {
                                                            activeThemeId = -1
                                                            savePref("active_theme_id", -1)
                                                        }
                                                    }
                                                }
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        repeat(4) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 16.dp, height = 18.dp)
                                                    .clip(RoundedCornerShape((customTheme.cornerRadius / 2).dp))
                                                    .background(Color(customTheme.keyColor)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("ক", color = Color(customTheme.textColor), fontSize = 7.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live test typing area playground
        item {
            Column {
                Text(
                    text = "নতুন থিম পরীক্ষা করুন (Test Typing Arena)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    placeholder = { Text("এখানে ক্লিক দিন কিবোর্ড লেআউট পরীক্ষা করার জন্য") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_typing_input"),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (testInputText.isNotEmpty()) {
                            IconButton(onClick = { testInputText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
        }

        // Clipboard Item Logs review
        item {
            Column {
                Text(
                    text = "ক্লিপবোর্ড রেকর্ডস (Clipboard Manager Database)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (customClips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো লেখা ক্লিপবোর্ডে কপি বা সংরক্ষিত নেই।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        customClips.take(10).forEach { item ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.text,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                database.dao().deleteClipboard(item)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete item",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // THEME BUILDER MODAL SHEET / DIALOG COVERAGE
    if (isThemeCreatorOpen) {
        AlertDialog(
            onDismissRequest = { isThemeCreatorOpen = false },
            title = { Text("নতুন কাস্টম থিম বিল্ডার (Custom Theme Creator)", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newThemeName,
                        onValueChange = { newThemeName = it },
                        label = { Text("থিমের নাম (Theme Name)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Mock background color selector (Presets list or HEX input style)
                    Text("ব্যাকগ্রাউন্ড কালার (Background Color)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF180A22), Color(0xFF011627)).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .clickable { newThemeBgColor = color }
                            )
                        }
                    }

                    Text("বাটন কি কালার (Key Color)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF2D124D), Color(0xFF012E4A)).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .clickable { newThemeKeyColor = color }
                            )
                        }
                    }

                    Text("টেক্সট কালার (Text Color)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Color(0xFFF8FAFC), Color(0xFFFFB7B2), Color(0xFFE2F1F8), Color(0xFF2EC4B6)).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .clickable { newThemeTextColor = color }
                            )
                        }
                    }

                    Text(
                        "বাটনের কোণার কার্ভ (Corner Radius: ${newThemeRadius.toInt()}dp)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = newThemeRadius,
                        onValueChange = { newThemeRadius = it },
                        valueRange = 0f..16f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val entity = CustomTheme(
                                name = newThemeName,
                                backgroundColor = newThemeBgColor.toArgb().toLong(),
                                keyColor = newThemeKeyColor.toArgb().toLong(),
                                textColor = newThemeTextColor.toArgb().toLong(),
                                cornerRadius = newThemeRadius.toInt()
                            )
                            database.dao().insertTheme(entity)
                            withContext(Dispatchers.Main) {
                                isThemeCreatorOpen = false
                                Toast.makeText(context, "${newThemeName} থিমটি সেভ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { isThemeCreatorOpen = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
