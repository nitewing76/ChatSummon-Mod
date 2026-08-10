package com.thiswuming.voicesummon.mob

import com.thiswuming.voicesummon.util.TextUtils
import net.minecraft.entity.EntityType


enum class MatchType {
    EXACT,       
    HOMOPHONE,   
    FUZZY,       
    ALIAS        
}


data class MobMatchResult(
    val entityType: EntityType<*>,
    val matchedName: String,
    val matchType: MatchType,
    val confidence: Float
)


class MobMatcher {

    
    
    private val enableHomophoneMatching: Boolean = false

    
    private val exactMap: Map<String, EntityType<*>> = MobMapping.DEFAULT_MOB_MAP

    
    @Suppress("unused")
    private val homophoneMap: Map<String, String> = HomophoneMapping.HOMOPHONE_MAP

    
    private val aliasMap: Map<String, String> = MobAlias.getAllAliases()

    
    private data class KeywordEntry(val cleaned: String, val target: String, val type: MatchType)

    private val keywords: List<KeywordEntry> by lazy {
        buildList {
            exactMap.keys.forEach { name ->
                add(KeywordEntry(TextUtils.cleanText(name), name, MatchType.EXACT))
            }
            aliasMap.forEach { (alias, target) ->
                add(KeywordEntry(TextUtils.cleanText(alias), target, MatchType.ALIAS))
            }
        }
    }

    
    val maxKeywordLength: Int by lazy {
        keywords.maxOfOrNull { it.cleaned.length } ?: 0
    }

    
    private val keywordSet: Set<String> by lazy {
        keywords.map { it.cleaned }.toSet()
    }

    
    fun isTrailingPrefixDeferral(text: String): Boolean {
        val cleanedText = TextUtils.cleanText(text)
        if (cleanedText.isEmpty()) return false
        for (entry in keywords) {
            if (entry.cleaned.isEmpty()) continue
            
            if (!cleanedText.endsWith(entry.cleaned)) continue
            
            if (isStrictPrefixOfLongerKeyword(entry.cleaned)) {
                return true
            }
        }
        return false
    }

    
    private fun isStrictPrefixOfLongerKeyword(word: String): Boolean {
        if (word.isEmpty()) return false
        for (kw in keywordSet) {
            if (kw.length > word.length && kw.startsWith(word)) {
                return true
            }
        }
        return false
    }

    
    @Suppress("unused")
    private val cleanedHomophones: Map<String, String> by lazy {
        homophoneMap.mapKeys { TextUtils.cleanText(it.key) }
    }

    
    fun match(text: String): MobMatchResult? {
        if (text.isBlank()) return null

        val cleanedText = TextUtils.cleanText(text)

        
        
        longestKeywordMatch(cleanedText)?.let { return it }

        
        if (enableHomophoneMatching) {
            homophoneMatch(cleanedText)?.let { return it }
        }

        
        return fuzzyMatch(cleanedText)
    }

    
    fun matchAll(text: String): List<MobMatchResult> {
        if (text.isBlank()) return emptyList()

        val cleanedText = TextUtils.cleanText(text)

        
        
        val keywordMatches = multiKeywordMatch(cleanedText, deferTrailingPrefix = true)
        if (keywordMatches.isNotEmpty()) return keywordMatches

        
        if (enableHomophoneMatching) {
            homophoneMatch(cleanedText)?.let { return listOf(it) }
        }
        return fuzzyMatch(cleanedText)?.let { listOf(it) } ?: emptyList()
    }

    
    fun matchAllWithContext(contextPrefix: String, newText: String): List<MobMatchResult> {
        if (newText.isBlank()) return emptyList()

        val cleanedPrefix = TextUtils.cleanText(contextPrefix)
        val cleanedNew = TextUtils.cleanText(newText)
        if (cleanedNew.isEmpty()) return emptyList()

        
        if (cleanedPrefix.isEmpty()) {
            return matchAll(newText)
        }

        val combined = cleanedPrefix + cleanedNew
        val prefixLen = cleanedPrefix.length

        
        
        val keywordMatches = multiKeywordMatch(combined, minEndExclusive = prefixLen, deferTrailingPrefix = true)
        if (keywordMatches.isNotEmpty()) return keywordMatches

        
        if (enableHomophoneMatching) {
            homophoneMatch(cleanedNew)?.let { return listOf(it) }
        }
        return fuzzyMatch(cleanedNew)?.let { listOf(it) } ?: emptyList()
    }

    
    private fun multiKeywordMatch(
        cleanedText: String,
        minEndExclusive: Int = 0,
        deferTrailingPrefix: Boolean = false
    ): List<MobMatchResult> {
        if (cleanedText.isEmpty()) return emptyList()

        
        data class Hit(val start: Int, val end: Int, val entry: KeywordEntry)
        val hits = mutableListOf<Hit>()
        for (entry in keywords) {
            if (entry.cleaned.isEmpty()) continue
            var fromIndex = 0
            while (true) {
                val idx = cleanedText.indexOf(entry.cleaned, fromIndex)
                if (idx < 0) break
                hits.add(Hit(idx, idx + entry.cleaned.length, entry))
                fromIndex = idx + entry.cleaned.length
            }
        }
        if (hits.isEmpty()) return emptyList()

        
        hits.sortWith(compareByDescending<Hit> { it.end - it.start }.thenBy { it.start })
        val occupied = BooleanArray(cleanedText.length)
        val selected = mutableListOf<Hit>()
        for (hit in hits) {
            var conflict = false
            for (i in hit.start until hit.end) {
                if (occupied[i]) { conflict = true; break }
            }
            if (conflict) continue
            for (i in hit.start until hit.end) occupied[i] = true
            selected.add(hit)
        }

        
        selected.sortBy { it.start }

        
        
        
        
        
        val seen = mutableSetOf<EntityType<*>>()
        val textLen = cleanedText.length
        return selected.mapNotNull { hit ->
            if (hit.end <= minEndExclusive) return@mapNotNull null
            if (deferTrailingPrefix && hit.end == textLen && isStrictPrefixOfLongerKeyword(hit.entry.cleaned)) {
                return@mapNotNull null
            }
            val entityType = exactMap[hit.entry.target]
            if (entityType != null && seen.add(entityType)) {
                MobMatchResult(
                    entityType = entityType,
                    matchedName = hit.entry.target,
                    matchType = hit.entry.type,
                    confidence = if (hit.entry.type == MatchType.EXACT) 0.95f else 0.85f
                )
            } else null
        }
    }

    
    private fun longestKeywordMatch(cleanedText: String): MobMatchResult? {
        var best: KeywordEntry? = null

        for (entry in keywords) {
            if (cleanedText.contains(entry.cleaned)) {
                if (best == null || entry.cleaned.length > best.cleaned.length) {
                    best = entry
                }
            }
        }

        if (best != null) {
            val entityType = exactMap[best.target]
            if (entityType != null) {
                return MobMatchResult(
                    entityType = entityType,
                    matchedName = best.target,
                    matchType = best.type,
                    confidence = if (best.type == MatchType.EXACT) 0.95f else 0.85f
                )
            }
        }

        return null
    }

    
    @Suppress("unused")
    private fun homophoneMatch(cleanedText: String): MobMatchResult? {
        
        val targetName = cleanedHomophones[cleanedText]
        if (targetName != null) {
            val entityType = exactMap[targetName]
            if (entityType != null) {
                return MobMatchResult(
                    entityType = entityType,
                    matchedName = targetName,
                    matchType = MatchType.HOMOPHONE,
                    confidence = 0.85f
                )
            }
        }

        
        for ((homophone, targetName) in cleanedHomophones) {
            if (cleanedText.contains(homophone)) {
                val entityType = exactMap[targetName]
                if (entityType != null) {
                    return MobMatchResult(
                        entityType = entityType,
                        matchedName = targetName,
                        matchType = MatchType.HOMOPHONE,
                        confidence = 0.8f
                    )
                }
            }
        }

        return null
    }

    
    private fun fuzzyMatch(cleanedText: String): MobMatchResult? {
        var bestMatch: Pair<String, Float>? = null
        var bestName: String? = null

        for (name in exactMap.keys) {
            val similarity = TextUtils.levenshteinSimilarity(cleanedText, name)

            if (similarity > 0.7f && (bestMatch == null || similarity > bestMatch.second)) {
                bestMatch = Pair(name, similarity)
                bestName = name
            }
        }

        if (bestMatch != null && bestName != null) {
            val entityType = exactMap[bestName]
            if (entityType != null) {
                return MobMatchResult(
                    entityType = entityType,
                    matchedName = bestName,
                    matchType = MatchType.FUZZY,
                    confidence = bestMatch.second
                )
            }
        }

        return null
    }
}
