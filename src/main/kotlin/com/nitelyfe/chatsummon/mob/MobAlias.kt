package com.nitelyfe.chatsummon.mob


object MobAlias {

    
    val DEFAULT_ALIAS_MAP: Map<String, String> = mapOf(
        
        "小白" to "骷髅",

        
        "小黑" to "末影人",
        "旱鸭子" to "末影人",
        "见水愁" to "末影人",

        
        "爬行者" to "苦力怕",
        "原谅怪" to "苦力怕",
        "爬虫怪" to "苦力怕",
        "丁丁怪" to "苦力怕",

        
        "咦呦咦" to "恶魂",
        "嘤嘤怪" to "恶魂",
        "EOE" to "恶魂",
        "地狱水母" to "恶魂",

        
        "蠢虫" to "蠹虫",
        "银鱼" to "蠹虫",
        "蛆" to "蠹虫",

        
        "铁巨人" to "铁傀儡",
        "白色僵尸" to "铁傀儡",
        "铁哥" to "铁傀儡",
        "铁人" to "铁傀儡",

        
        "雪人" to "雪傀儡",

        
        "猪人" to "僵尸猪灵",
        "僵尸猪" to "僵尸猪灵",

        
        "岩浆史莱姆" to "岩浆怪",

        
        "龙" to "末影龙",

        
        "凋灵" to "凋零",

        
        "草泥马" to "羊驼",

        
        "蝾螈" to "美西螈",

        
        "僵尸村民" to "僵尸村民",
    )

    
    private var customAliases: Map<String, String> = emptyMap()

    
    fun getAllAliases(): Map<String, String> {
        return DEFAULT_ALIAS_MAP + customAliases
    }

    
    fun setCustomAliases(aliases: Map<String, String>) {
        customAliases = aliases
    }
}