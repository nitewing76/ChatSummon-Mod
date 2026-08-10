package com.thiswuming.voicesummon.mob

import net.minecraft.entity.EntityType


object MobMapping {

    val DEFAULT_MOB_MAP: Map<String, EntityType<*>> = mapOf(
        
        "僵尸" to EntityType.ZOMBIE,
        "骷髅" to EntityType.SKELETON,
        "苦力怕" to EntityType.CREEPER,
        "末影人" to EntityType.ENDERMAN,
        "蜘蛛" to EntityType.SPIDER,
        "洞穴蜘蛛" to EntityType.CAVE_SPIDER,
        "史莱姆" to EntityType.SLIME,
        "烈焰人" to EntityType.BLAZE,
        "烈焰怪" to EntityType.BLAZE,
        "凋零骷髅" to EntityType.WITHER_SKELETON,
        "凋零" to EntityType.WITHER,
        "女巫" to EntityType.WITCH,
        "守卫者" to EntityType.GUARDIAN,
        "远古守卫者" to EntityType.ELDER_GUARDIAN,
        "潜影贝" to EntityType.SHULKER,
        "蠹虫" to EntityType.SILVERFISH,
        "末影螨" to EntityType.ENDERMITE,
        "恶魂" to EntityType.GHAST,
        "岩浆怪" to EntityType.MAGMA_CUBE,
        "熔岩怪" to EntityType.MAGMA_CUBE,
        "岩浆史莱姆" to EntityType.MAGMA_CUBE,
        "僵尸猪灵" to EntityType.ZOMBIFIED_PIGLIN,
        "猪灵" to EntityType.PIGLIN,
        "猪灵蛮兵" to EntityType.PIGLIN_BRUTE,
        "疣猪兽" to EntityType.HOGLIN,
        "僵尸疣猪兽" to EntityType.ZOGLIN,
        "掠夺者" to EntityType.PILLAGER,
        "卫道士" to EntityType.VINDICATOR,
        "唤魔者" to EntityType.EVOKER,
        "恼鬼" to EntityType.VEX,
        "幻术师" to EntityType.ILLUSIONER,
        "劫掠兽" to EntityType.RAVAGER,
        "流浪者" to EntityType.STRAY,
        "尸壳" to EntityType.HUSK,
        "溺尸" to EntityType.DROWNED,
        "幻翼" to EntityType.PHANTOM,
        "循声守卫" to EntityType.WARDEN,
        "监守者" to EntityType.WARDEN,
        "坚守者" to EntityType.WARDEN,
        "悦灵" to EntityType.ALLAY,
        "沼骸" to EntityType.BOGGED,
        "旋风人" to EntityType.BREEZE,
        "僵尸村民" to EntityType.ZOMBIE_VILLAGER,
        "炽足兽" to EntityType.STRIDER,
        "快乐恶魂" to EntityType.HAPPY_GHAST,
        "金恶魂" to EntityType.HAPPY_GHAST,

        
        "僵尸猪人" to EntityType.ZOMBIFIED_PIGLIN,
        "猪人" to EntityType.ZOMBIFIED_PIGLIN,
        "僵尸疣猪" to EntityType.ZOGLIN,

        
        "铁傀儡" to EntityType.IRON_GOLEM,
        "雪傀儡" to EntityType.SNOW_GOLEM,
        "海龟" to EntityType.TURTLE,
        "北极熊" to EntityType.POLAR_BEAR,
        "羊驼" to EntityType.LLAMA,
        "行商羊驼" to EntityType.TRADER_LLAMA,
        "熊猫" to EntityType.PANDA,
        "狐狸" to EntityType.FOX,
        "蜜蜂" to EntityType.BEE,
        "狼" to EntityType.WOLF,
        "狗" to EntityType.WOLF,
        "猫" to EntityType.CAT,
        "豹猫" to EntityType.OCELOT,
        "鹦鹉" to EntityType.PARROT,
        "海豚" to EntityType.DOLPHIN,
        "山羊" to EntityType.GOAT,
        "青蛙" to EntityType.FROG,
        "蝌蚪" to EntityType.TADPOLE,
        "骆驼" to EntityType.CAMEL,
        "嗅探兽" to EntityType.SNIFFER,
        "犰狳" to EntityType.ARMADILLO,

        
        "马" to EntityType.HORSE,
        "驴" to EntityType.DONKEY,
        "骡" to EntityType.MULE,
        "骷髅马" to EntityType.SKELETON_HORSE,
        "僵尸马" to EntityType.ZOMBIE_HORSE,
        "牛" to EntityType.COW,
        "哞菇" to EntityType.MOOSHROOM,
        "蘑菇牛" to EntityType.MOOSHROOM,
        "羊" to EntityType.SHEEP,
        "猪" to EntityType.PIG,
        "鸡" to EntityType.CHICKEN,
        "兔子" to EntityType.RABBIT,
        "鱿鱼" to EntityType.SQUID,
        "萤火鱿" to EntityType.GLOW_SQUID,
        "发光鱿鱼" to EntityType.GLOW_SQUID,
        "蝙蝠" to EntityType.BAT,
        "鳕鱼" to EntityType.COD,
        "鲑鱼" to EntityType.SALMON,
        "河豚" to EntityType.PUFFERFISH,
        "热带鱼" to EntityType.TROPICAL_FISH,
        "美西螈" to EntityType.AXOLOTL,

        
        "村民" to EntityType.VILLAGER,
        "流浪商人" to EntityType.WANDERING_TRADER,

        
        "末影龙" to EntityType.ENDER_DRAGON,
        "凋灵" to EntityType.WITHER,
        "远古守卫" to EntityType.ELDER_GUARDIAN,

        
        "雪人" to EntityType.SNOW_GOLEM,
        "铁人" to EntityType.IRON_GOLEM,
        "烈焰" to EntityType.BLAZE,
        "小白" to EntityType.SKELETON,
        "僵尸猪" to EntityType.ZOMBIFIED_PIGLIN,
    )

    
    fun getAllNames(): Set<String> = DEFAULT_MOB_MAP.keys
}