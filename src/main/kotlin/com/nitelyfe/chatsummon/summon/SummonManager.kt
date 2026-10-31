package com.nitelyfe.chatsummon.summon

import com.nitelyfe.chatsummon.config.ModConfig
import com.nitelyfe.chatsummon.mob.MobMapping
import com.nitelyfe.chatsummon.util.ModLogger
import net.minecraft.server.MinecraftServer
import net.minecraft.entity.SpawnReason
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import java.nio.file.Paths
import kotlin.math.sqrt


class SummonManager(
) {
    private val logger = ModLogger.getLogger("SummonManager")
    private val random = Random.create()

    
    private val rateLimitMs: Long = 1000L
    private val lastSummonTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    
    private var cachedConfig: ModConfig = ModConfig.load()
    private var cachedConfigMtime: Long = fileMtime()

    
    private fun getConfig(): ModConfig {
        val mtime = fileMtime()
        if (mtime != cachedConfigMtime) {
            cachedConfig = ModConfig.load()
            cachedConfigMtime = mtime
        }
        return cachedConfig
    }

    private fun fileMtime(): Long {
        return try {
            val f = Paths.get("config", "chatsummon.json").toFile()
            if (f.exists()) f.lastModified() else 0L
        } catch (_: Exception) {
            0L
        }
    }

    
    fun handleSummonRequest(server: MinecraftServer, speaker: ServerPlayerEntity, mobNames: List<String>) {
        if (mobNames.isEmpty()) return

        
        val now = System.currentTimeMillis()
        val uuid = speaker.uuidAsString
        val last = lastSummonTime.getOrDefault(uuid, 0L)
        if (now - last < rateLimitMs) {
            logger.warn("Summon request rate limited for player {}", speaker.name.string)
            return
        }
        lastSummonTime[uuid] = now

        
        val config = getConfig()

        val summonRange = config.summonRange
        val summonAtSpeaker = config.summonAtSpeakerIfNoTarget

        
        val maxMobs = 1 + random.nextInt(config.maxMobsPerSummon.coerceAtLeast(1))

        
        val validEntries = mobNames.mapNotNull { name ->
            val type = MobMapping.DEFAULT_MOB_MAP[name]
            if (type == null) {
                logger.warn("Unknown mob name: {}", name)
                speaker.sendMessage(Text.translatable("summon.unknown_mob", name), false)
                null
            } else {
                name to type
            }
        }
        if (validEntries.isEmpty()) return

        
        val targetPlayers = getTargetPlayers(server, speaker, summonRange)

        
        val spawnTargets = if (targetPlayers.isNotEmpty()) {
            targetPlayers.map { it.getEntityWorld() to it.getBlockPos() }
        } else if (summonAtSpeaker) {
            logger.info("No target player found, summoning at speaker's location")
            listOf(speaker.getEntityWorld() to speaker.getBlockPos())
        } else {
            speaker.sendMessage(Text.translatable("summon.no_target"), false)
            return
        }

        
        try {
            var totalSpawned = 0
            val spawnedPerMob = mutableMapOf<String, Int>()

            for ((world, summonPos) in spawnTargets) {
                for ((mobName, entityType) in validEntries) {
                    val safePos = findSafeSpawnPosition(world, summonPos)

                    var spawnedCount = 0
                    for (i in 0 until maxMobs) {
                        val spawnPos = if (i > 0) {
                            findSafeSpawnPosition(world, summonPos.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2))
                        } else {
                            safePos
                        }

                        val entity = entityType.create(world, null, spawnPos, SpawnReason.COMMAND, false, false)
                        if (entity != null) {
                            
                            (entity as? net.minecraft.entity.mob.MobEntity)?.setPersistent()
                            world.spawnEntity(entity)
                            spawnedCount++
                        }
                    }
                    totalSpawned += spawnedCount
                    spawnedPerMob.merge(mobName, spawnedCount, Int::plus)

                    if (spawnedCount > 0) {
                        logger.info("Summoned {} ({}) at {} (speaker: {})",
                            mobName, spawnedCount, safePos, speaker.name.string)
                    }
                }
            }

            if (totalSpawned > 0) {
                
                val targetNames = targetPlayers.joinToString("、") { it.name.string }
                val mobsDisplay = spawnedPerMob.keys.joinToString("、")
                
                ModLogger.writeSummonSuccess(
                    speaker.name.string,
                    mobsDisplay,
                    totalSpawned,
                    if (targetPlayers.isNotEmpty()) targetNames else "自己"
                )
                
                val actionBarMsg = if (targetPlayers.isNotEmpty()) {
                    Text.translatable("summon.actionbar_with_target", speaker.name.string, mobsDisplay, targetNames)
                } else {
                    Text.translatable("summon.actionbar_no_target", speaker.name.string, mobsDisplay)
                }
                server.playerManager.playerList.forEach { player ->
                    player.sendMessage(actionBarMsg, true)
                }
            } else {
                val mobsDisplay = validEntries.joinToString("、") { it.first }
                logger.error("Failed to create any entity: {}", mobsDisplay)
                speaker.sendMessage(Text.translatable("summon.create_failed", mobsDisplay), false)
            }
        } catch (e: Exception) {
            logger.error("Error summoning mob: {}", e.message, e)
            speaker.sendMessage(Text.translatable("summon.failed", e.message ?: ""), false)
        }
    }

    
    fun onPlayerDisconnect(player: ServerPlayerEntity) {
        lastSummonTime.remove(player.uuidAsString)
    }

    
    private fun getTargetPlayers(server: MinecraftServer, speaker: ServerPlayerEntity, range: Double): List<ServerPlayerEntity> {
        val speakerPos = speaker.getBlockPos()
        val candidates = mutableListOf<ServerPlayerEntity>()

        for (player in server.playerManager.playerList) {
            if (player == speaker) continue
            if (player.isSpectator) continue 

            if (!range.isInfinite()) {
                val dx = player.x - speakerPos.x
                val dy = player.y - speakerPos.y
                val dz = player.z - speakerPos.z
                val distance = sqrt(dx * dx + dy * dy + dz * dz)
                if (distance > range) continue
            }

            candidates.add(player)
        }

        return candidates
    }

    
    private fun findSafeSpawnPosition(world: ServerWorld, pos: BlockPos): BlockPos {
        
        for (attempt in 0..10) {
            val offsetX = random.nextInt(5) - 2
            val offsetZ = random.nextInt(5) - 2
            val candidatePos = pos.add(offsetX, 0, offsetZ)

            
            var y = candidatePos.y
            while (y > world.getBottomY() && world.getBlockState(BlockPos(candidatePos.x, y - 1, candidatePos.z)).isAir) {
                y--
            }
            
            if (y < world.getHeight() && world.getBlockState(BlockPos(candidatePos.x, y, candidatePos.z)).isAir) {
                return BlockPos(candidatePos.x, y, candidatePos.z)
            }
        }

        
        return pos
    }
}