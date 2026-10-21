package com.iron.breakfast.domain

import java.util.Locale

/**
 * One Latin spelling for Macedonian, so a Cyrillic name and a typed Latin one meet. Cyrillic letters map to their
 * Latin digraphs (ѓ to gj, ж to zh, ч to ch, џ to dz); the common Latin forms map the same way (dj and đ to gj, č to
 * ch, ć and ќ to kj, š to sh, ž to zh). Used only for the fallback match, never for storage or display.
 */
object Transliteration {
    private val cyrillic = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ѓ' to "gj", 'е' to "e", 'ж' to "zh", 'з' to "z",
        'ѕ' to "dz", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n", 'њ' to "nj",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ќ' to "kj", 'у' to "u", 'ф' to "f", 'х' to "h",
        'ц' to "c", 'ч' to "ch", 'џ' to "dz", 'ш' to "sh", 'ђ' to "gj", 'ћ' to "kj", 'ё' to "e",
    )
    private val latinForms = mapOf('č' to "ch", 'ć' to "kj", 'š' to "sh", 'ž' to "zh", 'đ' to "gj")

    /** The canonical Latin spelling of [text], lowercase. Cyrillic and Latin spellings of one word give the same string. */
    fun canonical(text: String): String {
        val mapped = buildString {
            for (ch in text.lowercase(Locale.ROOT)) append(cyrillic[ch] ?: latinForms[ch] ?: ch)
        }
        return mapped.replace("dj", "gj")
    }

    /**
     * The coarsest spelling: [canonical] with the digraphs folded to one letter (ch to c, sh to s, zh to z, gj to g,
     * kj to k). Used only as the lowest-ranked fallback, so ц and ч can collide here and nowhere else.
     */
    fun loose(text: String): String = canonical(text)
        .replace("ch", "c").replace("sh", "s").replace("zh", "z").replace("gj", "g").replace("kj", "k")
}
