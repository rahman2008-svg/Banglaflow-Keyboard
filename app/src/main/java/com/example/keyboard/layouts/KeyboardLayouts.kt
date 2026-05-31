package com.example.keyboard.layouts

enum class KeyActionType {
    CHARACTER,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    MODE_CHANGE, // Switch between layouts (English/Avro/Probhat/National)
    EMOJI,
    CLIPBOARD,
    VOICE,
    THEME,
    SETTINGS,
    FLOAT_TOGGLE,
    HIDE_KEYBOARD,
    CLEAR_BUFFER
}

data class KeyDef(
    val mainText: String,
    val shiftText: String = mainText,
    val isAction: Boolean = false,
    val actionType: KeyActionType = KeyActionType.CHARACTER,
    val weight: Float = 1.0f
)

object KeyboardLayouts {

    // English QWERTY Layout rows
    val englishLayout = listOf(
        // Row 1
        listOf(
            KeyDef("q", "Q"), KeyDef("w", "W"), KeyDef("e", "E"), KeyDef("r", "R"),
            KeyDef("t", "T"), KeyDef("y", "Y"), KeyDef("u", "U"), KeyDef("i", "I"),
            KeyDef("o", "O"), KeyDef("p", "P")
        ),
        // Row 2
        listOf(
            KeyDef("a", "A"), KeyDef("s", "S"), KeyDef("d", "D"), KeyDef("f", "F"),
            KeyDef("g", "G"), KeyDef("h", "H"), KeyDef("j", "J"), KeyDef("k", "K"),
            KeyDef("l", "L")
        ),
        // Row 3
        listOf(
            KeyDef("Shift", isAction = true, actionType = KeyActionType.SHIFT, weight = 1.5f),
            KeyDef("z", "Z"), KeyDef("x", "X"), KeyDef("c", "C"), KeyDef("v", "V"),
            KeyDef("b", "B"), KeyDef("n", "N"), KeyDef("m", "M"),
            KeyDef("Del", isAction = true, actionType = KeyActionType.BACKSPACE, weight = 1.5f)
        )
    )

    // Bangla Probhat Layout
    // Organised logically for smooth typing of Bengali on mobile screens
    val probhatLayout = listOf(
        // Row 1: Vowels and vowel signs
        listOf(
            KeyDef("ক", "খ"), KeyDef("গ", "ঘ"), KeyDef("ঙ", "ং"), KeyDef("চ", "ছ"),
            KeyDef("জ", "ঝ"), KeyDef("ঞ", "ঁ"), KeyDef("ট", "ঠ"), KeyDef("ড", "ঢ")
        ),
        // Row 2: Consonants
        listOf(
            KeyDef("ত", "থ"), KeyDef("দ", "ধ"), KeyDef("ন", "ণ"), KeyDef("প", "ফ"),
            KeyDef("ব", "ভ"), KeyDef("ম", "ৎ"), KeyDef("য", "য়"), KeyDef("র", "ড়")
        ),
        // Row 3: Vowel-signs / Kar symbols + Shift/Del
        listOf(
            KeyDef("Shift", isAction = true, actionType = KeyActionType.SHIFT, weight = 1.2f),
            KeyDef("ল", "ঢ়"), KeyDef("শ", "ষ"), KeyDef("স", "হ"), 
            KeyDef("া", "ি"), KeyDef("ী", "ু"), KeyDef("ূ", "ে"), KeyDef("ৈ", "ো"), KeyDef("ৌ", "ৃ"),
            KeyDef("Del", isAction = true, actionType = KeyActionType.BACKSPACE, weight = 1.2f)
        )
    )

    // Bangla National / Jatiya Layout
    val nationalLayout = listOf(
        // Row 1
        listOf(
            KeyDef("া", "অ"), KeyDef("ি", "ই"), KeyDef("ী", "ঈ"), KeyDef("ু", "উ"),
            KeyDef("ূ", "ঊ"), KeyDef("ে", "এ"), KeyDef("ৈ", "ঐ"), KeyDef("ো", "ও"),
            KeyDef("ৌ", "ঔ"), KeyDef("ৃ", "ঋ")
        ),
        // Row 2
        listOf(
            KeyDef("ক", "খ"), KeyDef("গ", "ঘ"), KeyDef("ঙ", "ঁ"), KeyDef("চ", "ছ"),
            KeyDef("জ", "ঝ"), KeyDef("ঞ", "ৎ"), KeyDef("ট", "ঠ"), KeyDef("ড", "ঢ"),
            KeyDef("ণ", "ষ")
        ),
        // Row 3
        listOf(
            KeyDef("Shift", isAction = true, actionType = KeyActionType.SHIFT, weight = 1.3f),
            KeyDef("ত", "থ"), KeyDef("দ", "ধ"), KeyDef("ন", "শ"), KeyDef("স", "হ"),
            KeyDef("প", "ফ"), KeyDef("ব", "ভ"), KeyDef("ম", "য"), KeyDef("র", "ল"),
            KeyDef("Del", isAction = true, actionType = KeyActionType.BACKSPACE, weight = 1.3f)
        )
    )

    // Bangla Avro Keyboard Rows (Uses English QWERTY with upper suggestion preview)
    val avroLayout = englishLayout
}
