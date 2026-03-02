package org.example.jinhhyu.fakeXray

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID

class FakeXray : JavaPlugin(), Listener {

    private val crouchStartTimes = mutableMapOf<UUID, Long>()
    private val triggeredThisCrouch = mutableSetOf<UUID>()
    private var monitorTask: BukkitTask? = null

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(this, this)
        monitorTask = server.scheduler.runTaskTimer(this, Runnable { monitorPlayers() }, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS)
    }

    override fun onDisable() {
        monitorTask?.cancel()
        crouchStartTimes.clear()
        triggeredThisCrouch.clear()
    }

    @EventHandler
    fun onSneakToggle(event: PlayerToggleSneakEvent) {
        val playerId = event.player.uniqueId
        if (event.isSneaking) {
            crouchStartTimes[playerId] = System.currentTimeMillis()
            triggeredThisCrouch.remove(playerId)
        } else {
            crouchStartTimes.remove(playerId)
            triggeredThisCrouch.remove(playerId)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        crouchStartTimes.remove(playerId)
        triggeredThisCrouch.remove(playerId)
    }

    private fun monitorPlayers() {
        val now = System.currentTimeMillis()
        for (player in server.onlinePlayers) {
            if (!player.isSneaking) {
                val playerId = player.uniqueId
                crouchStartTimes.remove(playerId)
                triggeredThisCrouch.remove(playerId)
                continue
            }

            val playerId = player.uniqueId
            val crouchStart = crouchStartTimes.getOrPut(playerId) { now }
            if (playerId in triggeredThisCrouch) {
                continue
            }

            if (now - crouchStart < CROUCH_DURATION_MS) {
                continue
            }

            val radius = configuredRadius()
            if (!hasDeepslateBehind(player, radius)) {
                continue
            }

            val placed = spawnHiddenDeepslateDiamondOre(player, radius, configuredSpawnCount())
            if (placed > 0) {
                triggeredThisCrouch.add(playerId)
            }
        }
    }

    private fun configuredSpawnCount(): Int = config.getInt("n", DEFAULT_N).coerceAtLeast(1)

    private fun configuredRadius(): Int = config.getInt("k", DEFAULT_K).coerceAtLeast(1)

    private fun hasDeepslateBehind(player: Player, radius: Int): Boolean {
        val location = player.location
        val world = location.world
        val behindDirection = location.direction.clone().setY(0.0)
        if (behindDirection.lengthSquared() <= 1.0E-6) {
            return false
        }
        behindDirection.normalize().multiply(-1)

        val centerX = location.blockX
        val centerY = location.blockY
        val centerZ = location.blockZ
        val radiusSquared = radius * radius

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val distanceSquared = x * x + y * y + z * z
                    if (distanceSquared == 0 || distanceSquared > radiusSquared) {
                        continue
                    }

                    val offset = Vector(x.toDouble(), y.toDouble(), z.toDouble())
                    if (offset.dot(behindDirection) <= 0.0) {
                        continue
                    }

                    val block = world.getBlockAt(centerX + x, centerY + y, centerZ + z)
                    if (block.type == Material.DEEPSLATE) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun spawnHiddenDeepslateDiamondOre(player: Player, radius: Int, amount: Int): Int {
        val location = player.location
        val world = location.world
        val centerX = location.blockX
        val centerY = location.blockY
        val centerZ = location.blockZ
        val radiusSquared = radius * radius
        val candidates = mutableListOf<Block>()

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val distanceSquared = x * x + y * y + z * z
                    if (distanceSquared == 0 || distanceSquared > radiusSquared) {
                        continue
                    }

                    val block = world.getBlockAt(centerX + x, centerY + y, centerZ + z)
                    if (!isValidReplacement(block)) {
                        continue
                    }
                    if (!isHiddenFromAir(block)) {
                        continue
                    }
                    candidates.add(block)
                }
            }
        }

        if (candidates.size < amount) {
            return 0
        }

        val toPlace = candidates.shuffled().take(amount)
        for (block in toPlace) {
            block.type = Material.DEEPSLATE_DIAMOND_ORE
        }
        return toPlace.size
    }

    private fun isValidReplacement(block: Block): Boolean {
        val type = block.type
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return false
        }
        if (!type.isSolid || block.isLiquid) {
            return false
        }
        if (type == Material.BEDROCK || type == Material.DEEPSLATE_DIAMOND_ORE) {
            return false
        }
        return true
    }

    private fun isHiddenFromAir(block: Block): Boolean {
        for (face in FACES) {
            val neighborType = block.getRelative(face).type
            if (neighborType == Material.AIR || neighborType == Material.CAVE_AIR || neighborType == Material.VOID_AIR) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val CROUCH_DURATION_MS = 3_000L
        private const val CHECK_INTERVAL_TICKS = 5L
        private const val DEFAULT_N = 3
        private const val DEFAULT_K = 8
        private val FACES = arrayOf(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
        )
    }
}
