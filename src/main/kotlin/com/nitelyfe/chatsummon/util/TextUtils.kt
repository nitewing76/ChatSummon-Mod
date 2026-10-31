package com.nitelyfe.chatsummon.util


object TextUtils {

    private val PUNCTUATION_REGEX = Regex("[，。！？、；：\"'\\[\\]【】（）()\\s]+")

    
    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val len1 = s1.length
        val len2 = s2.length

        
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            val ch1 = s1[i - 1]
            for (j in 1..len2) {
                val cost = if (ch1 == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,        
                    curr[j - 1] + 1,    
                    prev[j - 1] + cost  
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }

        return prev[len2]
    }

    
    fun levenshteinSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        val maxLen = maxOf(s1.length, s2.length)
        val distance = levenshteinDistance(s1, s2)
        return 1.0f - (distance.toFloat() / maxLen)
    }

    
    fun cleanText(text: String): String {
        return text
            .replace(PUNCTUATION_REGEX, "")
            .trim()
            .lowercase()
    }
}
