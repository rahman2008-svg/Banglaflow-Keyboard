package com.example.keyboard.engine

import java.util.Locale

object AvroEngine {
    // Dictionary of common Bangla word exceptions for perfect typing experience
    private val commonWords = mapOf(
        "ami" to "আমি",
        "tumi" to "তুমি",
        "amar" to "আমার",
        "tumar" to "তোমার",
        "kemon" to "কেমন",
        "acho" to "আছো",
        "bhalo" to "ভালো",
        "valobashi" to "ভালোবাসি",
        "valobasi" to "ভালোবাসি",
        "bangladesh" to "বাংলাদেশ",
        "bangla" to "বাংলা",
        "dhaka" to "ঢাকা",
        "bhasa" to "ভাষা",
        "shob" to "সব",
        "khub" to "খুব",
        "shundor" to "সুন্দর",
        "kothay" to "কোথায়",
        "ki" to "কি",
        "na" to "না",
        "ha" to "হ্যাঁ",
        "ranna" to "রান্না",
        "bhabo" to "ভাবো",
        "kotha" to "কথা",
        "ekhon" to "এখন",
        "hobe" to "হবে",
        "baje" to "বাজে",
        "bhai" to "ভাই",
        "bon" to "বোন",
        "ma" to "মা",
        "baba" to "বাবা",
        "aj" to "আজ",
        "kal" to "কাল",
        "din" to "দিন",
        "rath" to "রাত",
        "cha" to "চা",
        "pani" to "পানি",
        "khabar" to "খাবার",
        "bari" to "বাড়ি",
        "desh" to "দেশ",
        "manush" to "মানুষ",
        "bondhu" to "বন্ধু",
        "kaj" to "কাজ"
    )

    private val independentVowels = mapOf(
        "a" to "আ",
        "A" to "অ",
        "i" to "ই",
        "I" to "ঈ",
        "u" to "উ",
        "U" to "ঊ",
        "e" to "এ",
        "O" to "ও",
        "oi" to "ৈ", // independent oi would be ঐ, let's add support
        "ou" to "ৌ"  // independent ou would be ঔ, let's add support
    )

    private val vowelSigns = mapOf(
        "a" to "া",
        "i" to "ি",
        "I" to "ী",
        "u" to "ু",
        "U" to "ূ",
        "e" to "ে",
        "O" to "ো",
        "o" to "ো",
        "oi" to "ৈ",
        "ou" to "ৌ",
        "rri" to "ৃ"
    )

    private val consonants = mapOf(
        "kh" to "খ",
        "g" to "গ",
        "gh" to "ঘ",
        "ng" to "ঙ",
        "ch" to "ছ",
        "c" to "চ",
        "jh" to "ঝ",
        "j" to "জ",
        "Th" to "ঠ",
        "T" to "ট",
        "Dh" to "ঢ",
        "D" to "ড",
        "th" to "থ",
        "t" to "ত",
        "dh" to "ধ",
        "d" to "দ",
        "ph" to "ফ",
        "p" to "প",
        "bh" to "ভ",
        "b" to "ব",
        "m" to "ম",
        "z" to "য",
        "y" to "য়",
        "r" to "র",
        "l" to "ল",
        "sh" to "শ",
        "S" to "ষ",
        "s" to "স",
        "h" to "হ",
        "Rh" to "ঢ়",
        "R" to "ড়",
        "w" to "ও",
        "x" to "ক্স",
        "f" to "ফ",
        "v" to "ভ"
    )

    // Separate digraph consonant starts so we can parse them as unit
    private val digraphs = setOf("kh", "gh", "ch", "jh", "Th", "Dh", "th", "dh", "ph", "bh", "sh", "Rh", "ng")

    /**
     * Converts a phonetic english word using Avro parsing logic.
     */
    fun convert(input: String): String {
        if (input.isEmpty()) return ""
        val lowercaseInput = input.lowercase(Locale.ROOT)
        
        // Exact dictionary word first
        if (commonWords.containsKey(lowercaseInput)) {
            return commonWords[lowercaseInput]!!
        }

        val result = StringBuilder()
        var i = 0
        val len = input.length
        
        var lastWasConsonant = false
        
        while (i < len) {
            val remaining = len - i
            
            // Check custom suffix or triple character clusters
            if (remaining >= 3) {
                val tri = lowercaseInput.substring(i, i + 3)
                if (tri == "rri") {
                    if (lastWasConsonant) {
                        result.append("ৃ")
                    } else {
                        result.append("ঋ")
                    }
                    lastWasConsonant = false
                    i += 3
                    continue
                }
            }

            // Check double character digraphs
            if (remaining >= 2) {
                val digraph = lowercaseInput.substring(i, i + 2)
                
                // Vowels
                if (digraph == "oi") {
                    if (lastWasConsonant) {
                        result.append("ৈ")
                    } else {
                        result.append("ঐ")
                    }
                    lastWasConsonant = false
                    i += 2
                    continue
                }
                if (digraph == "ou") {
                    if (lastWasConsonant) {
                        result.append("ৌ")
                    } else {
                        result.append("ঔ")
                    }
                    lastWasConsonant = false
                    i += 2
                    continue
                }
                if (digraph == "ee") {
                    if (lastWasConsonant) {
                        result.append("ী")
                    } else {
                        result.append("ঈ")
                    }
                    lastWasConsonant = false
                    i += 2
                    continue
                }
                if (digraph == "oo") {
                    if (lastWasConsonant) {
                        result.append("ূ")
                    } else {
                        result.append("ঊ")
                    }
                    lastWasConsonant = false
                    i += 2
                    continue
                }

                // Consonants digraphs
                if (consonants.containsKey(digraph)) {
                    // Juktoborno: If last was consonant, and this is consonant, we insert a Hasanta `্` (u09CD)
                    if (lastWasConsonant) {
                        result.append("\u09CD")
                    }
                    result.append(consonants[digraph])
                    lastWasConsonant = true
                    i += 2
                    continue
                }
            }

            // Single letters
            val char = lowercaseInput[i].toString()
            
            // Vowels
            if (char == "a" || char == "e" || char == "i" || char == "o" || char == "u" || char == "o") {
                if (lastWasConsonant) {
                    val sign = vowelSigns[char] ?: ""
                    result.append(sign)
                } else {
                    val mappedIndependent = independentVowels[char] ?: char
                    result.append(mappedIndependent)
                }
                lastWasConsonant = false
            } 
            // Consonants
            else if (consonants.containsKey(char)) {
                if (lastWasConsonant) {
                    result.append("\u09CD") // Link consonants
                }
                result.append(consonants[char])
                lastWasConsonant = true
            } 
            // Spaces, numbers or punctuation
            else {
                result.append(input[i])
                lastWasConsonant = false
            }
            i++
        }
        
        return result.toString()
    }
}
